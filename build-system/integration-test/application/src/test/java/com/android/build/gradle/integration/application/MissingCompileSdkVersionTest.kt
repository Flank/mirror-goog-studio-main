/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.SyncIssue
import com.android.sdklib.SdkVersionInfo
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import java.io.File

class MissingCompileSdkVersionTest {

    private lateinit var sdkHome: File

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withSdk(false)
        .create()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        sdkHome = project.file("local-sdk-for-test")
        FileUtils.mkdirs(sdkHome)

        TestFileUtils.appendToFile(
            project.localProp,
            System.lineSeparator()
                    + SdkConstants.SDK_DIR_PROPERTY
                    + " = "
                    + sdkHome.absolutePath.replace("\\", "\\\\"))

        TestFileUtils.searchAndReplace(
            project.buildFile,
            "compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION,
            ""
        )
    }

    /**
     * Tests the sync finishes successfully when compileSdkVersion is missing.
     * It also checks that compile version is set to the default one when no SDKs are installed.
     */
    @Test
    @Throws(Exception::class)
    fun testSyncIsSuccessful() {
        // Sync should complete successfully
        val modelContainer = project.model().ignoreSyncIssues().fetchAndroidProjects()
        val syncIssues = modelContainer.onlyModelSyncIssues

        val compileSdkNotSetSyncIssues = syncIssues.filter { it.type == SyncIssue.TYPE_COMPILE_SDK_VERSION_NOT_SET }
        assertThat(compileSdkNotSetSyncIssues).hasSize(1)
        val compileSdkIssue = compileSdkNotSetSyncIssues.elementAt(0)
        assertThat(compileSdkIssue.message).isEqualTo(
            "compileSdkVersion is not specified. Please add it to build.gradle")

        val missingSdkPackageSyncIssues = syncIssues.filter { it.type == SyncIssue.TYPE_MISSING_SDK_PACKAGE }
        assertThat(missingSdkPackageSyncIssues).hasSize(1)
        val missingSdkIssue = missingSdkPackageSyncIssues.elementAt(0)
        assertThat(missingSdkIssue.message).contains(
            "Failed to find target with hash string 'android-${SdkVersionInfo.HIGHEST_KNOWN_STABLE_API}'")

        assertThat(modelContainer.onlyModel.compileTarget).isEqualTo(
            "android-${SdkVersionInfo.HIGHEST_KNOWN_STABLE_API}")
    }

    /**
     * Tests that compile version is set to the highest one installed.
     */
    @Test
    @Throws(Exception::class)
    fun testHighestSdkInstalledIsSelected() {
        installPlatform("24")
        installPlatform("23")

        val model = project.model().ignoreSyncIssues().fetchAndroidProjects().onlyModel

        assertThat(model.compileTarget).isEqualTo("android-24")
    }


    /** Tests that missing compileSdkVersion breaks the regular build. */
    @Test
    @Throws(Exception::class)
    fun testRegularBuildBreaks() {
        project.executor().expectFailure().run("assembleDebug")
    }

    private fun installPlatform(version: String) {
        FileUtils.copyDirectoryToDirectory(
            TestUtils.getSdk().resolve(SdkConstants.FD_PLATFORMS).resolve("android-$version")
                    .toFile(),
            FileUtils.join(sdkHome, SdkConstants.FD_PLATFORMS)
        )
    }
}
