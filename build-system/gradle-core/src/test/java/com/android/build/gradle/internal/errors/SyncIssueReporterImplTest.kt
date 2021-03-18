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
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Test
import kotlin.test.assertFailsWith

class SyncIssueReporterImplTest {

    val issueReporter = object : SyncIssueReporterImpl.GlobalSyncIssueService() {
        override fun getParameters(): Parameters {
            return object : Parameters {
                override val mode: Property<SyncOptions.EvaluationMode>
                    get() = FakeGradleProperty(SyncOptions.EvaluationMode.IDE)
                override val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
                    get() = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
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
        issueReporter.reportError(IssueReporter.Type.COMPILE_SDK_VERSION_NOT_SET, RuntimeException(""))
        assertThat(issueReporter.getAllIssuesAndClear().map { it.type }).containsExactly(
            IssueReporter.Type.COMPILE_SDK_VERSION_NOT_SET.type
        )
    }

    @Test
    fun testCloseReportsRemainingErrors() {
        issueReporter.reportError(IssueReporter.Type.BUILD_TOOLS_TOO_LOW, RuntimeException("Error: 1"))
        issueReporter.reportError(IssueReporter.Type.COMPILE_SDK_VERSION_NOT_SET, RuntimeException("Error: 2"))
        issueReporter.reportError(IssueReporter.Type.EDIT_LOCKED_DSL_VALUE, RuntimeException("Error: 3"))
        issueReporter.reportWarning(IssueReporter.Type.DEPRECATED_DSL, RuntimeException("Warning"))

        val issueException = assertFailsWith<EvalIssueException> {
            issueReporter.close()
        }
        assertThat(issueException.suppressed).hasLength(2)
        // Ordering might change, so assert about the three errors together
        val errors = issueException.suppressed.asList() + issueException
        assertThat(errors.map { it.message }).containsExactly("Error: 1", "Error: 2", "Error: 3")
    }

    @Test
    fun testReportingAfterClose() {
        issueReporter.reportError(IssueReporter.Type.BUILD_TOOLS_TOO_LOW, RuntimeException(""))
        assertThat(issueReporter.getAllIssuesAndClear().map { it.type }).containsExactly(
            IssueReporter.Type.BUILD_TOOLS_TOO_LOW.type
        )
        issueReporter.close()
        val failure = assertFailsWith<IllegalStateException> {
            issueReporter.reportError(IssueReporter.Type.BUILD_TOOLS_TOO_LOW, RuntimeException(""))
        }
        assertThat(failure).hasMessageThat().isEqualTo("Issue registered after handler locked.")
    }
}
