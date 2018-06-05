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

package com.android.build.gradle.internal.crash

import com.android.tools.analytics.AnalyticsSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import com.android.build.gradle.internal.crash.PluginCrashReporter.maybeReportExceptionForTest as reportForTest

class PluginCrashReporterTest {
    @Test
    fun testUserOptOut() {
        val settings = AnalyticsSettings()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = false
        assertThat(reportForTest(NullPointerException(), settings)).isFalse()
    }

    @Test
    fun testReportingWhiteListedException() {
        val settings = AnalyticsSettings()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = true

        assertThat(reportForTest(NullPointerException(), settings)).isTrue()
        assertThat(reportForTest(RuntimeException(NullPointerException()), settings))
            .isTrue()
        assertThat(
            reportForTest(RuntimeException(RuntimeException(NullPointerException())), settings)
        ).isTrue()
    }

    @Test
    fun testReportingNonWhiteListedException() {
        val settings = AnalyticsSettings()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = true

        assertThat(reportForTest(RuntimeException(), settings)).isFalse()
        assertThat(reportForTest(IllegalStateException(RuntimeException()), settings)).isFalse()
    }

    @Test
    fun testExternalApiUsageException() {
        val settings = AnalyticsSettings()
        AnalyticsSettings.setInstanceForTest(settings)
        settings.optedIn = true

        assertThat(reportForTest(ExternalApiUsageException(RuntimeException()), settings)).isFalse()
    }
}
