/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.extension

import kotlinx.rpc.codegen.common.RpcNames
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.hasDefaultValue

/**
 * This class scans user declared RPC service
 * and returns all necessary information for code generation by [RpcStubGenerator].
 *
 * Some checks are preformed during scanning,
 * but all user-friendly errors are expected to be thrown by frontend plugins
 */
internal object RpcDeclarationScanner {
    fun scanServiceDeclaration(service: IrClass, ctx: RpcIrContext, logger: MessageCollector): ServiceDeclaration {
        var stubClass: IrClass? = null

        var constructorIndex = 0
        val declarations = service.declarations.memoryOptimizedMap { declaration ->
            when (declaration) {
                is IrSimpleFunction -> {
                    if (declaration.isFakeOverride) {
                        return@memoryOptimizedMap null
                    }

                    ServiceDeclaration.Method(
                        function = declaration,
                        arguments = ctx.versionSpecificApi.run {
                            declaration.valueParametersVS().memoryOptimizedMap { param ->
                                ServiceDeclaration.Argument(param, param.type, param.hasDefaultValue())
                            }
                        },
                    )
                }

                is IrConstructor -> {
                    ServiceDeclaration.Constructor(
                        name = rpcConstructorName(constructorIndex),
                        function = declaration,
                        arguments = ctx.versionSpecificApi.run {
                            declaration.valueParametersVS().memoryOptimizedMap { param ->
                                ServiceDeclaration.Argument(param, param.type, param.hasDefaultValue())
                            }
                        },
                    ).also {
                        constructorIndex += 1
                    }
                }

                is IrProperty -> {
                    if (!declaration.isFakeOverride &&
                        declaration.visibility.delegate in listOf(Visibilities.Public, Visibilities.Internal)
                    ) {
                        error(
                            "Public or internal fields are not supported in @Rpc services, this error should be caught by frontend."
                        )
                    }
                    return@memoryOptimizedMap null
                }

                is IrClass -> {
                    if (declaration.name == RpcNames.SERVICE_STUB_NAME) {
                        stubClass = declaration
                        return@memoryOptimizedMap null
                    }

                    unsupportedDeclaration(service, declaration, logger)
                }

                else -> {
                    unsupportedDeclaration(service, declaration, logger)
                }
            }
        }

        val stubClassNotNull = stubClass
            ?: error("Expected generated stub class to be present in ${service.name.asString()}. FIR failed to do so.")

        return ServiceDeclaration(
            service = service,
            stubClass = stubClassNotNull,
            methods = declarations.filterIsInstance<ServiceDeclaration.Method>(),
            constructors = declarations.filterIsInstance<ServiceDeclaration.Constructor>(),
        )
    }
}

fun rpcConstructorName(constructorIndex: Int): String = "__rpcConstructor_$constructorIndex"

private fun unsupportedDeclaration(service: IrClass, declaration: IrDeclaration, logger: MessageCollector): Nothing? {
    logger.report(
        severity = CompilerMessageSeverity.LOGGING,
        message = "Unsupported declaration in @Rpc interface ${service.name}: ${declaration.dumpKotlinLike()}",
    )

    return null
}
