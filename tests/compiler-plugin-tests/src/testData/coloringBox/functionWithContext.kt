/*
 * Copyright 2023-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// TARGET_BACKEND: JVM

import kotlinx.coroutines.runBlocking
import kotlinx.rpc.annotations.Remote
import kotlinx.rpc.codegen.test.ServerClassContext
import kotlinx.rpc.codegen.test.ClientNetworkContext

data class TestData(val value: String)

@Remote(ServerClassContext::class)
open class BoxService {
    open suspend fun test1(testData: TestData): String = ""

    open suspend fun test2(testData: TestData): String = ""
}

context(_: ClientNetworkContext)
suspend fun clientFunction(box: BoxService): String {
    val test1 = box.test1(TestData("value"))
    val test2 = box.test2(TestData("value"))
    return if (test1 == "call_42" && test2 == "call_42") "OK" else "Fail: test1=$test1, test2=$test2"
}

fun box(): String = runBlocking {
    val box = context(ClientNetworkContext) {
        BoxService()
    }
    with(ClientNetworkContext) {
        clientFunction(box)
    }
}
