/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.process

import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.text
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class DefaultProcessOutputHandlerTest {

    @get:Rule
    var temporaryFolderRule = TemporaryFolder()
    private val stdoutFile: File by lazy {
        temporaryFolderRule.root.resolve("stdout")
    }
    private val stderrFile: File by lazy {
        temporaryFolderRule.root.resolve("stderr")
    }

    @Test
    fun `do not log anything`() = check(
        logStdout = false,
        logStderr = false,
        logFullStdout = false,
        stdout = "stdout",
        stderr = "stderr",
        lifecycle = "",
    )

    @Test
    fun `only log stderr`() = check(
        logStdout = false,
        logStderr = true,
        logFullStdout = false,
        stdout = "stdout",
        stderr = "stderr",
        lifecycle = "C/C++: stderr",
    )

    @Test
    fun `only log compiler output from stdout`() = check(
        logStdout = true,
        logStderr = false,
        logFullStdout = false,
        stdout = """
            this line is not a compiler output
            /path/to/abc.c(1,1): note: unused variable
            /path/to/abc.c(2,1): error: something is wrong
        """.trimIndent(),
        stderr = "stderr",
        lifecycle = """
            C/C++: /path/to/abc.c(1,1): note: unused variable
            C/C++: /path/to/abc.c(2,1): error: something is wrong
            """.trimIndent(),
    )

    @Test
    fun `log all stdout if asked`() = check(
        logStdout = true,
        logStderr = false,
        logFullStdout = true,
        stdout = """
            this line is not a compiler output
            /path/to/abc.c(1,1): note: unused variable
            /path/to/abc.c(2,1): error: something is wrong
        """.trimIndent(),
        stderr = "stderr",
        lifecycle = """
            C/C++: this line is not a compiler output
            C/C++: /path/to/abc.c(1,1): note: unused variable
            C/C++: /path/to/abc.c(2,1): error: something is wrong
            """.trimIndent(),
    )


    @Test
    fun `skip logging ninja directory line if no compiler output`() = check(
        logStdout = true,
        logStderr = false,
        logFullStdout = false,
        stdout = """
            this line is not a compiler output
            ninja: Entering directory '/path/to/dir'
            nothing to be done
        """.trimIndent(),
        stderr = "stderr",
        lifecycle = "",
    )

    @Test
    fun `log ninja directory line if there are compiler outputs`() = check(
        logStdout = true,
        logStderr = false,
        logFullStdout = false,
        stdout = """
            this line is not a compiler output
            ninja: Entering directory '/path/to/dir'
            /path/to/abc.c(1,1): note: unused variable
            /path/to/abc.c(2,1): error: something is wrong
        """.trimIndent(),
        stderr = "stderr",
        lifecycle = """
            C/C++: ninja: Entering directory '/path/to/dir'
            C/C++: /path/to/abc.c(1,1): note: unused variable
            C/C++: /path/to/abc.c(2,1): error: something is wrong
            """.trimIndent(),
    )

    private fun check(
        logStdout: Boolean,
        logStderr: Boolean,
        logFullStdout: Boolean,
        stdout: String,
        stderr: String,
        lifecycle: String
    ) {
        val messages = mutableListOf<String>()
        FakeThreadLoggingEnvironment(messages).use {
            DefaultProcessOutputHandler(
                stderrFile = stderrFile,
                stdoutFile = stdoutFile,
                logPrefix = "",
                logStderr = logStderr,
                logStdout = logStdout,
                logFullStdout = logFullStdout,
            ).createOutput().use {
                it.standardOutput.write(stdout.toByteArray(StandardCharsets.UTF_8))
                it.errorOutput.write(stderr.toByteArray(StandardCharsets.UTF_8))
            }
        }
        Truth.assertThat(messages.joinToString("\n")).isEqualTo(lifecycle)
    }

    class FakeThreadLoggingEnvironment(val messages: MutableList<String>) : ThreadLoggingEnvironment() {

        override fun log(message: LoggingMessage) {
            messages += message.text()
        }

    }
}
