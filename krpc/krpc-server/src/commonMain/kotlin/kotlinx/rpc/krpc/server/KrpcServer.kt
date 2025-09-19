/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.krpc.server

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.rpc.RpcServer
import kotlinx.rpc.annotations.Rpc
import kotlinx.rpc.descriptor.RpcServiceDescriptor
import kotlinx.rpc.descriptor.serviceDescriptorOf
import kotlinx.rpc.internal.utils.InternalRpcApi
import kotlinx.rpc.internal.utils.map.RpcInternalConcurrentHashMap
import kotlinx.rpc.krpc.KrpcConfig
import kotlinx.rpc.krpc.KrpcTransport
import kotlinx.rpc.krpc.internal.*
import kotlinx.rpc.krpc.internal.logging.RpcInternalCommonLogger
import kotlinx.rpc.krpc.server.internal.KrpcServerConnector
import kotlinx.rpc.krpc.server.internal.KrpcServerService
import kotlin.collections.set
import kotlin.concurrent.Volatile
import kotlin.error
import kotlin.reflect.KClass

/**
 * Gives ids to the incoming connections in sequential order. Ids are sent to peers during the handshake process.
 */
private val SERVER_ATOMIC_CONNECTION_COUNTER = atomic(initial = 0L)

/**
 * kRPC implementation of the [RpcServer].
 * Takes care of tracking requests and responses,
 * serializing data, tracking streams, processing exceptions, and other protocol responsibilities.
 * Routes resulting messages to the proper registered services.
 * Leaves out the delivery of encoded messages to the specific implementations with [KrpcTransport].
 *
 * @param config configuration provided for that specific server. Applied to all services that use this server.
 * @param transport [KrpcTransport] instance that will be used to send and receive RPC messages.
 * IMPORTANT: Must be exclusive to this server, otherwise unexpected behavior may occur.
 */
