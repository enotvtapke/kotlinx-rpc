/*
 * Copyright 2023-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// RUN_PIPELINE_TILL: BACKEND

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.withService
import kotlinx.rpc.GlobalRpcClientConfig
import kotlinx.rpc.annotations.Rpc
import kotlinx.rpc.codegen.test.TestRpcClient

data class TestData(val value: String)

@Rpc
class BoxService(counter: Int) {
    constructor() : this(0)

    constructor(p: String): this(0)

    suspend fun test1(testData: TestData): String = ""

    suspend fun test2(testData: TestData): String = ""
}

fun box(): String = runBlocking {
    val box = BoxService("s")
    val test1 = box.test1(TestData("value"))
    val test2 = box.test2(TestData("value"))

    if (test1 == "call_42" && test2 == "call_42") "OK" else "Fail: test1=$test1, test2=$test2"
}
