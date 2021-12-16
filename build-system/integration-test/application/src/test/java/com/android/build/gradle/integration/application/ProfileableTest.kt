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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.SigningHelper
import com.android.build.gradle.options.StringOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import sun.security.x509.X500Name
import kotlin.jvm.Throws

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
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run("assembleRelease")
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE_SIGNED)
        val verificationResult = SigningHelper.assertApkSignaturesVerify(apk, 14)
        assertThat(
            (verificationResult.signerCertificates.first().subjectDN as X500Name).commonName
        ).isEqualTo("Android Debug")
    }

    @Test
    fun `test dsl when profileable and debuggable enabled`() {
        val app = project.getSubproject(":app")
        app.buildFile.appendText("android.buildTypes.debug.debuggable true\n")
        app.buildFile.appendText("android.buildTypes.debug.profileable true\n")
        project.executor()
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run("assembleDebug")
        // Ensure profileable is not applied (debuggable dsl option overrides profileable).
        val manifest = ApkSubject.getManifestContent(
            project.getApkAsFile(GradleTestProject.ApkType.DEBUG).toPath())
        assertThat(manifest).doesNotContain(
            arrayListOf(
                "        E: application (line=11)",
                "            E: profileable (line=12)",
                "              A: http://schemas.android.com/apk/res/android:shell(0x01010594)=true"
            )
        )
    }

    @Test
    fun `test injecting the debug build type to be profileable`() {
        project.executor()
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .with(StringOption.PROFILING_MODE, "profileable")
            .run("assembleDebug")
        val app = project.getSubproject(":app")
        checkProjectContainsProfileableInManifest(app, GradleTestProject.ApkType.DEBUG)
    }

    @Test
    fun `test injecting the release build type to be profileable`() {
        project.executor()
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .with(StringOption.PROFILING_MODE, "profileable")
            .run("assembleRelease")
        val app = project.getSubproject(":app")
        checkProjectContainsProfileableInManifest(app, GradleTestProject.ApkType.RELEASE_SIGNED)
    }

    private fun checkProjectContainsProfileableInManifest(
        project: GradleTestProject,
        apkType: GradleTestProject.ApkType
    ) {
        val manifest = ApkSubject.getManifestContent(project.getApkAsFile(apkType).toPath())
        assertThat(manifest).containsAtLeastElementsIn(
            arrayListOf(
                "        E: application (line=11)",
                "            E: profileable (line=12)",
                "              A: http://schemas.android.com/apk/res/android:shell(0x01010594)=true"
            )
        )
    }
}
