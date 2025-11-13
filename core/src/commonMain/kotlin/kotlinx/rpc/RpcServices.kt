/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc

import kotlinx.atomicfu.AtomicLong
import kotlinx.atomicfu.atomic
import kotlinx.rpc.annotations.Remote
import kotlinx.rpc.internal.utils.CompilerPluginUtil

/**
 * This variable exposes KrpcServer that is used by Remote classes serializers to register new remote services when they should be sent to another node.
 */
public var rpcServer: RpcServer? = null

/**
 * Gives ids to the incoming connections in sequential order. Ids are sent to peers during the handshake process.
 */
private val SERVER_ATOMIC_CONNECTION_COUNTER: AtomicLong = atomic(initial = 0L)

public fun nextConnectionId(): Long = SERVER_ATOMIC_CONNECTION_COUNTER.incrementAndGet()

@CompilerPluginUtil
public inline fun <@Remote(RemoteConfig::class) reified Service : Any> registerRemoteService(service: Service): Long {
    if (rpcServer == null) {
        error("KrpcServer is not initialized. Please make sure that ktor server is up and running.")
    }
    val id = serviceId(nextConnectionId())
    rpcServer!!.registerService(Service::class, { service }, id.toString())
    return id
}
