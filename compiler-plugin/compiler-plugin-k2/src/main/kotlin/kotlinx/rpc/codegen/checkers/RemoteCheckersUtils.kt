/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.checkers

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isSubtypeOf

context(context: CheckerContext)
fun ConeKotlinType.inContext(): Boolean =
    context.containingDeclarations.filterIsInstance<FirCallableDeclaration>().any { declaration ->
        declaration.contextParameters.map { it.returnTypeRef.coneType }
            .plusElement(declaration.receiverParameter?.typeRef?.coneType).any {
                it?.isSubtypeOf(this, context.session) ?: false
            }
    }
