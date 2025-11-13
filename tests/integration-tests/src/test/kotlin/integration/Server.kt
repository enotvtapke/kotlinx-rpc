/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package integration

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.rpc.descriptor.serviceDescriptorOf
import kotlinx.rpc.internal.utils.ExperimentalRpcApi
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

@OptIn(ExperimentalRpcApi::class)
fun Application.module() {
    install(Krpc)

    routing {
        rpc("/calculator") {
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

            registerServiceForCreation(AsyncCalculator::class)
            registerServiceForCreation(CalculatorFabric::class)
        }
    }
}
