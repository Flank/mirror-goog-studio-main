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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Integration test for minimum plugin version checks. */
class PluginVersionCheckTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(
                    MultiModuleTestProject.builder()
                            .subproject("lib", MinimalSubProject.lib("com.example.lib"))
                            .build()
            ).create()

    @Test
    fun testButterKnifeTooOld() {
        // Use an old version of the ButterKnife plugin, expect sync issues
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                buildscript {
                    dependencies {
                        classpath 'com.jakewharton:butterknife-gradle-plugin:9.0.0-rc1'
                    }
                }
                """.trimIndent()
        )
        TestFileUtils.appendToFile(
                project.getSubproject("lib").buildFile,
                "apply plugin: 'com.jakewharton.butterknife'"
        )

        val model = project.modelV2().ignoreSyncIssues().fetchModels()
        val syncIssues = model.container.getNonDeprecationIssues()
        assertThat(syncIssues).hasSize(1)
        val syncIssue = syncIssues.single()

        assertThat(syncIssue.type).isEqualTo(SyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD)
        assertThat(syncIssue.severity).isEqualTo(SyncIssue.SEVERITY_ERROR)
        val expected = "The Android Gradle plugin supports only Butterknife Gradle " +
                "plugin version 9.0.0-rc2 and higher.\n" +
                "The following dependencies do not satisfy the required version:\n" +
                "root project 'project' -> " +
                "com.jakewharton:butterknife-gradle-plugin:9.0.0-rc1"
        assertThat(syncIssue.message).isEqualTo(expected)

        val failure = project.executor().expectFailure().run("generateDebugR2")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains(expected)
        }
    }

    @Test
    fun testButterKnifeOk() {
        // Use a sufficiently new version of the ButterKnife plugin, expect no sync issues
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                buildscript {
                    dependencies {
                        classpath 'com.jakewharton:butterknife-gradle-plugin:9.0.0-rc2'
                    }
                }
                """.trimIndent()
        )
        TestFileUtils.appendToFile(
                project.getSubproject("lib").buildFile,
                "apply plugin: 'com.jakewharton.butterknife'"
        )

        val model = project.modelV2().fetchModels()
        assertThat(model.container.getNonDeprecationIssues()).isEmpty()

        project.executor().run("generateDebugR2")
    }

    private fun ModelContainerV2.getNonDeprecationIssues(): List<SyncIssue> {
        val issueModel = this.singleProjectInfo.issues ?: throw RuntimeException("Failed to get issue model")
        return issueModel.syncIssues.filter { it.type != SyncIssue.TYPE_DEPRECATED_DSL }
    }

    @Test
    fun testKotlinAndroidExtensionsTooOld() {
        // Use an old version of the kotlin-android-extensions plugin, expect sync issues
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                buildscript {
                    dependencies {
                        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10"
                    }
                }
                """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("lib").buildFile,
            """
                apply plugin: 'kotlin-android'
                apply plugin: 'kotlin-android-extensions'
            """.trimIndent()
        )

        val model = project.modelV2().ignoreSyncIssues().fetchModels()
        val syncIssues = model.container.getNonDeprecationIssues()
        assertThat(syncIssues).hasSize(1)
        val syncIssue = syncIssues.single()

        assertThat(syncIssue.type).isEqualTo(SyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD)
        assertThat(syncIssue.severity).isEqualTo(SyncIssue.SEVERITY_ERROR)
        val expected = "The Android Gradle plugin supports only kotlin-android-extensions Gradle " +
                "plugin version 1.6.20 and higher.\n" +
                "The following dependencies do not satisfy the required version:\n" +
                "root project 'project' -> " +
                "org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10"
        assertThat(syncIssue.message).isEqualTo(expected)

        val failure = project.executor().expectFailure().run("assembleDebug")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains(expected)
        }
    }

    @Test
    fun testKotlinAndroidExtensionsOk() {
        // Use a sufficiently new version of the kotlin-android-extensions plugin, expect no sync
        // issues
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                buildscript {
                    dependencies {
                        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${TestUtils.KOTLIN_VERSION_FOR_TESTS}"
                    }
                }
                """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("lib").buildFile,
            """
                apply plugin: 'kotlin-android'
                apply plugin: 'kotlin-android-extensions'
            """.trimIndent()
        )

        val model = project.modelV2().fetchModels()
        assertThat(model.container.getNonDeprecationIssues()).isEmpty()

        project.executor().run("assembleDebug")
    }
}
