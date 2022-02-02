/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.detector.api.Severity
import java.io.PrintWriter
import java.io.StringWriter

/** Test client which provides access to any logged errors */
open class LoggingTestLintClient : TestLintClient() {
    /** Returns the warnings logged so far */
    open fun getLoggedOutput(): String = log.toString().trim()

    override fun log(severity: Severity, exception: Throwable?, format: String?, vararg args: Any) {
        log.append(severity.description).append(": ")
        format?.let { log.append(String.format(it, *args)) }
        exception?.let {
            if (format != null) {
                log.append('\n')
            }
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            log.append(sw.toString())
        }
        log.append('\n')
    }

    override fun log(exception: Throwable?, format: String?, vararg args: Any) {
        log(Severity.ERROR, exception, format, *args)
    }

    private val log = StringBuilder()
}
