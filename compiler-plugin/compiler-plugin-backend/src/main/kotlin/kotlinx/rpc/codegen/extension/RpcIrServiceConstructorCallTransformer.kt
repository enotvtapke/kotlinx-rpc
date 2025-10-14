/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.extension

import kotlinx.rpc.codegen.common.RpcClassId.remoteAnnotation
import kotlinx.rpc.codegen.common.RpcNames
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
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
        val serviceStubClass = serviceClass.nestedClasses.singleOrNull { it.name == RpcNames.SERVICE_STUB_NAME } ?:
            error("No stub class is present in rpc service ${serviceClass.name.asString()}")
        val constructorName = Name.identifier(
            rpcConstructorName(
                serviceClass.constructors.indexOfFirst { it.symbol == expression.symbol }.takeIf { it != -1 }
                    ?: error("No constructor corresponding to constructor call is present in rpc service ${serviceClass.name.asString()}")
            )
        )
        val rpcConstructorFunction = serviceStubClass.functions.singleOrNull { function ->
            function.name == constructorName
        } ?: error("No constructor with name ${constructorName.asString()} is present in stub for rpc service ${serviceClass.name.asString()}. " +
                "Available stub functions: ${serviceStubClass.functions.joinToString { it.name.asString() }}")

        val remoteAnnotationCall = serviceClass.getAnnotation(remoteAnnotation.asSingleFqName())!!
        val contextObjectClassExpression = remoteAnnotationCall.getValueArgument(Name.identifier("context"))
            ?: error("Annotation '${remoteAnnotation.asSingleFqName().asString()}' should have an argument named `context`")
        val contextObjectSymbol = ((contextObjectClassExpression.type as? IrSimpleType)?.arguments[0] as? IrTypeProjection)?.type?.classOrFail
            ?: error("Cannot get NetworkContext from type ${contextObjectClassExpression.type}")

        val contextObjectContextSymbol =
            contextObjectSymbol.owner.findDeclaration<IrProperty> { it.name == data.remoteClassContextContext.owner.name }?.getter?.returnType?.classOrFail
                ?: error("Cannot find `context` property in remote class configuration")
        val inLocalContext = containingDeclarations.filterIsInstance<IrFunction>().any {
            it.parameters.filter { parameter -> parameter.kind in listOf(DispatchReceiver, ExtensionReceiver, Context) }.any {
                parameter -> parameter.type.isSubtypeOfClass(contextObjectContextSymbol)
            }
        }
        if (inLocalContext) return super.visitConstructorCall(expression, data)

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
                    type = data.remoteClassContextRpcClient.owner.getter!!.returnType,
                    symbol = data.remoteClassContextRpcClient.owner.getter!!.symbol,
                    typeArgumentsCount = 0
                ).apply {
                    dispatchReceiver = IrGetObjectValueImpl(
                        expression.startOffset,
                        expression.endOffset,
                        contextObjectSymbol.defaultType,
                        contextObjectSymbol
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