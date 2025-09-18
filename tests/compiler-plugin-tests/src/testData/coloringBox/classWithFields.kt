/*
 * Copyright 2023-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// TARGET_BACKEND: JVM

import kotlinx.coroutines.runBlocking
import kotlinx.rpc.withService
import kotlinx.rpc.annotations.Rpc
import kotlinx.rpc.codegen.test.TestRpcClient
import kotlinx.rpc.GlobalRpcClientConfig

@Rpc
open class BoxService {
    private val field: String = "field"
    open suspend fun simple(): String = "str"
}

fun box(): String = runBlocking {
    GlobalRpcClientConfig.rpcClient = TestRpcClient
    val service = BoxService()
    val result = service.simple()

    if (result == "call_42") "OK" else "Fail: $result"
}
