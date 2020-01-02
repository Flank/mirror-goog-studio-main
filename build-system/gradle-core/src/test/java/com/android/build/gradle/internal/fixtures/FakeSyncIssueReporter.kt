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

package com.android.build.gradle.internal.fixtures

import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.ide.SyncIssueImpl
import com.android.builder.errors.EvalIssueException
import com.android.builder.model.SyncIssue
import com.google.common.collect.ImmutableList

/**
 * Implementation of [SyncIssueReporter].
 *
 * This records errors and warnings both as [SyncIssue] objects, but also records
 * the raw messages, allowing different ways to test the reported issues.
 *
 * This is similar to [FakeIssueReporter] from builder but also implements [SyncIssueReporter]
 */
class FakeSyncIssueReporter(
    private val throwOnError : Boolean = false
) : SyncIssueReporter() {
    override val syncIssues: ImmutableList<SyncIssue>
        get() = ImmutableList.copyOf(_syncIssues)

    private val _syncIssues = mutableListOf<SyncIssue>()

    val messages = mutableListOf<String>()
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    override fun reportIssue(type: Type,
        severity: Severity,
        exception: EvalIssueException
    ) {
        val issue = SyncIssueImpl(type, severity, exception)
        _syncIssues.add(issue)

        messages.add(exception.message)
        when(severity) {
            Severity.ERROR -> errors.add(exception.message)
            Severity.WARNING -> warnings.add(exception.message)
        }

        if (severity == Severity.ERROR && throwOnError) {
            throw exception
        }
    }

    override fun hasIssue(type: Type): Boolean {
        return _syncIssues.any { issue -> issue.type == type.type }
    }

    override fun lockHandler() {
        throw UnsupportedOperationException("lockHandler not implemented.")
    }
}