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

package com.android.build.gradle.integration.analytics

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import org.junit.Rule
import org.junit.Test

/**
 * This test exists to ensure we record statistics properly when configuration caching is enabled.
 */
class AnalyticsConfigurationCachingTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .enableProfileOutput()
        .create()

    @Test
    fun buildLevelStatisticsExistInConfigurationCachedRun() {
        val capturer = ProfileCapturer(project)
        val nonCachedRun = capturer.capture { project.execute("assembleDebug") }.single()
        val configCachedRun = capturer.capture { project.execute("assembleDebug") }.single()
        Truth.assertThat(configCachedRun.gradleVersion).isEqualTo(nonCachedRun.gradleVersion)
    }

    @Test
    fun projectLevelStatisticsExistInConfigurationCachedRun() {
        val capturer = ProfileCapturer(project)
        val nonCachedRun = capturer.capture { project.execute("assembleDebug") }.single()
        val configCachedRun = capturer.capture { project.execute("assembleDebug") }.single()
        Truth.assertThat(configCachedRun.projectCount).isEqualTo(nonCachedRun.projectCount)
    }

    @Test
    fun testConfigurationSpans() {
        val capturer = ProfileCapturer(project)
        val nonCachedRun = capturer.capture { project.execute("assembleDebug") }.single()

        var configurationSpans = nonCachedRun.spanList.filter {
            it.type == ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE
        }
        Truth.assertThat(configurationSpans).isNotEmpty()

        // spans of config types(e.g. BASE_PLUGIN_PROJECT_CONFIGURE) should not exist
        // in configuration cached run
        val configCachedRun = capturer.capture { project.execute("assembleDebug") }.single()

        configurationSpans = configCachedRun.spanList.filter {
            it.type == ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE
        }
        Truth.assertThat(configurationSpans).isEmpty()
    }

    @Test
    fun testSpanIdAllocation() {
        val capturer = ProfileCapturer(project)
        val nonCachedRun = capturer.capture { project.execute("assembleDebug") }.single()

        // ensure uniqueness of allocated ids
        var allSpansWithId = nonCachedRun.spanList.filter { it.hasId() }
        var uniqueSpanIds = allSpansWithId.map { it.id }.distinct()
        Truth.assertThat(allSpansWithId.size).isEqualTo(uniqueSpanIds.size)
        // ensure id is allocated from a fixed number
        Truth.assertThat(uniqueSpanIds.min()).isEqualTo(2)
        val configCachedRun = capturer.capture { project.execute("assembleDebug") }.single()
        // ensure uniqueness of allocated ids
        allSpansWithId = configCachedRun.spanList.filter { it.hasId() }
        uniqueSpanIds = configCachedRun.spanList.map { it.id }.distinct()
        Truth.assertThat(allSpansWithId.size).isEqualTo(uniqueSpanIds.size)
        // ensure id is allocated from a fixed number
        Truth.assertThat(uniqueSpanIds.min()).isEqualTo(2)
    }

    @Test
    fun totalBuildTimeRecorded() {
        val capturer = ProfileCapturer(project)
        val nonCachedRun = capturer.capture { project.execute("assembleDebug") }.single()
        Truth.assertThat(nonCachedRun.buildTime).isGreaterThan(0)
        val configCachedRun = capturer.capture { project.execute("assembleDebug") }.single()

        Truth.assertThat(configCachedRun.buildTime).isGreaterThan(0)
        Truth.assertThat(nonCachedRun.buildTime).isGreaterThan(configCachedRun.buildTime)
    }
}
