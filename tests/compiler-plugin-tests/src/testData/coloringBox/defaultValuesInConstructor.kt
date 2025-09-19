/*
 * Copyright 2023-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// TARGET_BACKEND: JVM

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.withService
import kotlinx.rpc.GlobalRpcClientConfig
import kotlinx.rpc.annotations.Rpc
import kotlinx.rpc.codegen.test.TestRpcClient

data class TestData(val value: String)

@Rpc
open class BoxService(counter: Int = 0) {

    open suspend fun test1(testData: TestData): String = ""

    open suspend fun test2(testData: TestData): String = ""
}

fun box(): String = runBlocking {
    GlobalRpcClientConfig.rpcClient = TestRpcClient

    val box = BoxService(1) // TODO BoxService() does not work because generated stub constructor __rpc_constructor_0 does not copy default parameters from original constructor
    val test1 = box.test1(TestData("value"))
    val test2 = box.test2(TestData("value"))

    if (test1 == "call_42" && test2 == "call_42") "OK" else "Fail: test1=$test1, test2=$test2"
}
