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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class ResValuesApiTests: VariantApiBaseTest(
    TestType.Script
) {
    @Test
    fun addCustomResValueField() {
        given {
            tasksToInvoke.add("compileDebugSources")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            import com.android.build.api.variant.ResValue

            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                onVariants { variant ->
                    variant.resValues.put(variant.makeResValueKey("string", "VariantName"),
                        ResValue(name, "Variant Name"))
                }
            }
                """.trimIndent()
                testingElements.addManifest(this)
                addSource(
                    "src/main/kotlin/com/android/build/example/minimal/MainActivity.kt",
                    //language=kotlin
                    """
                    package com.android.build.example.minimal

                    import android.app.Activity
                    import android.os.Bundle
                    import android.widget.TextView

                    class MainActivity : Activity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            val label = TextView(this)
                            label.setText("Hello ${'$'}{R.string.VariantName}")
                            setContentView(label)
                        }
                    }
                    """.trimIndent())
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Adding a ResValue field in Kotlin

This sample show how to add a Resource value known at configuration time.

The added field is used in the MainActivity.kt file.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun addCustomResValueFromTask() {
        given {
            tasksToInvoke.add("compileDebugSources")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.artifact.SingleArtifact
            import com.android.build.api.variant.ResValue

            ${testingElements.getGitVersionTask()}

            val gitVersionProvider = tasks.register<GitVersionTask>("gitVersionProvider") {
                File(project.buildDir, "intermediates/gitVersionProvider/output").also {
                    it.parentFile.mkdirs()
                    gitVersionOutputFile.set(it)
                }
                outputs.upToDateWhen { false }
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants { variant ->
                    variant.resValues.put(variant.makeResValueKey("string", "GitVersion"),
                        gitVersionProvider.map {  task ->
                            ResValue(task.gitVersionOutputFile.get().asFile.readText(Charsets.UTF_8), "git version")
                        })
                }
            }""".trimIndent()
                testingElements.addManifest(this)
                addSource(
                    "src/main/kotlin/com/android/build/example/minimal/MainActivity.kt",
                    //language=kotlin
                    """
            package com.android.build.example.minimal

            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView

            class MainActivity : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val label = TextView(this)
                    label.setText("Hello ${'$'}{R.string.GitVersion}")
                    setContentView(label)
                }
            }
            """.trimIndent())
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Adding a ResValue field in Kotlin

This sample show how to add a resource value for which the value is not known at
configuration time and will be calculated by a Task.

The added field is used in the MainActivity.kt file.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
