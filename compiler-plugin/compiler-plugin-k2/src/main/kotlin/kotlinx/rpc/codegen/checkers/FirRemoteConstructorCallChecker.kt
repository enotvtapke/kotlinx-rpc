/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen.checkers

import kotlinx.rpc.codegen.FirRpcPredicates
import kotlinx.rpc.codegen.FirVersionSpecificApiImpl.toClassSymbolVS
import kotlinx.rpc.codegen.checkers.diagnostics.FirRpcDiagnostics.INVALID_REMOTE_CALL_CONTEXT
import kotlinx.rpc.codegen.common.RpcClassId.remoteContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.resolvedType

object FirRemoteConstructorCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        if (expression.toResolvedCallableSymbol() !is FirConstructorSymbol) return
        val classSymbol = expression.resolvedType.toClassSymbolVS(context.session)
            ?: error("${expression.resolvedType} is not a class")
        if (!context.session.predicateBasedProvider.matches(FirRpcPredicates.remote, classSymbol)) return

        val requiredContextType = remoteContext.constructClassLikeType()
        if (!requiredContextType.inContext())
            reporter.reportOn(
                source = expression.source,
                factory = INVALID_REMOTE_CALL_CONTEXT,
                a = requiredContextType,
            )
    }
}
