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

import com.android.build.gradle.internal.cxx.codeText
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.INFO
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.LIFECYCLE
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.WARN
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.ERROR
import com.android.utils.cxx.CxxDiagnosticCode.RESERVED_FOR_TESTS
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.concurrent.thread

class LoggingEnvironmentTest {

    private class TestLoggingEnvironment(private val tag : String) : ThreadLoggingEnvironment() {
        val messages = mutableListOf<String>()
        override fun log(message: LoggingMessage) {
            messages += when(message.level) {
                INFO -> "info $tag: ${message.text()}"
                LIFECYCLE -> "lifecycle $tag: ${message.text()}"
                WARN -> "warn $tag: ${message.text()}"
                ERROR -> "error $tag: ${message.text()}"
                else -> error("unexpected")
            }
        }
    }

    @Test
    fun testEnvironmentIsPerThread() {
        lateinit var thread1Messages : List<String>
        lateinit var thread2Messages : List<String>
        val thread1 = thread {
            TestLoggingEnvironment("thread 1").use { logger ->
                errorln(RESERVED_FOR_TESTS, "error")
                warnln("warn")
                lifecycleln("lifecycle")
                infoln("info")
                thread1Messages = logger.messages
            }
        }
        val thread2 = thread {
            TestLoggingEnvironment("thread 2").use { logger ->
                errorln(RESERVED_FOR_TESTS, "error")
                warnln("warn")
                lifecycleln("lifecycle")
                infoln("info")
                thread2Messages = logger.messages
            }
        }
        thread1.join()
        thread2.join()
        assertThat(thread1Messages).containsExactly("error thread 1: ${RESERVED_FOR_TESTS.codeText} error",
            "warn thread 1: C/C++: warn", "lifecycle thread 1: C/C++: lifecycle", "info thread 1: C/C++: info")
        assertThat(thread2Messages).containsExactly("error thread 2: ${RESERVED_FOR_TESTS.codeText} error",
            "warn thread 2: C/C++: warn", "lifecycle thread 2: C/C++: lifecycle", "info thread 2: C/C++: info")
    }

    @Test
    fun testEnvironmentsNest() {
        TestLoggingEnvironment("nest 1").use { outer ->
            errorln(RESERVED_FOR_TESTS, "error")
            TestLoggingEnvironment("nest 2").use { inner ->
                errorln(RESERVED_FOR_TESTS, "error")
                assertThat(inner.messages).containsExactly("error nest 2: ${RESERVED_FOR_TESTS.codeText} error")
            }
            errorln(RESERVED_FOR_TESTS, "error")
            assertThat(outer.messages).containsExactly("error nest 1: ${RESERVED_FOR_TESTS.codeText} error",
                "error nest 1: ${RESERVED_FOR_TESTS.codeText} error")
        }
    }

    @Test
    fun `131271062 percent in format with no args`() {
        TestLoggingEnvironment("nest 1").use {
            errorln(RESERVED_FOR_TESTS, "error %F")
        }
    }

    @Test
    fun `131271062 percent in format with args`() {
        TestLoggingEnvironment("nest 1").use {
            errorln(RESERVED_FOR_TESTS, "error %F", "arg")
        }
    }
}
