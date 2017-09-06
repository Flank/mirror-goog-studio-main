/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.errors

import com.android.builder.model.SyncIssue

/**
 * Reporter for issues during evaluation.
 *
 *
 * This handles dealing with errors differently if the project is being run from the command line
 * or from the IDE, in particular during Sync when we don't want to throw any exception
 */
interface EvalIssueReporter {

    /**
     * Reports an issue.
     *
     * The behavior of this method depends on whether the project is being evaluated by an IDE
     * (during sync) or from the command line. If it's the former, the issue will simply be recorded
     * and displayed after the sync properly finishes. If it's the latter, then the evaluation might
     * abort depending on the severity.
     *
     * @param type the type of the issue.
     * @param severity the severity of the issue
     * @param msg a human readable issue (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a SyncIssue if the issue.
     * @param data a data representing the source of the issue. This goes hand in hand with the
     * <var>type</var>, and is not meant to be readable. Instead a (possibly translated) message
     * is created from this data and type. Default value is null
     * @see SyncIssue
     */
    fun reportIssue(type: Int, severity: Int, msg: String, data: String?): SyncIssue

    /**
     * Reports an issue.
     *
     * The behavior of this method depends on whether the project is being evaluated by an IDE
     * (during sync) or from the command line. If it's the former, the issue will simply be recorded
     * and displayed after the sync properly finishes. If it's the latter, then the evaluation might
     * abort depending on the severity.
     *
     * @param type the type of the issue.
     * @param severity the severity of the issue
     * @param msg a human readable issue (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a SyncIssue if the issue.
     * @see SyncIssue
     */
    fun reportIssue(type: Int, severity: Int, msg: String): SyncIssue {
      return reportIssue(type, severity, msg, null)
    }

    /**
     * Reports an error.
     *
     * When running outside of IDE sync, this will throw and exception and abort execution.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @param data a data representing the source of the error. This goes hand in hand with the
     * <var>type</var>, and is not meant to be readable. Instead a (possibly translated) message
     * is created from this data and type.
     * @return a [SyncIssue] if the error is only recorded.
     */
    fun reportError(type: Int, msg: String, data: String?) = reportIssue(type,
            SyncIssue.SEVERITY_ERROR,
            msg,
            data)

    /**
     * Reports an error.
     *
     * When running outside of IDE sync, this will throw and exception and abort execution.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a [SyncIssue] if the error is only recorded.
     */
    fun reportError(type: Int, msg: String) = reportIssue(type, SyncIssue.SEVERITY_ERROR, msg, null)

    /**
     * Reports a warning.
     *
     * Behaves similar to [reportError] but does not abort the build.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @param data a data representing the source of the error. This goes hand in hand with the
     * <var>type</var>, and is not meant to be readable. Instead a (possibly translated) message
     * is created from this data and type.
     * @return a [SyncIssue] if the warning is only recorded.
     */
    fun reportWarning(type: Int, msg: String, data: String?) = reportIssue(type,
            SyncIssue.SEVERITY_WARNING,
            msg,
            data)

    /**
     * Reports a warning.
     *
     * Behaves similar to [reportError] but does not abort the build.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a [SyncIssue] if the warning is only recorded.
     */
    fun reportWarning(type: Int, msg: String) = reportIssue(type,
            SyncIssue.SEVERITY_WARNING,
            msg,
            null)

}
