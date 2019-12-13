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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
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
        ).create()

    @Test
    fun testUpToDate() {
        executor().run("assembleDebug")

        assertThat(project.testDir.resolve(".instant-execution-state")).isDirectory()
        executor().run("assembleDebug")
    }

    @Test
    fun testCleanBuild() {
        executor().run("assembleDebug")
        executor().run("clean", "assembleDebug")
    }

    @Test
    fun testWhenInvokedFromTheIde() {
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .run("assembleDebug")

        assertThat(project.testDir.resolve(".instant-execution-state")).isDirectory()
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .run("assembleDebug")
    }

    private fun executor(): GradleTaskExecutor =
        project.executor().withLoggingLevel(LoggingLevel.LIFECYCLE).withArgument("-Dorg.gradle.unsafe.instant-execution=true")
}