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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import org.junit.Rule
import org.junit.Test

class RegisterExternalAptJavaOutputTest {
    @JvmField
    @Rule
    var project =
        GradleTestProject.builder().fromTestApp(MinimalSubProject.app("apt.test")).create()

    /**
     * Regression test for http://b/135780031. Test correctness if we configure Java compile task
     * before invoking Variant API.
     */
    @Test
    fun testAddingGenSourcesAfterJavaCompileConfigured() {
        project.buildFile.appendText("\n" +
            """
                File genSrcDir = new File(projectDir, "externally_generated")
                File testSrc = new File(genSrcDir, "test/Data.java")
                testSrc.parentFile.mkdirs()
                testSrc.write("package test;\n public class Data {}")

                android.applicationVariants.all {
                  it.getJavaCompileProvider().get()
                  it.registerExternalAptJavaOutput(project.fileTree(genSrcDir))
                }
            """.trimIndent()
        )

        project.executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).containsClass("Ltest/Data;")
    }
}