import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    alias(libs.plugins.conventions.jvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlinx.rpc)
}

dependencies {
    implementation(projects.krpc.krpcClient)
    implementation(projects.krpc.krpcServer)
    implementation(projects.krpc.krpcSerialization.krpcSerializationJson)

    implementation(libs.serialization.json)

    implementation(projects.krpc.krpcKtor.krpcKtorClient)
    implementation(projects.krpc.krpcKtor.krpcKtorServer)

    implementation(libs.atomicfu)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.netty)
//    implementation(libs.ktor.server.call.logging)

    implementation(libs.logback.classic)
}

kotlin {
    explicitApi = ExplicitApiMode.Disabled
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

//repositories {
//    mavenCentral()
//}
