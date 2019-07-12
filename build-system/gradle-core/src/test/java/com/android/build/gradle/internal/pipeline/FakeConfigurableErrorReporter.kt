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

package com.android.build.gradle.internal.pipeline

import com.android.build.gradle.internal.errors.SyncIssueHandler
import com.android.build.gradle.internal.ide.SyncIssueImpl
import com.android.builder.errors.EvalIssueException
import com.android.builder.model.SyncIssue
import com.google.common.collect.ImmutableList

class FakeConfigurableErrorReporter : SyncIssueHandler() {

    override val syncIssues: ImmutableList<SyncIssue>
        get() = if (syncIssue != null) ImmutableList.of(syncIssue) else ImmutableList.of()
    var syncIssue: SyncIssue? = null
        private set

    @Synchronized
    override fun lockHandler() {
        throw UnsupportedOperationException(
            "lockHandler() shouldn't be called from inside tasks."
        )
    }

    @Synchronized
    override fun reportIssue(
        type: Type,
        severity: Severity,
        exception: EvalIssueException
    ) {
        // always create a sync issue, no matter what the mode is. This can be used to validate
        // what error is thrown anyway.
        syncIssue = SyncIssueImpl(type, severity, exception)
    }

    @Synchronized
    override fun hasSyncIssue(type: Type): Boolean {
        return syncIssue != null && syncIssue!!.type == type.type
    }
}