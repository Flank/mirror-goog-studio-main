/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.transport

import java.util.function.BooleanSupplier
import java.util.function.Predicate
import java.util.function.Supplier

/** Contains helper methods for transport tests.  */
object TestUtils {
    private const val RETRY_INTERVAL_MILLIS = 200
    private const val NO_LIMIT = Int.MAX_VALUE

    /**
     * Some Kotlin-specific utilities that would otherwise clash with the legacy Java APIs of the
     * same name.
     */
    object Kt {
        /**
         * Run the loop and wait until condition is true or the retry limit is reached. Returns the
         * result afterwards.
         *
         * @param supplier a function that returns the desired result.
         * @param condition tests whether the result is desired.
         * @param retryLimit Limit to retry before return the result. If not specified, try forever.
         * @param <T> type of the desired result.
         * @return the result from the last run (condition met or timeout).
         */
        fun <T> waitForAndReturn(supplier: () -> T, condition: (T) -> Boolean, retryLimit: Int = NO_LIMIT): T {
            var result = supplier()
            var count = 0
            while (!condition(result) && count < retryLimit) {
                println("Retrying condition")
                ++count
                try {
                    Thread.sleep(RETRY_INTERVAL_MILLIS.toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace(System.out)
                    break
                }
                result = supplier()
            }
            return result
        }

        /**
         * Like [waitForAndReturn] but when you don't care about the return value.
         */
        fun waitFor(condition: () -> Boolean, retryLimit: Int = NO_LIMIT) {
            waitForAndReturn({ Unit }, { condition() }, retryLimit)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun <T> waitForAndReturn(supplier: Supplier<T>, condition: Predicate<T>, retryLimit: Int = NO_LIMIT): T {
        return Kt.waitForAndReturn(
                { supplier.get() },
                { value: T -> condition.test(value) },
                retryLimit)
    }

    @JvmOverloads
    @JvmStatic
    fun waitFor(condition: BooleanSupplier, retryLimit: Int = NO_LIMIT) {
        Kt.waitFor({ condition.asBoolean }, retryLimit)
    }
}
