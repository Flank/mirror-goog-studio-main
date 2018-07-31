/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint

import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.SecureRandomDetector
import com.android.tools.lint.detector.api.Detector
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.LINT_SESSION
import com.google.wireless.android.sdk.stats.LintIssueId.LintSeverity.ERROR_SEVERITY
import com.google.wireless.android.sdk.stats.LintIssueId.LintSeverity.IGNORE_SEVERITY
import com.google.wireless.android.sdk.stats.LintSession.AnalysisType.BUILD

class LintBatchAnalyticsTest : AbstractCheckTest() {
    // Used to test the scheduling of usage tracking.
    private lateinit var scheduler: VirtualTimeScheduler
    // A UsageTracker implementation that allows introspection of logged metrics in tests.
    private lateinit var usageTracker: TestUsageTracker

    override fun setUp() {
        super.setUp()
        scheduler = VirtualTimeScheduler()
        val analyticsSettings = AnalyticsSettingsData()
        analyticsSettings.optedIn = true
        AnalyticsSettings.setInstanceForTest(analyticsSettings)
        usageTracker = TestUsageTracker(scheduler)
        UsageTracker.setWriterForTest(usageTracker)
    }

    override fun tearDown() {
        usageTracker.close()
        UsageTracker.cleanAfterTesting()
        super.tearDown()
    }

    override fun getDetector(): Detector {
        return SecureRandomDetector()
    }

    fun testAnalytics() {
        val project = getProjectDir(
            null, manifest().minSdk(1),
            java(
                """
                package test.pkg;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MyTest {
                    String s1 = "/sdcard/mydir";
                    String s2 = "/sdcard/mydir";
                }
                """
            ).indented()
        )
        MainTest.checkDriver(
            null,
            null,

            // Expected exit code
            LintCliFlags.ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--check",
                "SdCardPath",
                "--sdk-home", // SDK is needed to get version number for the baseline
                TestUtils.getSdk().path,
                "--disable",
                "LintError",
                "-Werror",
                project.path
            ),
            null,
            null
        )

        val usages = usageTracker.usages
        assertEquals(1, usages.size)
        assertThat(usages).hasSize(1)
        val usage = usages[0]
        val event = usage.studioEvent
        assertThat(event.kind).isEqualTo(LINT_SESSION)
        val session = event.lintSession
        with(session) {
            assertThat(analysisType).isEqualTo(BUILD)
            assertThat(baselineEnabled).isFalse()
            assertThat(warningsAsErrors).isTrue()
        }

        val issues = session.issueIdsList
        assertEquals(2, issues.size)
        val issue1 = issues[0]
        with(issues[0]) {
            assertThat(issueId).isEqualTo("SdCardPath")
            assertThat(count).isEqualTo(2)
            assertThat(severity).isEqualTo(ERROR_SEVERITY)
        }
        // Just disabled: count is 0 but communicated since it differs from default
        with(issues[1]) {
            assertThat(issueId).isEqualTo("LintError")
            assertThat(count).isEqualTo(0)
            assertThat(severity).isEqualTo(IGNORE_SEVERITY)
        }

        val performance = session.lintPerformance
        with(performance) {
            assertThat(fileCount).isEqualTo(1)
            assertThat(moduleCount).isEqualTo(1)
            assertThat(javaSourceCount).isEqualTo(1)
            assertThat(kotlinSourceCount).isEqualTo(0)
            assertThat(testSourceCount).isEqualTo(0)
            assertThat(resourceFileCount).isEqualTo(0)
        }
    }
}
