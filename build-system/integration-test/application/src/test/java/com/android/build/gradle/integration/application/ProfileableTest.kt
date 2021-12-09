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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.options.StringOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
    fun profilingModeOptionIsProfileable() {
        project.executor()
            .with(StringOption.PROFILING_MODE, "profileable")
            .run("assembleDebug")

        val app = project.getSubproject(":app")
        val manifest =
            ApkSubject.getManifestContent(
                app.getApkAsFile(GradleTestProject.ApkType.DEBUG)
                    .toPath()
            )
        assertThat(manifest).containsAtLeastElementsIn(
            arrayListOf(
                "        E: application (line=11)",
                "            E: profileable (line=12)",
                "              A: http://schemas.android.com/apk/res/android:shell(0x01010594)=true"
            )
        )
    }
}