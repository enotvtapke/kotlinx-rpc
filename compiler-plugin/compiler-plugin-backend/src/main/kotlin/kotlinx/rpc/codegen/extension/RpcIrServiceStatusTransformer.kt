/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.extension

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.IrTransformer

internal class RpcIrServiceStatusTransformer : IrTransformer<RpcIrContext>() {
    override fun visitClass(
        declaration: IrClass,
        data: RpcIrContext
    ): IrStatement {
        if (!declaration.rpc() || declaration.isInterface) return declaration
        declaration.modality = Modality.OPEN
        declaration.functions.forEach { function ->
            function.modality = Modality.OPEN
        }
        return declaration
    }
}