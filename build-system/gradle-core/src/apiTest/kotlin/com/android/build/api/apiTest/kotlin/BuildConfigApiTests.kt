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
import com.android.build.gradle.options.BooleanOption
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertNotNull

class BuildConfigApiTests: VariantApiBaseTest(
    TestType.Script
) {

    @Test
    fun addCustomBuildConfigField() {
        given {
            tasksToInvoke.add("compileDebugSources")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            import com.android.build.api.variant.BuildConfigField

            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                onVariants {
                    it.buildConfigFields.put("FloatValue", BuildConfigField("Float", "1f", "Float Value" ))
                    it.buildConfigFields.put("LongValue", BuildConfigField("Long", "1L", "Long Value" ))
                    it.buildConfigFields.put("VariantName", BuildConfigField("String", "\", ${'$'}, {name}\"", "Variant Name" ))
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
                            label.setText("Hello ${'$'}{BuildConfig.FloatValue}")
                            label.setText("Hello ${'$'}{BuildConfig.LongValue}")
                            label.setText("Hello ${'$'}{BuildConfig.VariantName}")
                            setContentView(label)
                        }
                    }
                    """.trimIndent())
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Adding a BuildConfig field in Kotlin

This sample show how to add a field in the BuildConfig class for which the value is known at
configuration time.

The added field is used in the MainActivity.kt file.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            onVariantStats { variant ->
                val variantPropertiesAccessList = variant.variantApiAccess.variantPropertiesAccessList
                Truth.assertThat(variantPropertiesAccessList.size).isAtLeast(3)
                // make sure our minSdkVersion reset has been recorded.
                variantPropertiesAccessList.forEach {
                    Truth.assertThat(it.type).isEqualTo(
                        VariantPropertiesMethodType.BUILD_CONFIG_FIELDS_VALUE)
                }
            }
        }
    }

    @Test
    fun addCustomFieldWithValueFromTask() {
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
            import com.android.build.api.variant.BuildConfigField

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
                onVariants {
                    it.buildConfigFields.put("GitVersion", gitVersionProvider.map {  task ->
                        BuildConfigField(
                            "String",
                            "\"{task.gitVersionOutputFile.get().asFile.readText(Charsets.UTF_8)}\"",
                            "Git Version")
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
                    label.setText("Hello ${'$'}{BuildConfig.GitVersion}")
                    setContentView(label)
                }
            }
            """.trimIndent())
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Adding a BuildConfig field in Kotlin

This sample show how to add a field in the BuildConfig class for which the value is not known at
configuration time.

The added field is used in the MainActivity.kt file.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            onVariantStats { variant ->
                val variantPropertiesAccessList = variant.variantApiAccess.variantPropertiesAccessList
                Truth.assertThat(variantPropertiesAccessList.size).isAtLeast(1)
                // make sure our minSdkVersion reset has been recorded.
                variantPropertiesAccessList.forEach {
                    Truth.assertThat(it.type).isEqualTo(
                        VariantPropertiesMethodType.BUILD_CONFIG_FIELDS_VALUE)
                }
            }
        }
    }
}
