/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen

import kotlinx.rpc.codegen.checkers.FirRemoteConstructorCallChecker
import kotlinx.rpc.codegen.checkers.FirRemoteMethodCallChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar

class FirRemoteAdditionalCheckers(
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(FirRpcPredicates.remote)
    }

    override val expressionCheckers: ExpressionCheckers = FirRemoteExpressionCheckers()
}

class FirRemoteExpressionCheckers() : ExpressionCheckers() {
    override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(
        FirRemoteConstructorCallChecker,
        FirRemoteMethodCallChecker,
    )
}
