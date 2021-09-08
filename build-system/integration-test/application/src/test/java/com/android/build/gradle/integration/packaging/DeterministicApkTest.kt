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
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.RELEASE
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test to check that AGP produces deterministic APKs when it should.
 */
@RunWith(FilterableParameterized::class)
class DeterministicApkTest(
        private val debuggable: Boolean,
        private val fromIde: Boolean,
        private val forceDeterministicApk: Boolean,
) {

    companion object {
        @Parameterized.Parameters(name = "debuggable_{0}_fromIde_{1}_forceDeterministicApk_{2}")
        @JvmStatic
        fun params() = listOf(
                arrayOf(true, true, false),
                arrayOf(true, false, false),
                arrayOf(false, true, false),
                arrayOf(false, false, false),
                arrayOf(true, true, true)
        )
    }

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @get:Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .addFile(TestSourceFile("src/main/resources/foo.txt", "foo${System.lineSeparator()}"))
            .create()

    @Test
    fun cleanBuildDeterministicApkTest() {
        // clean debuggable builds from the IDE aren't deterministic, and we can't test that the
        // bytes don't match because they might match sometimes, so we skip this case.
        Assume.assumeFalse(debuggable && fromIde && !forceDeterministicApk)

        // First we build the APK as-is
        getExecutor().run(getAssembleTask())
        val apk1 = project.getApk(
                getApkType(),
                GradleTestProject.ApkLocation.Output
        )
        assertThat(apk1).exists()
        val byteArray1 = apk1.file.toFile().readBytes()

        // Then clean, build again, and assert that the APK is the same as the original
        getExecutor().run("clean", getAssembleTask())
        val apk2 = project.getApk(
                getApkType(),
                GradleTestProject.ApkLocation.Output
        )
        assertThat(apk2).exists()
        val byteArray2 = apk2.file.toFile().readBytes()
        assertThat(byteArray2).isEqualTo(byteArray1)
    }

    @Test
    fun incrementalBuildDeterministicApkTest() {
        // First we build the APK as-is
        getExecutor().run(getAssembleTask())
        val apk1 = project.getApk(
                getApkType(),
                GradleTestProject.ApkLocation.Output
        )
        assertThat(apk1).exists()
        val byteArray1 = apk1.file.toFile().readBytes()

        // Do an intermediate build in which we make foo.txt bigger, creating a larger APK.
        TestFileUtils.replaceLine(project.file("src/main/resources/foo.txt"), 1, "foo bar")
        getExecutor().run(getAssembleTask())

        // Then revert the change to foo.txt, build again, and assert that the APK is the same as
        // the original, unless it's debuggable and from the IDE, in which case it's expected to be
        // different because it will have a virtual entry.
        TestFileUtils.replaceLine(project.file("src/main/resources/foo.txt"), 1, "foo")
        getExecutor().run(getAssembleTask())
        val apk2 = project.getApk(
                getApkType(),
                GradleTestProject.ApkLocation.Output
        )
        assertThat(apk2).exists()
        val byteArray2 = apk2.file.toFile().readBytes()
        if (debuggable && !forceDeterministicApk) {
            assertThat(byteArray2).isNotEqualTo(byteArray1)
        } else {
            assertThat(byteArray2).isEqualTo(byteArray1)
        }
    }

    private fun getAssembleTask() = if (debuggable) "assembleDebug" else "assembleRelease"

    private fun getApkType() = if (debuggable) DEBUG else RELEASE

    private fun getExecutor(): GradleTaskExecutor =
        project.executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, fromIde)
            .with(BooleanOption.FORCE_DETERMINISTIC_APK, forceDeterministicApk)
}
