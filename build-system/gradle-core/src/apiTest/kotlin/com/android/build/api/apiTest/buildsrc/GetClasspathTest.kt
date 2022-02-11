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

package com.android.build.api.apiTest.buildsrc

import com.google.common.truth.Truth
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

class GetClasspathTest : BuildSrcScriptApiTest() {

    @Test
    fun compileClasspathTest() {
        given {
            tasksToInvoke.add("debugPrintCompileClasspath")
            addBuildSrc {
                addSource(
                    "src/main/kotlin/PrintClasspathTask.kt",
                    // language=kotlin
                    """
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.file.ConfigurableFileCollection
                    import org.gradle.api.tasks.Classpath
                    import org.gradle.api.tasks.TaskAction

                    abstract class PrintClasspathTask: DefaultTask() {

                        @get:Classpath
                        abstract val classpath: ConfigurableFileCollection

                        @TaskAction
                        fun taskAction() {
                            for (file in classpath.files) {
                                System.out.println(file.absolutePath)
                            }
                        }
                    }
                    """.trimIndent())
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """
                    import com.android.build.api.artifact.MultipleArtifact
                    import com.android.build.api.dsl.ApplicationExtension
                    import com.android.build.api.variant.AndroidComponentsExtension
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project

                    abstract class ExamplePlugin: Plugin<Project> {

                        override fun apply(project: Project) {

                            val androidComponents =
                                project.extensions.getByType(AndroidComponentsExtension::class.java)

                            androidComponents.onVariants { variant ->

                                val taskProvider =
                                    project.tasks.register(
                                        variant.name + "PrintCompileClasspath",
                                        PrintClasspathTask::class.java
                                    ) {
                                        it.classpath.from(variant.compileClasspath)
                                    }
                            }
                        }
                    }
                    """.trimIndent()
                )
            }
            addModule(":app") {
                addCommonBuildFile(this)
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("R.jar")
            Truth.assertThat(output).contains("kotlin-classes${File.separatorChar}debug")
        }
    }
}
