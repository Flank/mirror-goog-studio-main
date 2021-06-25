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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidLintInputsTest {

    @Test
    fun `check default when override is not set`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = false)
        val lintVersion = getLintMavenArtifactVersion(
            versionOverride = null,
            reporter = issueReporter,
            defaultVersion = "30.0.0-rc01",
            agpVersion = "7.0.0-rc01",
        )
        assertThat(lintVersion).isEqualTo("30.0.0-rc01")
        assertThat(issueReporter.errors).hasSize(0)
        assertThat(issueReporter.warnings).hasSize(0)
    }

    @Test
    fun `check lint version can be overridden with a valid newer version`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = false)
        val lintVersion = getLintMavenArtifactVersion(
            versionOverride = "7.1.0-alpha04",
            reporter = issueReporter,
            defaultVersion = "30.0.0-rc01",
            agpVersion = "7.0.0-rc01",
        )
        assertThat(lintVersion).isEqualTo("30.1.0-alpha04")
        assertThat(issueReporter.errors).hasSize(0)
        assertThat(issueReporter.warnings).hasSize(0)
    }

    @Test
    fun `check lint version override with invalid version`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = false)
        val lintVersion = getLintMavenArtifactVersion(
            versionOverride = "+",
            reporter = issueReporter,
            defaultVersion = "30.0.0-rc01",
            agpVersion = "7.0.0-rc01",
        )
        assertThat(lintVersion).isEqualTo("30.0.0-rc01")
        assertThat(issueReporter.errors).hasSize(1)
        assertThat(issueReporter.warnings).hasSize(0)
        assertThat(issueReporter.errors.single()).isEqualTo(
            """
                Could not parse lint version override '+'
                Recommendation: Remove or update the gradle property android.experimental.lint.version to be at least 7.0.0-rc01
                """.trimIndent()
        )
    }

    @Test
    fun `check lint version override with slightly outdated version`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = false)

        val lintVersion = getLintMavenArtifactVersion(
            versionOverride = "7.0.0-alpha05",
            reporter = issueReporter,
            defaultVersion = "30.1.0",
            agpVersion = "7.1.0",
        )
        assertThat(lintVersion).isEqualTo("30.0.0-alpha05")
        assertThat(issueReporter.errors).hasSize(0)
        assertThat(issueReporter.warnings).hasSize(1)
        assertThat(issueReporter.warnings.single()).isEqualTo(
            """
                The build will use lint version 7.0.0-alpha05 which is older than the default.
                Recommendation: Remove or update the gradle property android.experimental.lint.version to be at least 7.1.0
            """.trimIndent()
        )
    }

    @Test
    fun `check lint version override with an significantly outdated version`() {
        val issueReporter = FakeSyncIssueReporter(throwOnError = false)
        val lintVersion = getLintMavenArtifactVersion(
            versionOverride = "7.0.0",
            reporter = issueReporter,
            defaultVersion = "31.0.0-rc01",
            agpVersion = "8.0.0-rc01",
        )
        assertThat(lintVersion).isEqualTo("31.0.0-rc01")
        assertThat(issueReporter.errors).hasSize(1)
        assertThat(issueReporter.warnings).hasSize(0)
        assertThat(issueReporter.errors.single()).isEqualTo(
            """
                Lint must be at least version 8.0.0, and is recommended to be at least 8.0.0-rc01
                Recommendation: Remove or update the gradle property android.experimental.lint.version to be at least 8.0.0-rc01
                """.trimIndent()
        )
    }
}

