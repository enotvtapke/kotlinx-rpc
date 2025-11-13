/*
 * Copyright 2023-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package integration

import kotlinx.rpc.annotations.Remote

@Remote(ServerClassContext::class)
class CalculatorFabric {
    fun valueCalculator(value: Long): AsyncCalculator {
        with(ServerContext) {
            println("Creating calculator with value $value")
            return AsyncCalculator(value)
        }
    }
}

@Remote(ServerClassContext::class)
class AsyncCalculator(private var initialValue: Long) {
    fun factorial() {
        println("Calculating $initialValue!")
        initialValue = (1..initialValue).fold(1L) { acc, i -> acc * i }
    }

    fun divide(value: Long) {
        println("Calculating $initialValue / $value")
        initialValue /= value
    }

    fun add(value: Long) {
        println("Calculating $initialValue + $value")
        initialValue += value
    }

    fun subtract(value: Long) {
        println("Calculating $initialValue - $value")
        initialValue -= value
    }

    fun multiply(value: Long) {
        println("Calculating $initialValue * $value")
        initialValue *= value
    }

    fun value(): Long = initialValue

    fun reset() {
        println("Resetting calculator")
        initialValue = 0
    }
}
