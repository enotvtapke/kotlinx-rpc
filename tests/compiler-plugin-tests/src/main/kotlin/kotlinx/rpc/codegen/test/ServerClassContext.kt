/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.test

import kotlinx.rpc.NetworkContext
import kotlinx.rpc.RemoteClassContext
import kotlinx.rpc.RpcClient

data object ServerClassContext: RemoteClassContext {
    override val context: NetworkContext = ServerNetworkContext
    override val rpcClient: RpcClient = TestRpcClient
}

data object ServerNetworkContext: NetworkContext
data object ClientNetworkContext: NetworkContext
