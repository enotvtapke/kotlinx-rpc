/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.extension

import kotlinx.rpc.codegen.common.RpcClassId
import kotlinx.rpc.codegen.common.RpcNames
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.nestedClasses
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.Name

internal class RpcIrServiceConstructorCallTransformer : IrTransformer<RpcIrContext>() {
    override fun visitClass(
        declaration: IrClass,
        data: RpcIrContext
    ): IrStatement {
        if (declaration.hasAnnotation(RpcClassId.rpcAnnotation)) return declaration
        return super.visitClass(declaration, data)
    }

    override fun visitConstructorCall(
        expression: IrConstructorCall,
        data: RpcIrContext
    ): IrElement {
        val serviceClass = expression.type.classOrFail.owner
        if (!serviceClass.hasAnnotation(RpcClassId.rpcAnnotation)) {
            return super.visitConstructorCall(expression, data)
        }
        val serviceStubClass = serviceClass.nestedClasses.single { it.name == RpcNames.SERVICE_STUB_NAME }
        val rpcConstructorFunction = serviceStubClass.functions.single { function ->
            function.name == Name.identifier(
                rpcConstructorName(
                    serviceClass.constructors.indexOfFirst { it.symbol == expression.symbol }
                )
            )
        }

        return vsApi(data) {
            val serviceStub = IrCallImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = expression.type,
                symbol = data.functions.rpcClientWithService,
                typeArgumentsCount = 1
            ).apply {
                typeArguments[0] = expression.type
                val defaultRpcClient = IrCallImpl(
                    startOffset = expression.startOffset,
                    endOffset = expression.endOffset,
                    type = data.globalRpcClient.owner.getter!!.returnType,
                    symbol = data.globalRpcClient.owner.getter!!.symbol,
                    typeArgumentsCount = 0
                ).apply {
                    dispatchReceiver = IrGetObjectValueImpl(
                        expression.startOffset,
                        expression.endOffset,
                        data.globalRpcClientConfig.defaultType,
                        data.globalRpcClientConfig
                    )
                }
                arguments[0] = defaultRpcClient
            }

            IrCallImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = expression.type,
                symbol = rpcConstructorFunction.symbol,
                typeArgumentsCount = 0
            ).apply {
                dispatchReceiver = IrTypeOperatorCallImpl(
                    expression.startOffset,
                    expression.endOffset,
                    type,
                    IrTypeOperator.CAST,
                    type,
                    serviceStub
                )
                expression.arguments.forEachIndexed { index, irExpression ->
                    arguments[index + 1] = irExpression
                }
            }
        }
    }
}