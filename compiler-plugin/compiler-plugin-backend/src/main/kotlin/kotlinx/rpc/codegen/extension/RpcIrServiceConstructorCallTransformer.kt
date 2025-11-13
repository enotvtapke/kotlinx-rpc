/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.extension

import kotlinx.rpc.codegen.common.RpcNames
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.Name

internal class RpcIrServiceConstructorCallTransformer : IrTransformer<RpcIrContext>() {
    private val containingDeclarations = mutableListOf<IrDeclaration>()

    override fun visitDeclaration(declaration: IrDeclarationBase, data: RpcIrContext): IrStatement {
        containingDeclarations.add(declaration)
        return super.visitDeclaration(declaration, data).also {
            containingDeclarations.removeLast()
        }
    }

    override fun visitClass(
        declaration: IrClass,
        data: RpcIrContext
    ): IrStatement {
        if (declaration.remote()) return declaration
        return super.visitClass(declaration, data)
    }

    override fun visitConstructorCall(
        expression: IrConstructorCall,
        data: RpcIrContext
    ): IrElement {
        val serviceClass = expression.type.classOrFail.owner
        if (!serviceClass.remote()) {
            return super.visitConstructorCall(expression, data)
        }

        val remoteConfigSymbol = serviceClass.remoteConfigObject()

        val remoteConfigContextSymbol =
            remoteConfigSymbol.owner.findDeclaration<IrProperty> { it.name == data.remoteConfigContext.owner.name }?.getter?.returnType?.classOrFail
                ?: error("Cannot find `context` property in remote configuration")
        val inLocalContext = containingDeclarations.filterIsInstance<IrFunction>().any {
            it.parameters.filter { parameter -> parameter.kind in listOf(DispatchReceiver, ExtensionReceiver, Context) }
                .any { parameter ->
                    parameter.type.isSubtypeOfClass(remoteConfigContextSymbol)
                }
        }
        if (inLocalContext) return super.visitConstructorCall(expression, data)

        val constructorName = Name.identifier(
            rpcConstructorName(
                serviceClass.constructors.indexOfFirst { it.symbol == expression.symbol }.takeIf { it != -1 }
                    ?: error("No constructor corresponding to constructor call is present in rpc service ${serviceClass.name.asString()}")
            )
        )

        val serviceStubClass = serviceClass.nestedClasses.singleOrNull { it.name == RpcNames.SERVICE_STUB_NAME } ?:
            error("No stub class is present in rpc service ${serviceClass.name.asString()}")

        return remoteConstructorCall(
            constructorName = constructorName,
            serviceStubClass = serviceStubClass,
            serviceStub = serverStubCall(data, serviceClass),
            ctx = data,
            constructorArguments = expression.arguments.toList()
        )
    }
}

fun serverStubCall(
    ctx: RpcIrContext,
    serviceClass: IrClass,
): IrCall {
    val remoteConfigSymbol = serviceClass.remoteConfigObject()
    return vsApi(ctx) {
        IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = serviceClass.defaultType,
            symbol = ctx.functions.rpcClientWithService,
            typeArgumentsCount = 1
        ).apply {
            typeArguments[0] = serviceClass.defaultType
            val defaultRpcClient = IrCallImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = ctx.remoteConfigRpcClient.owner.getter!!.returnType,
                symbol = ctx.remoteConfigRpcClient.owner.getter!!.symbol,
                typeArgumentsCount = 0
            ).apply {
                dispatchReceiver = IrGetObjectValueImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    remoteConfigSymbol.defaultType,
                    remoteConfigSymbol
                )
            }
            this@apply.arguments[0] = defaultRpcClient
        }
    }
}

fun remoteConstructorCall(
    ctx: RpcIrContext,
    serviceStubClass: IrClass,
    serviceStub: IrExpression,
    constructorName: Name,
    constructorArguments: List<IrExpression?>
): IrCall {

    val rpcConstructorFunction = serviceStubClass.functions.singleOrNull { function ->
        function.name == constructorName
    } ?: error(
        "No constructor with name ${constructorName.asString()} is present in stub ${serviceStubClass.name.asString()}. " +
                "Available stub functions: ${serviceStubClass.functions.joinToString { it.name.asString() }}"
    )

    return vsApi(ctx) {
        IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = rpcConstructorFunction.returnType,
            symbol = rpcConstructorFunction.symbol,
            typeArgumentsCount = 0
        ).apply {
            dispatchReceiver = IrTypeOperatorCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                type,
                IrTypeOperator.CAST,
                type,
                serviceStub
            )
            constructorArguments.forEachIndexed { index, irExpression ->
                this@apply.arguments[index + 1] = irExpression
            }
        }
    }
}