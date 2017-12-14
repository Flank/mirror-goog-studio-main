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
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class IncorrectDslUsageTest {

    @get:Rule
    var project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create()

    @Test
    fun incorrectSupportedAbisAndSplitsInformation() {

            TestFileUtils.appendToFile(project.buildFile, "\n" +
                    "apply plugin: 'com.android.application'\n" +
                    "android {\n" +
                    "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                    "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                    "    defaultConfig {\n" +
                    "        applicationId \"demo.bug\"\n" +
                    "        minSdkVersion 21\n" +
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
                    "            universalApk false\n" +
                    "        }\n" +
                    "      }\n" +
                    "}\n")

        // Query the model to get the incorrect DSL declaration.
        val model : AndroidProject = project.model().ignoreSyncIssues().single.onlyModel

        val dslIssue = TruthHelper.assertThat(model).hasIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_GENERIC)

        assertThat(dslIssue.message).contains("x86")
        assertThat(dslIssue.message).contains("armeabi-v7a")
    }
}

