/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.extension

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.kotlinFqName

class ServiceDeclaration(
    val service: IrClass,
    val stubClass: IrClass,
    val methods: List<Method>,
    val constructors: List<Constructor>,
) {
    val fqName = service.kotlinFqName.asString()

    val serviceType = service.defaultType

    sealed interface Callable {
        val name: String
        val function: IrFunction
        val arguments: List<Argument>
    }

    class Method(
        override val function: IrSimpleFunction,
        override val arguments: List<Argument>,
    ) : Callable {
        override val name: String = function.name.asString()
    }

    class Constructor(
        override val name: String,
        override val function: IrConstructor,
        override val arguments: List<Argument>,
    ) : Callable

    class Argument(
        val value: IrValueParameter,
        val type: IrType,
        val defaultValue: IrExpressionBody?,
    )
}
