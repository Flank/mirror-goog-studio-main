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

package com.android.build.gradle.integration.common.truth

import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth

fun Collection<SyncIssue>.checkSingleIssue(
    type: Int? = null,
    severity: Int? = null,
    message: String? = null
) {
    val issue = checkSingleIssue(type, severity)

    message?.let {
        Truth.assertWithMessage("Issue message").that(issue.message).isEqualTo(it)
    }
}

fun Collection<SyncIssue>.checkSingleIssue(
    type: Int? = null,
    severity: Int? = null,
    messageCheck: ((String) -> Unit)? = null
) {
    val issue = checkSingleIssue(type, severity)

    messageCheck?.let {
        messageCheck(issue.message)
    }
}

private fun Collection<SyncIssue>.checkSingleIssue(
    type: Int?,
    severity: Int?
): SyncIssue {
    when (size) {
        0 -> {
            throw RuntimeException("Issue list is empty, expected 1")
        }
        1 -> {
            // success, do nothing
        }
        else -> {
            throw RuntimeException("Issue list contains $size entries, expected 1\n${this}")
        }
    }

    val issue = this.single()

    type?.let {
        Truth.assertWithMessage("Issue type").that(issue.type).isEqualTo(it)
    }

    severity?.let {
        if (issue.severity != it) {
            throw RuntimeException("Incorrect Issue Severity. Expected '${it.toSeverity()}' but was '${issue.severity.toSeverity()}'")
        }
    }

    return issue
}

private fun Int.toSeverity(): String = when(this) {
    SyncIssue.SEVERITY_WARNING -> "WARNING"
    SyncIssue.SEVERITY_ERROR -> "ERROR"
    else -> throw RuntimeException("Unexpected severity value: $this")
}
