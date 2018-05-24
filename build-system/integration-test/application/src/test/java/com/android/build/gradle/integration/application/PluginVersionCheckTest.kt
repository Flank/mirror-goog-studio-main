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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.SyncIssue.SEVERITY_ERROR
import com.android.builder.model.SyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Integration test for minimum plugin version checks. */
class PluginVersionCheckTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun testCrashlyticsPlugin() {
        // Use an old version of the Crashlytics plugin, expect sync issues
        TestFileUtils.searchVerbatimAndReplace(
            project.buildFile,
            "android {\n",
            "buildscript {\n"
                    + "    dependencies {\n"
                    + "        classpath 'io.fabric.tools:gradle:1.22.1'\n"
                    + "    }\n"
                    + "}\n"
                    + "apply plugin: 'io.fabric'\n\n"
                    + "android {\n"
        )

        val model =
            project.model().withArgument(
                "-Duser.home=" + CrashlyticsTest.getCustomUserHomeForCrashlytics(project)
            )
                .ignoreSyncIssues().fetchAndroidProjects()
        val syncIssues = model.onlyModel.syncIssues

        assertThat(syncIssues).hasSize(1)
        val syncIssue = syncIssues.iterator().next()

        assertThat(syncIssue.type).isEqualTo(TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD)
        assertThat(syncIssue.severity).isEqualTo(SEVERITY_ERROR)
        assertThat(syncIssue.message).contains(
            "The minimum supported version of the Crashlytics plugin" +
                    " (io.fabric.tools:gradle) is 1.25.4." +
                    " Project 'project' is using version 1.22.1."
        )
    }
}