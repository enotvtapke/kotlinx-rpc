/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package integration

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.rpc.RemoteConfig
import kotlinx.rpc.RemoteContext
import kotlinx.rpc.RpcClient
import kotlinx.rpc.descriptor.serviceDescriptorOf
import kotlinx.rpc.internal.utils.ExperimentalRpcApi
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

data object ServerClassContext: RemoteConfig { // TODO RemoteClassConfiguration
    override val context: ServerContext = ServerContext // TODO It is important to do not specify type here
    @OptIn(ExperimentalRpcApi::class)
    override val rpcClient: RpcClient = HttpClient {
        installKrpc()
    }.rpc {
        url {
            host = "localhost"
            port = 8080
            encodedPath = "calculator"
        }

        rpcConfig {
            serialization {
                json {
                    val descriptor = serviceDescriptorOf<AsyncCalculator>()
                    val appModule = SerializersModule {
                        contextual(descriptor.serializer!!)
                    }
                    serializersModule = appModule
                }
            }
        }
    }

}

fun a() {
//    ServerClassContext.rpcClient.withService<>()
}

data object ServerContext : RemoteContext

// При обрыве соединения уменьшать счётчики ссылок ресурсов соединения

