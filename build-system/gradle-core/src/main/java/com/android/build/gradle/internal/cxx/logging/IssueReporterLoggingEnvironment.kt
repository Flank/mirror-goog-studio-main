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

import com.android.builder.errors.EvalIssueReporter
import com.android.builder.errors.EvalIssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION
import com.android.builder.errors.EvalIssueReporter.Severity.ERROR
import com.android.builder.errors.EvalIssueReporter.Severity.WARNING
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging

// Change to false when b/130363042 is fixed
private const val FORCE_EXCEPTION_ON_ERROR = true

/**
 * A logging environment that will report errors and warnings to an [EvalIssueReporter].
 * Messages are also logger to a standard [org.gradle.api.logging.Logger].
 */
class IssueReporterLoggingEnvironment(
    private val issueReporter : EvalIssueReporter) : ThreadLoggingEnvironment() {
    private val logger = Logging.getLogger(IssueReporterLoggingEnvironment::class.java)
    private val errors = mutableListOf<LoggingRecord>()

    override fun error(message: String) {
        issueReporter.reportIssue(EXTERNAL_NATIVE_BUILD_CONFIGURATION, ERROR, message)
        logger.error(message)
    }

    override fun warn(message: String) {
        issueReporter.reportIssue(EXTERNAL_NATIVE_BUILD_CONFIGURATION, WARNING, message)
        logger.warn(message)
    }

    override fun info(message: String) {
        logger.info(message)
    }

    override fun close() {
        // Close will cause the next logger to popped to the top of the stack.
        super.close()

        if (FORCE_EXCEPTION_ON_ERROR) {
            errors.onEach { (level, message) ->
                if (level == LoggingLevel.ERROR) {
                    throw GradleException(message)
                }
            }
        }
    }
}