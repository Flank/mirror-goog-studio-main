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

package com.android.build.gradle.integration.application

import com.android.apksig.ApkVerifier
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.SigningHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

/**
 * Tests verifying that builds using the profileable option are configured correctly.
 * For example, including the profileable tag in AndroidManifest, disable debuggable features and
 * doesn't use release signing configs etc.
 */
class ProfileableTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(
        MultiModuleTestProject.builder()
            .subproject(":app", MinimalSubProject.app("com.profilabletest.app")).build()
    ).create()

    @get:Rule
    val temporaryDirectory = TemporaryFolder()

    @Test
    fun `test dsl setting the release build type to be profileable`() {
        val app = project.getSubproject(":app")
        app.buildFile.appendText("android.buildTypes.release.profileable true")
        project.executor()
                .with(BooleanOption.ENABLE_DEFAULT_DEBUG_SIGNING_CONFIG, true)
                .run("assembleRelease")
        val apkSigned =
                project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE_SIGNED)
        val verificationResult = SigningHelper.assertApkSignaturesVerify(apkSigned, 30)
        assertThat(
            verificationResult.signerCertificates.first().subjectX500Principal.name
        ).isEqualTo("C=US,O=Android,CN=Android Debug")
        val manifest = ApkSubject.getManifestContent(apkSigned.file.toAbsolutePath())
        assertThat(manifest).containsAtLeastElementsIn(
            arrayListOf(
                "        E: application (line=11)",
                "            E: profileable (line=12)",
                "              A: http://schemas.android.com/apk/res/android:enabled(0x0101000e)=true",
                "              A: http://schemas.android.com/apk/res/android:shell(0x01010594)=true"
            )
        )

        // Test no signing config configured, if the automatic signing config assignment is disabled.
        project.executor().with(BooleanOption.ENABLE_DEFAULT_DEBUG_SIGNING_CONFIG, false)
                .run("clean", "assembleRelease")
        val apkUnsigned =
                project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE)
        ApkSubject.assertThat(apkUnsigned).doesNotContainApkSigningBlock()
    }

    @Test
    fun `test dsl when profileable and debuggable enabled`() {
        val app = project.getSubproject(":app")
        app.buildFile.appendText("android.buildTypes.debug.debuggable true\n")
        app.buildFile.appendText("android.buildTypes.debug.profileable true\n")
        val result = project.executor().run("assembleDebug")
        // Ensure profileable is not applied (debuggable dsl option overrides profileable).
        val manifest = ApkSubject.getManifestContent(
            project.getApkAsFile(GradleTestProject.ApkType.DEBUG).toPath()
        )
        assertThat(manifest).doesNotContain(
            arrayListOf(
                "        E: application (line=11)",
                "            E: profileable (line=12)",
                "              A: http://schemas.android.com/apk/res/android:shell(0x01010594)=true"
            )
        )
        result.stdout.use { out ->
            ScannerSubject.assertThat(out).contains(
                ":app build type 'debug' can only have debuggable or profileable enabled.\n" +
                        "Only one of these options can be used at a time.\n" +
                        "Recommended action: Only set one of debuggable=true and profileable=true.\n"
            )
        }
    }

    @Test
    fun `test injecting the debug build type to be profileable`() {
        val app = project.getSubproject(":app")
        val result = project.executor()
                .with(StringOption.PROFILING_MODE, "profileable")
                .run("assembleDebug")
        assertThat(result.tasks.filter { it.lowercase(Locale.US).contains("lint") })
                .named("Lint tasks")
                .isEmpty()
        checkProjectContainsProfileableInManifest(app, GradleTestProject.ApkType.DEBUG)
    }

    @Test
    fun `test injecting the release build type to be profileable`() {
        val app = project.getSubproject(":app")
        val result = project.executor()
            .with(StringOption.PROFILING_MODE, "profileable")
            .with(BooleanOption.ENABLE_DEFAULT_DEBUG_SIGNING_CONFIG, true)
            .run("assembleRelease")
        assertThat(result.tasks.filter { it.lowercase(Locale.US).contains("lint") })
                .named("Lint tasks")
                .isEmpty()
        checkProjectContainsProfileableInManifest(app, GradleTestProject.ApkType.RELEASE_SIGNED)
    }

    @Test
    fun `build with minSdk less than 30 fails`() {
        val app = project.getSubproject(":app")
        TestFileUtils.searchAndReplace(
            app.buildFile.absoluteFile,
            "android.compileSdkVersion $DEFAULT_COMPILE_SDK_VERSION",
            "android.compileSdkVersion 29",
        )
        val result = project.executor()
            .with(StringOption.PROFILING_MODE, "profileable")
            .expectFailure()
            .run("assembleDebug")

        result.stderr.use { out ->
            ScannerSubject.assertThat(out).contains(
                "'profileable' is enabled with compile SDK <30."
            )
        }
    }

    private fun checkProjectContainsProfileableInManifest(
        project: GradleTestProject,
        apkType: GradleTestProject.ApkType
    ) {
        val manifest = ApkSubject.getManifestContent(project.getApkAsFile(apkType).toPath())
        assertThat(manifest).containsAtLeastElementsIn(
            arrayListOf(
                "        E: application (line=11)",
                "          A: http://schemas.android.com/apk/res/android:testOnly(0x01010272)=true",
                "            E: profileable (line=12)",
                "              A: http://schemas.android.com/apk/res/android:enabled(0x0101000e)=true",
                "              A: http://schemas.android.com/apk/res/android:shell(0x01010594)=true"
            )
        )
    }
}
