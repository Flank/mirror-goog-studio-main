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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class GetCompileClasspathTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testErrorWhenResolvingDuringConfiguration() {
        TestFileUtils.appendToFile(
            project.buildFile,
            // language=groovy
            """
                abstract class PrintClasspathTask extends DefaultTask {

                    @Classpath
                    abstract ConfigurableFileCollection getClasspath()

                    @TaskAction
                    def taskAction() {
                        for (file in classpath.files) {
                            System.out.println(file.getAbsolutePath())
                        }
                    }
                }

                androidComponents {
                    onVariants(selector().all(), { variant ->
                        project.tasks.register(
                            variant.name + "PrintClasspath",
                            PrintClasspathTask.class
                        ) {
                            // The expected error is caused by trying to resolve the FileCollection
                            // during configuration here.
                            it.classpath.from(variant.compileClasspath.files)
                        }
                    })
                }
            """.trimIndent())
        val result = project.executor().expectFailure().run("debugPrintClasspath")
        ScannerSubject.assertThat(result.stderr).contains(
            "Configuration 'debugCompileClasspath' was resolved during configuration time."
        )
    }
}
