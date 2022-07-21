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

package com.android.build.gradle.integration.instantexecution

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BasicInstantExecutionTest {

    private val app = MinimalSubProject.app("com.app")
    private val lib = MinimalSubProject.lib("com.lib")

    @JvmField
    @Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":lib", lib)
                .subproject(
                    ":test",
                    MinimalSubProject
                        .test("com.test")
                        .appendToBuild("android.targetProjectPath ':app'")
                )
                .build()
        )
        .enableProfileOutput()
        .create()

    @Before
    fun setUp() {
        project.projectDir.resolve(".gradle/configuration-cache").deleteRecursively()
    }

    @Test
    fun testUpToDate() {
        executor().run("assemble")
        assertThat(project.projectDir.resolve(".gradle/configuration-cache")).isDirectory()
        val result = executor().run("assemble")
        // AnalyticsRecordingTask and AndroidLintTextOutputTask always run
        Truth.assertThat(result.didWorkTasks).containsExactly(
            ":app:analyticsRecordingRelease",
            ":app:lintVitalRelease",
            ":app:assembleRelease",
            ":app:assemble")
    }

    @Test
    fun testCleanBuild() {
        executor().run("assemble")
        executor().run("clean")
        executor().run("assemble")
    }

    @Test
    fun testWhenInvokedFromTheIde() {
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .run("assemble")

        assertThat(project.projectDir.resolve(".gradle/configuration-cache")).isDirectory()
        executor().run("clean")
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .run("assemble")
    }

    /** Regression test for b/146659187. */
    @Test
    fun testWithJniMerging() {
        project.getSubproject("app").file("src/main/jniLibs/subDir/empty.so").also {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        executor().run(":app:mergeDebugJniLibFolders")
        executor().run("clean")
        executor().run(":app:mergeDebugJniLibFolders")
    }

    @Test
    fun testAndroidTestBuild() {
        executor().run(":app:assembleDebugAndroidTest")
        executor().run("clean")
        executor().run(":app:assembleDebugAndroidTest")
    }

    @Test
    fun testWithProjectIsolation() {
        // TODO(b/228885012): Make plugin version checks compatible with project isolation.
        executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.PROJECT_ISOLATION)
            .withArgument("-Dorg.gradle.unsafe.configuration-cache.max-problems=27")
            .run("assemble")
    }

    private fun executor(): GradleTaskExecutor =
        project.executor()
            .withLoggingLevel(LoggingLevel.LIFECYCLE)
            .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
}
