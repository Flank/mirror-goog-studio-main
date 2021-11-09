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

package com.android.build.gradle.internal.cxx

import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.LoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.ERROR
import com.android.build.gradle.internal.cxx.logging.decodeLoggingMessage
import com.android.build.gradle.internal.cxx.logging.getCxxStructuredLogFolder
import com.android.build.gradle.internal.cxx.logging.readStructuredLogs
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.google.common.truth.Truth.assertThat
import org.junit.rules.TemporaryFolder
import com.android.utils.cxx.CxxDiagnosticCode
import java.io.File

class StructuredLog(private val temp : TemporaryFolder) {

    private var rootBuildGradleFolder : File? = null
    private var logFolder : File? = null
    private var loggingEnvironment : LoggingEnvironment? = null

    init {
        createNewLoggingFolders()
    }

    private fun createNewLoggingFolders() {
        loggingEnvironment?.close()
        rootBuildGradleFolder = temp.newFolder()
        logFolder = getCxxStructuredLogFolder(rootBuildGradleFolder!!)
        logFolder!!.mkdirs()
        createFreshIssueReporter()
    }

    private fun createFreshIssueReporter() {
        loggingEnvironment?.close()
        val nopIssueReporter = object : IssueReporter() {
            override fun reportIssue(type: Type, severity: Severity, exception: EvalIssueException) { }
            override fun hasIssue(type: Type) = false
        }
        loggingEnvironment = IssueReporterLoggingEnvironment(
            issueReporter = nopIssueReporter,
            rootBuildGradleFolder = rootBuildGradleFolder!!,
            cxxFolder = null
        )
    }

    private fun flushLogs() {
        createFreshIssueReporter()
    }

    fun clear() {
        createNewLoggingFolders()
    }

    private fun getLoggingMessages() : Pair<File, List<LoggingMessage>> {
        val oldRoot = rootBuildGradleFolder!!
        flushLogs()
        return oldRoot to readStructuredLogs(
            logFolder = logFolder!!,
            ::decodeLoggingMessage)
    }

    private fun assertLoggingMessage(code: Int, text : String?) {
        val message = getLoggingMessages().second.filter { it.diagnosticCode == code }
        assertThat(message).hasSize(1)
        if (text != null) {
            assertThat(message.single().message).isEqualTo(text)
        }
    }

    fun assertWarning(code: CxxDiagnosticCode, text : String? = null) {
        assertLoggingMessage(code.warningCode, text)
    }

    fun assertError(code: CxxDiagnosticCode, text : String? = null) {
        assertLoggingMessage(code.errorCode, text)
    }

    fun assertNoErrors() {
        val errors = getLoggingMessages().second
            .filter { it.level == ERROR }
        assertThat(errors).hasSize(0)
    }

    fun loggingMessages(): List<LoggingMessage> {
        val (oldRoot, messages) = getLoggingMessages()
        return messages
                .map {
                    it.toBuilder()
                        .setMessage(it.message.replace(oldRoot.path, "{ROOT}"))
                        .build()
                }
    }
}
