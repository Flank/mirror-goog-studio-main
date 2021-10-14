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
import kotlin.test.assertNotNull

class ExtendingAgpTest : BuildSrcScriptApiTest() {

    @Test
    fun toyPluginAds2021() {
        given {
            tasksToInvoke.add("assembleDebug")
            addBuildSrc {
                addSource(
                    "src/main/kotlin/AddAssetTask.kt",
                    // language=kotlin
                    """
                    import java.io.File
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.provider.Property
                    import org.gradle.api.file.DirectoryProperty
                    import org.gradle.api.tasks.Input
                    import org.gradle.api.tasks.OutputDirectory
                    import org.gradle.api.tasks.TaskAction

                    abstract class AddAssetTask: DefaultTask() {

                        @get:Input
                        abstract val content: Property<String>

                        @get:OutputDirectory
                        abstract val outputDir: DirectoryProperty

                        @TaskAction
                        fun taskAction() {
                            File(outputDir.asFile.get(), "extra.txt").writeText(content.get())
                            System.out.println(
                                "Asset added with content : \"${'$'}{content.get()}\""
                            )
                        }
                    }
                    """.trimIndent())
                addSource(
                    "src/main/kotlin/ToyVariantExtension.kt",
                    // language=kotlin
                    """
                    /**
                    * Simple Variant scoped extension interface that will be attached to the AGP
                    * variant object.
                    */
                    import org.gradle.api.provider.Property

                    interface ToyVariantExtension {
                        /**
                         * content is declared a Property<> so other plugins can declare a task
                         * providing this value that will then be determined at execution time.
                         */
                        val content: Property<String>
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ToyExtension.kt",
                    // language=kotlin
                    """
                    /**
                    * Simple DSL extension interface that will be attached to the android build type
                    * DSL element.
                    */
                    interface ToyExtension {
                        var content: String?
                    }
                    """.trimIndent()
                )
                addSource(
                    "src/main/kotlin/ToyPlugin.kt",
                    // language=kotlin
                    """
                    import com.android.build.api.artifact.MultipleArtifact
                    import com.android.build.api.dsl.ApplicationExtension
                    import com.android.build.api.variant.AndroidComponentsExtension
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project

                    abstract class ToyPlugin: Plugin<Project> {

                        override fun apply(project: Project) {

                            val android = project.extensions.getByType(ApplicationExtension::class.java)

                            android.buildTypes.forEach {
                                it.extensions.add("toy", ToyExtension::class.java)
                            }

                            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

                            androidComponents.beforeVariants { variantBuilder ->
                                val buildType = android.buildTypes.getByName(variantBuilder.buildType)
                                val toyExtension = buildType.extensions.findByName("toy") as ToyExtension

                                val variantExtension = project.objects.newInstance(ToyVariantExtension::class.java)
                                variantExtension.content.set(toyExtension?.content ?: "foo")
                                variantBuilder.registerExtension(ToyVariantExtension::class.java, variantExtension)
                            }

                            androidComponents.onVariants { variant ->
                                val content = variant.getExtension(ToyVariantExtension::class.java)?.content

                                val taskProvider =
                                    project.tasks.register(variant.name + "AddAsset", AddAssetTask::class.java) { it.content.set(content) }

                                variant.artifacts
                                    .use(taskProvider)
                                    .wiredWith(AddAssetTask::outputDir)
                                    .toAppendTo(MultipleArtifact.ASSETS)
                            }
                        }
                    }
                    """.trimIndent()
                )
            }
            addModule(":app") {
                buildFile =
                """
                plugins {
                        id("com.android.application")
                        kotlin("android")
                }

                apply<ToyPlugin>()

                android { ${testingElements.addCommonAndroidBuildLogic()}
                }

                androidComponents {
                    onVariants { variant ->
                        variant.getExtension(ToyVariantExtension::class.java)?.content?.set("Hello World")
                    }
                }
                """.trimIndent()
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        withDocs {
            index =
                """
                This recipe was written for the "Getting the most from the latest Android Gradle
                plugin" talk at ADS 2021.

                This recipe is an example of (1) modifying an AGP intermediate artifact, (2) adding
                a custom element to AGP's DSL, and (3) adding a custom property to AGP's variant
                API.

                In this recipe, the custom AddAssetTask writes a file which is packaged in the
                downstream APK. ToyExtension and ToyVariantExtension are extensions on the DSL and
                Variant API, respectively, which allow the content of the extra asset to be set by
                the user or by another Gradle plugin.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output)
                .contains("Asset added with content : \"Hello World\"")
        }
    }
}