@OptIn(InternalCoroutinesApi::class)
public abstract class KrpcServer(
    private val config: KrpcConfig.Server,
    transport: KrpcTransport,
) : RpcServer, KrpcEndpoint {

    /**
     * Close this server, removing all the services and stopping accepting messages.
     */
    public fun close(message: String? = null) {
        internalScope.cancel(message ?: "Server closed")
    }

    /**
     * Waits until the server is closed.
     */
    public suspend fun awaitCompletion() {
        internalScope.coroutineContext.job.join()
    }

    /*
     * #####################################################################
     * #                                                                   #
     * #                         INTERNALS AHEAD                           #
     * #                                                                   #
     * #####################################################################
     */

    @InternalRpcApi
    public val internalScope: CoroutineScope = CoroutineScope(
        transport.coroutineContext + SupervisorJob(transport.coroutineContext.job)
    )

    private val logger = RpcInternalCommonLogger.logger(rpcInternalObjectId())

    private val connector by lazy {
        KrpcServerConnector(
            serialFormat = config.serialFormatInitializer.build(),
            transport = transport,
            waitForServices = config.waitForServices,
        )
    }

    @InternalRpcApi
    final override val sender: KrpcMessageSender get() = connector

    @InternalRpcApi
    final override var supportedPlugins: Set<KrpcPlugin> = emptySet()
        private set

    private val clientSupportedPlugins: MutableMap<Long, Set<KrpcPlugin>> = mutableMapOf()

    private val rpcServices = RpcInternalConcurrentHashMap<String, KrpcServerService<*>>()

    @Volatile
    private var cancelledByClient = false

    init {
        internalScope.coroutineContext.job.invokeOnCompletion(onCancelling = true) {
            if (!cancelledByClient) {
                sendCancellation(CancellationType.ENDPOINT, null, null, closeTransportAfterSending = true)
            }
        }

        internalScope.launch(CoroutineName("krpc-server-generic-protocol-messages")) {
            connector.subscribeToProtocolMessages(::handleProtocolMessage)

            connector.subscribeToGenericMessages(::handleGenericMessage)
        }
    }

    private suspend fun handleProtocolMessage(message: KrpcProtocolMessage) {
        when (message) {
            is KrpcProtocolMessage.Handshake -> {
                val connectionId = SERVER_ATOMIC_CONNECTION_COUNTER.incrementAndGet()
                clientSupportedPlugins[connectionId] = message.supportedPlugins
                supportedPlugins = message.supportedPlugins // TODO supported plugins are not needed when I have clientSupportedPlugins but I preserved it to not fixing tests
                connector.sendMessage(KrpcProtocolMessage.Handshake(KrpcPlugin.ALL, connectionId = connectionId))
            }

            is KrpcProtocolMessage.Failure -> {
                logger.error {
                    "Client [${message.connectionId}] failed to handle protocol message ${message.failedMessage}: " +
                            message.errorMessage
                }
            }
        }
    }

    final override fun <@Rpc Service : Any> registerService(
        serviceKClass: KClass<Service>,
        serviceFactory: () -> Service,
    ) {
        val descriptor = serviceDescriptorOf(serviceKClass)

        internalScope.launch(CoroutineName("krpc-server-service-$descriptor")) {
            connector.subscribeToServiceMessages(descriptor.fqName) { message ->
                val rpcServerService = rpcServices.computeIfAbsent(descriptor.fqName) {
                    createNewServiceInstance(
                        descriptor,
                        plugins(message.connectionId!!),
                        serviceFactory,
                    )
                }

                rpcServerService.accept(message)
            }
        }
    }

    final override fun <@Rpc Service : Any> registerServiceForCreation(
        serviceKClass: KClass<Service>,
    ) {
        val descriptor = serviceDescriptorOf(serviceKClass)

        internalScope.launch(CoroutineName("krpc-server-service-$descriptor")) {
            connector.subscribeToServiceMessages(descriptor.fqName) { message ->
                val rpcServerService = rpcServices.computeIfAbsent(
                    "${message.connectionId}$${descriptor.fqName}$${message.serviceId}"
                ) {
                    createNewUninitializedServiceInstance(descriptor, plugins(message.connectionId!!))
                }

                rpcServerService.accept(message)
            }
        }
    }

    override fun <@Rpc Service : Any> deregisterService(serviceKClass: KClass<Service>) {
        connector.unsubscribeFromServiceMessages(serviceDescriptorOf(serviceKClass).fqName)
        rpcServices.remove(serviceDescriptorOf(serviceKClass).fqName)
    }

    private fun <@Rpc Service : Any> createNewServiceInstance(
        descriptor: RpcServiceDescriptor<Service>,
        plugins: Set<KrpcPlugin>,
        serviceFactory: () -> Service,
    ): KrpcServerService<Service> {
        return KrpcServerService(
            descriptor = descriptor,
            config = config,
            connector = connector,
            supportedPlugins = plugins,
            serverScope = internalScope,
        ).apply { initService(serviceFactory()) }
    }

    private fun <@Rpc Service : Any> createNewUninitializedServiceInstance(
        descriptor: RpcServiceDescriptor<Service>,
        plugins: Set<KrpcPlugin>
    ): KrpcServerService<Service> {
        return KrpcServerService(
            descriptor = descriptor,
            config = config,
            connector = connector,
            supportedPlugins = plugins,
            serverScope = internalScope,
        )
    }

    @InternalRpcApi
    final override suspend fun handleCancellation(message: KrpcGenericMessage) {
        when (val type = message.cancellationType()) {
            CancellationType.ENDPOINT -> {
                cancelledByClient = true

                internalScope.cancel("Server cancelled by client")
                rpcServices.clear()
            }

            CancellationType.REQUEST -> {
                val serviceType = message[KrpcPluginKey.CLIENT_SERVICE_ID]
                    ?: error("Expected CLIENT_SERVICE_ID for cancellation of type 'request'")

                val callId = message[KrpcPluginKey.CANCELLATION_ID]
                    ?: error("Expected CANCELLATION_ID for cancellation of type 'request'")

                rpcServices[serviceType]?.cancelRequestWithOptionalAck(callId, "Request cancelled by client")
            }

            else -> {
                logger.warn {
                    "Unsupported ${KrpcPluginKey.CANCELLATION_TYPE} $type for server, " +
                            "only 'endpoint' type may be sent by a server"
                }
            }
        }
    }

    private fun plugins(connectionId: Long): Set<KrpcPlugin> = clientSupportedPlugins[connectionId]
        ?: error("No supported plugins are found for client withe connection id `${connectionId}`")
}
