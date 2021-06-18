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

package com.android.build.gradle.integration.packaging

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.builder.internal.packaging.ApkCreatorType
import com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER
import com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(FilterableParameterized::class)
class DeterministicApkTest(private val apkCreatorType: ApkCreatorType) {

    companion object {
        @Parameterized.Parameters(name = "apkCreatorType_{0}")
        @JvmStatic
        fun params() = listOf(APK_Z_FILE_CREATOR, APK_FLINGER)
    }

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @get:Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .setApkCreatorType(apkCreatorType)
            .create()

    @Test
    fun cleanReleaseBuildDeterministicTest() {
        // First we build the release APK as-is
        project.execute(":assembleRelease")
        val apk1 = project.getApk(GradleTestProject.ApkType.RELEASE)
        assertThat(apk1).exists()
        val byteArray1 = apk1.file.toFile().readBytes()

        // Then clean, build again, and assert that APK is the same as the original
        project.execute("clean", ":assembleRelease")
        val apk2 = project.getApk(GradleTestProject.ApkType.RELEASE)
        assertThat(apk2).exists()
        val byteArray2 = apk2.file.toFile().readBytes()
        assertThat(byteArray2).isEqualTo(byteArray1)
    }

    @Test
    fun cleanDebugBuildDeterministicTest() {
        // Deterministic builds for debug APKs supported only with zipflinger
        Assume.assumeTrue(apkCreatorType == APK_FLINGER)

        // First we build the debug APK as-is
        project.execute(":assembleDebug")
        val apk1 = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk1).exists()
        val byteArray1 = apk1.file.toFile().readBytes()

        // Then clean, build again, and assert that APK is the same as the original
        project.execute("clean", ":assembleDebug")
        val apk2 = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk2).exists()
        val byteArray2 = apk2.file.toFile().readBytes()
        assertThat(byteArray2).isEqualTo(byteArray1)
    }
}
