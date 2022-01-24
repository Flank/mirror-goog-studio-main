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
package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.checkSingleIssue
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.StringOption
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AbiRelatedDslUsageTest {

    @get:Rule
    var project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create()

    @Test
    fun incorrectSupportedAbisAndSplitsInformation() {
        testSupportedAbisAndSplitsInformation(false)
    }

    @Test
    fun correctSupportedAbisAndSplitsInformation() {
        testSupportedAbisAndSplitsInformation(true)
    }

    private fun testSupportedAbisAndSplitsInformation(isUniversalApkRequested: Boolean) {
        TestFileUtils.appendToFile(project.buildFile, "\n" +
                "apply plugin: 'com.android.application'\n" +
                "android {\n" +
                "    namespace \"${HelloWorldApp.NAMESPACE}\"\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                "    defaultConfig {\n" +
                "        applicationId \"demo.bug\"\n" +
                "        minSdkVersion 21\n" +
                "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n" +
                "        targetSdkVersion 27\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "        testInstrumentationRunner \"android.support.test.runner.AndroidJUnitRunner\"\n" +
                "        ndk {\n" +
                "            abiFilters \"x86\"\n" +
                "        }\n" +
                "      }\n" +
                "      splits {\n" +
                "        abi {\n" +
                "            enable true\n" +
                "            reset()\n" +
                "            include 'armeabi-v7a'\n" +
                "            universalApk $isUniversalApkRequested \n" +
                "        }\n" +
                "      }\n" +
                "}\n")

        // Query the model to get the incorrect DSL declaration.
        val result = project.modelV2().ignoreSyncIssues().fetchModels()

        val rootBuild = result.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")

        if (isUniversalApkRequested) {
            assertThat(issues).isEmpty()
        } else {
            issues.checkSingleIssue(
                type = com.android.builder.model.v2.ide.SyncIssue.TYPE_GENERIC,
                severity = com.android.builder.model.v2.ide.SyncIssue.SEVERITY_ERROR,
                message = "Conflicting configuration : 'x86' in ndk abiFilters cannot be present when splits abi filters are set : armeabi-v7a"
            )
        }
    }

    @Test
    fun incorrectAbiRequestedWithNdkFilters() {
        TestFileUtils.appendToFile(project.buildFile, "\n" +
                "apply plugin: 'com.android.application'\n" +
                "android {\n" +
                "    namespace \"${HelloWorldApp.NAMESPACE}\"\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                "    defaultConfig {\n" +
                "        applicationId \"demo.bug\"\n" +
                "        minSdkVersion 21\n" +
                "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n" +
                "        targetSdkVersion 27\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "        ndk {\n" +
                "            abiFilters \"x86\"\n" +
                "        }\n" +
                "      }\n" +
                "}\n")

        // Query the model to get the incorrect ABI target.
        val result = project.modelV2()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "mips")
            .ignoreSyncIssues()
            .fetchModels()

        val rootBuild = result.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")

        issues.checkSingleIssue(
            type = com.android.builder.model.v2.ide.SyncIssue.TYPE_GENERIC,
            severity = com.android.builder.model.v2.ide.SyncIssue.SEVERITY_WARNING,
            message = "Cannot build selected target ABI: mips, supported ABIs are: x86"
        )
    }

    @Test
    fun incorrectAbiRequestedWithSplits() {
        TestFileUtils.appendToFile(project.buildFile, "\n" +
                "apply plugin: 'com.android.application'\n" +
                "android {\n" +
                "    namespace \"${HelloWorldApp.NAMESPACE}\"\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                "    defaultConfig {\n" +
                "        applicationId \"demo.bug\"\n" +
                "        minSdkVersion 21\n" +
                "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n" +
                "        targetSdkVersion 27\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "      }\n" +
                "      splits {\n" +
                "        abi {\n" +
                "            enable true\n" +
                "            reset()\n" +
                "            include 'armeabi-v7a'\n" +
                "            universalApk false\n" +
                "        }\n" +
                "      }\n" +
                "}\n")

        // Query the model to get the incorrect ABI target.
        val result = project.modelV2()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .ignoreSyncIssues()
            .fetchModels()

        val rootBuild = result.container.getProject(":")
        val issues = rootBuild.issues?.syncIssues ?: throw RuntimeException("Missing issues model")

        issues.checkSingleIssue(
            type = com.android.builder.model.v2.ide.SyncIssue.TYPE_GENERIC,
            severity = com.android.builder.model.v2.ide.SyncIssue.SEVERITY_WARNING,
            message = "Cannot build selected target ABI: x86, supported ABIs are: FilterConfiguration(filterType=ABI, identifier=armeabi-v7a)"
        )
    }
}
