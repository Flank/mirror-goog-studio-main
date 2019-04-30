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

package com.android.build.gradle.internal.cxx.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.concurrent.thread

class LoggingEnvironmentTest {

    private class TestLoggingEnvironment(private val tag : String) : ThreadLoggingEnvironment() {
        val messages = mutableListOf<String>()
        override fun error(message: String) {
            messages += "error $tag: $message"
        }

        override fun warn(message: String) {
            messages += "warn $tag: $message"
        }

        override fun info(message: String) {
            messages += "info $tag: $message"
        }
    }

    @Test
    fun testEnvironmentIsPerThread() {
        val thread1 = thread {
            TestLoggingEnvironment("thread 1").use { logger ->
                errorln("error")
                warnln("warn")
                infoln("info")
                assertThat(logger.messages).containsExactly("error thread 1: error",
                    "warn thread 1: warn", "info thread 1: info")
            }
        }
        val thread2 = thread {
            TestLoggingEnvironment("thread 2").use { logger ->
                errorln("error")
                warnln("warn")
                infoln("info")
                assertThat(logger.messages).containsExactly("error thread 2: error",
                    "warn thread 2: warn", "info thread 2: info")
            }
        }
        thread1.join()
        thread2.join()
    }

    @Test
    fun testEnvironmentsNest() {
        TestLoggingEnvironment("nest 1").use { outer ->
            errorln("error")
            TestLoggingEnvironment("nest 2").use { inner ->
                errorln("error")
                assertThat(inner.messages).containsExactly("error nest 2: error")
            }
            errorln("error")
            assertThat(outer.messages).containsExactly("error nest 1: error",
                "error nest 1: error")
        }
    }

    @Test
    fun `131271062 percent in format with no args`() {
        TestLoggingEnvironment("nest 1").use {
            errorln("error %F")
        }
    }

    @Test
    fun `131271062 percent in format with args`() {
        TestLoggingEnvironment("nest 1").use {
            errorln("error %F", "arg")
        }
    }
}