/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.rpc.internal.utils

/**
 * This annotation is used to mark functions that are in compiler plugins to generate less code
 */
@InternalRpcApi
@Target(AnnotationTarget.FUNCTION)
public annotation class CompilerPluginUtil