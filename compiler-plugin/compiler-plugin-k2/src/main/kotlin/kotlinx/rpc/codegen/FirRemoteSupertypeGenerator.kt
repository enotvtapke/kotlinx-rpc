/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.codegen

import kotlinx.rpc.codegen.FirRpcPredicates.remote
import kotlinx.rpc.codegen.common.RpcClassId.remoteInterface
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef

class FirRemoteSupertypeGenerator(session: FirSession): FirSupertypeGenerationExtension(session) {
    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean =
        session.predicateBasedProvider.matches(remote, declaration)

    override fun computeAdditionalSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>,
        typeResolver: TypeResolveService
    ): List<ConeKotlinType> = listOf(remoteInterface.defaultType(listOf()))

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(remote)
    }
}