/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen

import kotlinx.rpc.codegen.FirRpcPredicates.remote
import kotlinx.rpc.codegen.common.RpcClassId
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl.Modifier.SUSPEND
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

class FirRemoteStatusTransformer(session: FirSession) : FirStatusTransformerExtension(session) {

    override fun transformStatus(
        status: FirDeclarationStatus,
        function: FirSimpleFunction,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean
    ): FirDeclarationStatus {
        val returnType = vsApi { function.symbol.resolvedReturnTypeRef.coneTypeVS.toClassSymbolVS(session) }
            ?: return status
        if (returnType.classId == RpcClassId.flow) return status
        return status.transform { this[SUSPEND] = true }
    }

    override fun needTransformStatus(declaration: FirDeclaration): Boolean {
        if (declaration !is FirSimpleFunction) return false
        val containingClass = declaration.symbol.getContainingClassSymbol() ?: return false
        return session.predicateBasedProvider.matches(remote, containingClass)
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(remote)
    }
}
