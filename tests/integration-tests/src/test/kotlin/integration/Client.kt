/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package integration

import io.netty.util.AsyncMapping
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.RemoteContext
import kotlinx.rpc.internal.utils.ExperimentalRpcApi

@OptIn(ExperimentalRpcApi::class)
fun main(): Unit = runBlocking {

    with(object : RemoteContext {}) {
        println("Hey")
//        val fabric = CalculatorFabric()
        val calculator = AsyncCalculator(0)
//        val descriptor = serviceDescriptorOf<AsyncCalculator>()
//        val appModule = SerializersModule {
//            contextual(descriptor.serializer!!)
//        }
//        val json = Json {
//            serializersModule = appModule
//            prettyPrint = true
//        }
//        val encoded = json.encodeToString(calculator)
//        println(encoded.toLong().toString(2))
        calculator.add(10)
        calculator.multiply(2)
        calculator.subtract(7)
        println("Accumulated value is ${calculator.value()}")
        calculator.factorial()
        println("Accumulated value is ${calculator.value()}")
        calculator.reset()
        println("Accumulated value is ${calculator.value()}")
        calculator.close()
//        fabric.close()
        Unit
    }
}
