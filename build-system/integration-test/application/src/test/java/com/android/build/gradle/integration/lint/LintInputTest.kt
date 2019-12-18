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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Regression test for b/141126614 */
class LintInputTest {
    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.lib("com.example")
                    .appendToBuild("""
                        dependencies {
                            implementation 'junit:junit:4.12'
                        }

                        abstract class UseLintInputsTask extends DefaultTask {
                            @InputFiles
                            abstract ConfigurableFileCollection getInputFiles()

                            @OutputFile
                            abstract RegularFileProperty getOutputFile()

                            @TaskAction
                            void writeFile() {
                                outputFile.get().asFile.text = inputFiles.files.toString()
                            }
                        }
                        task useLintInputs(type: UseLintInputsTask) {
                            inputFiles.from(project.tasks["lint"].inputs.files)
                            outputFile = layout.buildDirectory.file("lintInputs.txt")
                        }
                        """.trimIndent())
            )
            .create()

    @Test
    fun regressionTest() {
        val result = project.executor()
            .run("useLintInputs")
        assertThat(result.didWorkTasks).contains(":compileDebugJavaWithJavac")
        assertThat(project.buildDir.resolve("lintInputs.txt")).contains("junit-4.12.jar")
    }
}