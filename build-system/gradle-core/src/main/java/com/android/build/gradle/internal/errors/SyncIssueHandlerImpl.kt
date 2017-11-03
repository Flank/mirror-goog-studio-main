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

package com.android.build.gradle.internal.errors

import com.android.annotations.concurrency.Immutable
import com.android.build.gradle.internal.ide.SyncIssueImpl
import com.android.build.gradle.options.SyncOptions.EvaluationMode
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

class SyncIssueHandlerImpl(
        private val mode: EvaluationMode,
        private val logger: Logger)
    : SyncIssueHandler {

    private val _syncIssues = Maps.newHashMap<SyncIssueKey, SyncIssue>()

    override val syncIssues: ImmutableList<SyncIssue>
        get() = ImmutableList.copyOf(_syncIssues.values)

    override fun hasSyncIssue(type: EvalIssueReporter.Type): Boolean {
        return _syncIssues.values.stream().anyMatch { issue -> issue.type == type.type }
    }

    override fun reportIssue(
            type: EvalIssueReporter.Type,
            severity: EvalIssueReporter.Severity,
            msg: String,
            data: String?): SyncIssue {
        val issue: SyncIssue
        when (mode) {
            EvaluationMode.STANDARD -> {
                if (severity.severity != SyncIssue.SEVERITY_WARNING) {
                    throw GradleException(msg)
                }
                logger.warn("WARNING: " + msg)
                issue = SyncIssueImpl(type, severity, data, msg)
            }

            EvaluationMode.IDE -> {
                issue = SyncIssueImpl(type, severity, data, msg)
                _syncIssues.put(syncIssueKeyFrom(issue), issue)
            }
            else -> throw RuntimeException("Unknown SyncIssue type")
        }

        return issue
    }
}

/**
 * Creates a key from a SyncIssue to use in a map.
 */
private fun syncIssueKeyFrom(syncIssue: SyncIssue): SyncIssueKey {
    return SyncIssueKey(syncIssue.type, syncIssue.data)
}

@Immutable
internal data class SyncIssueKey constructor(
        private val type: Int,
        private val data: String?) {

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("data", data)
                .toString()
    }
}
