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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Test that library instrumentation tests are signed, even when using a build type other than debug
 *
 * See https://issuetracker.google.com/111118208
 */
class LibraryInstrumentationTestSigningTest {

    private val lib = MinimalSubProject.lib("com.example.lib")

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(lib).create()

    @Test
    fun checkDebugSigning() {
        project.executor().run("assembleDebugAndroidTest")
        project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG).use { testApk ->
            assertThat(testApk).containsApkSigningBlock()
        }
    }

    @Test
    fun checkReleaseSigning() {
        project.buildFile.appendText("""
            android.testBuildType 'release'
            """)
        project.executor().run("assembleReleaseAndroidTest")
        project.getApk(ANDROIDTEST_RELEASE).use { testApk ->
            assertThat(testApk).containsApkSigningBlock()
        }
    }

    companion object {
        val ANDROIDTEST_RELEASE = GradleTestProject.ApkType.of("release", "androidTest", true)
    }
}
