/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.options.SyncOptions
import com.android.builder.errors.IssueReporter
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Test

class SyncIssueReporterImplTest {

    val issueReporter = object : SyncIssueReporterImpl.GlobalSyncIssueService() {
        override fun getParameters(): Parameters {
            return object : Parameters {
                override val mode: Property<SyncOptions.EvaluationMode>
                    get() = FakeGradleProperty(SyncOptions.EvaluationMode.IDE)
            }
        }
    }

    @Test
    fun testReportingIssueTwice() {
        issueReporter.reportError(IssueReporter.Type.BUILD_TOOLS_TOO_LOW, RuntimeException(""))
        assertThat(issueReporter.getAllIssuesAndClear().map { it.type }).containsExactly(
            IssueReporter.Type.BUILD_TOOLS_TOO_LOW.type
        )
        assertThat(issueReporter.getAllIssuesAndClear().map { it.type }).isEmpty()
    }

    @Test
    fun testReportingAfterClear() {
        issueReporter.reportError(IssueReporter.Type.BUILD_TOOLS_TOO_LOW, RuntimeException(""))
        assertThat(issueReporter.getAllIssuesAndClear().map { it.type }).containsExactly(
            IssueReporter.Type.BUILD_TOOLS_TOO_LOW.type
        )
        issueReporter.reportError(IssueReporter.Type.BUILD_TOOLS_TOO_LOW, RuntimeException(""))
        assertThat(issueReporter.getAllIssuesAndClear().map { it.type }).isEmpty()
    }
}