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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.android.build.gradle.options.BooleanOption
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.VariantPropertiesAccess
import org.junit.Test
import kotlin.test.assertNotNull

class AddCustomSourceTest: VariantApiBaseTest(TestType.Script) {

    @Test
    fun addCustomSourceInDslType() {
        given {
            tasksToInvoke.addAll(listOf("sourceSets"))
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

                android {
                    compileSdkVersion(29)
                    defaultConfig {
                        minSdkVersion(21)
                        targetSdkVersion(29)
                    }
                }

                androidComponents {
                    registerSourceType("toml")
                }
                """.trimIndent()
                testingElements.addManifest( this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Add custom source folders in Kotlin
This sample show how to add a new custom source folders to all source sets. The source folder will
not be used by any AGP tasks (since we do no know about it), however, it can be used by plugins and
tasks participating into the Variant API callbacks.

To register the custom sources, you just need to use
`androidComponents { registerSourceType("toml") } `

## To Run
./gradlew sourceSets
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Custom sources: [app/src/android test/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/android test debug/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/android test release/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/debug/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/main/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/release/toml]")
        }
    }

    @Test
    fun addCustomSourceInVariantTest() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugDisplayAllSources"))
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

                abstract class AddCustomSources: DefaultTask() {

                    @get:OutputDirectory
                    abstract val outputFolder: DirectoryProperty

                    @TaskAction
                    fun taskAction() {
                        val outputFile = File(outputFolder.asFile.get(), "com/foo/bar.toml")
                        outputFile.parentFile.mkdirs()
                        outputFile.writeText(""${'"'}
                            [clients]
                            data = [ ["gamma", "delta"], [1, 2] ]
                        ""${'"'})
                    }
                }

                abstract class DisplayAllSources: DefaultTask() {

                    @get:InputFiles
                    abstract val sourceFolders: ListProperty<Directory>

                    @TaskAction
                    fun taskAction() {

                        sourceFolders.get().forEach { directory ->
                            println("--> Got a Directory ${ '$' }directory")
                            println("<-- done")
                        }
                    }
                }

                android {
                    compileSdkVersion(29)
                    defaultConfig {
                        minSdkVersion(21)
                        targetSdkVersion(29)
                    }
                }

                androidComponents {
                    onVariants { variant ->
                        val addSourceTaskProvider =  project.tasks.register<AddCustomSources>("${ '$' }{variant.name}AddCustomSources") {
                            outputFolder.set(File(project.layout.buildDirectory.asFile.get(), "toml/gen"))
                        }

                        variant.sources.getByName("toml").also {
                                it.addSrcDir("src/${'$'}{variant.name}/toml")
                                it.add(addSourceTaskProvider, AddCustomSources::outputFolder)
                        }
                        println(variant.sources.getByName("toml"))

                        project.tasks.register<DisplayAllSources>("${ '$' }{variant.name}DisplayAllSources") {
                            sourceFolders.set(variant.sources.getByName("toml").all)
                        }
                    }
                }
                """.trimIndent()
                testingElements.addManifest( this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Add custom source folders in Kotlin
This sample show how to add a new custom source folders to the Variant. Note the sources will not
be added to the DSL and therefore to the usual src/ locations. The source folder will
not be used by any AGP tasks (since we do not know about it), however, it can be used by plugins and
tasks participating into the Variant API callbacks.

To access the custome sources, you just need to use
`sourceFolders.set(variant.sources.getByName("toml").getAll()`
which can be used as [Task] input directly.

To add a folder which content will be  a execution time by a [Task] execution, you need
to use the [SourceDirectories.add] method providing a [TaskProvider] and the pointer to the output folder
where source files will be generated and added to the compilation task.

## To Run
./gradlew :app:debugDisplayAllSources
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("addCustomSourceInVariantTest/app/src/debug/toml")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                super.onVariantStats {
                    if (it.isDebug) {
                        Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(6)
                        Truth.assertThat(
                            it.variantApiAccess.variantPropertiesAccessList.map(
                                VariantPropertiesAccess::getType
                            )
                        )
                            .containsExactly(
                                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                                VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE,
                                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                                VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE,
                                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                                VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE,
                            )
                    }
                }
            }
        }
    }

    @Test
    fun addCustomSourceTypeInDslAndVariantTest() {
        given {
            tasksToInvoke.addAll(listOf("clean", "sourceSets", ":app:debugDisplayAllSources"))
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

                abstract class AddCustomSources: DefaultTask() {

                    @get:OutputDirectory
                    abstract val outputFolder: DirectoryProperty

                    @TaskAction
                    fun taskAction() {
                        val outputFile = File(outputFolder.asFile.get(), "com/foo/bar.toml")
                        outputFile.parentFile.mkdirs()
                        outputFile.writeText(""${'"'}
                            [clients]
                            data = [ ["gamma", "delta"], [1, 2] ]
                        ""${'"'})
                    }
                }

                abstract class DisplayAllSources: DefaultTask() {

                    @get:InputFiles
                    abstract val sourceFolders: ListProperty<Directory>

                    @TaskAction
                    fun taskAction() {

                        sourceFolders.get().forEach { directory ->
                            println("--> Got a Directory ${ '$' }directory")
                            println("<-- done")
                        }
                    }
                }

                android {
                    compileSdkVersion(29)
                    defaultConfig {
                        minSdkVersion(21)
                    }
                }

                androidComponents {
                    registerSourceType("toml")
                    onVariants { variant ->
                        val addSourceTaskProvider =  project.tasks.register<AddCustomSources>("${ '$' }{variant.name}AddCustomSources") {
                            outputFolder.set(File(project.layout.buildDirectory.asFile.get(), "toml/gen"))
                        }

                        variant.sources.getByName("toml").also {
                                it.addSrcDir("third_party/${'$'}{variant.name}/toml")
                                it.add(addSourceTaskProvider, AddCustomSources::outputFolder)
                        }
                        println(variant.sources.getByName("toml"))

                        project.tasks.register<DisplayAllSources>("${ '$' }{variant.name}DisplayAllSources") {
                            sourceFolders.set(variant.sources.getByName("toml").all)
                        }
                    }
                }
                """.trimIndent()
                testingElements.addManifest( this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Add custom source folders in Kotlin
This sample show how to add a new custom source folders to the Variant. The source folder will
not be used by any AGP tasks, however, it can be used by plugins and tasks participating into the
Variant API callbacks.

To access the custome sources, you just need to use
`sourceFolders.set(variant.sources.getByName("toml").all`
which can be used as [Task] input directly.

To add a folder which content will be  a execution time by a [Task] execution, you need
to use the [SourceDirectories.add] method providing a [TaskProvider] and the pointer to the output folder
where source files will be generated and added to the compilation task.

## To Run
./gradlew :app:debugDisplayAllSources
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Custom sources: [app/src/android test/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/android test debug/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/android test release/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/debug/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/main/toml]")
            Truth.assertThat(output).contains("Custom sources: [app/src/release/toml]")
            Truth.assertThat(output).contains("addCustomSourceTypeInDslAndVariantTest/app/src/main/toml")
            Truth.assertThat(output).contains("addCustomSourceTypeInDslAndVariantTest/app/src/debug/toml")
            Truth.assertThat(output).contains("addCustomSourceTypeInDslAndVariantTest/app/third_party/debug/toml")
            Truth.assertThat(output).contains("addCustomSourceTypeInDslAndVariantTest/app/build/toml/gen")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                super.onVariantStats {
                    if (it.isDebug) {
                        Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(6)
                        Truth.assertThat(
                            it.variantApiAccess.variantPropertiesAccessList.map(
                                VariantPropertiesAccess::getType
                            )
                        )
                            .containsExactly(
                                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                                VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE,
                                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                                VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE,
                                VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                                VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE,
                            )
                    }
                }
            }
        }
    }

    @Test
    fun addCustomSourceTypeRequiringMergeTest() {
        given {
            tasksToInvoke.addAll(listOf("debugConsumeMergedToml"))
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

                    android {
                        compileSdkVersion(29)
                        defaultConfig {
                            minSdkVersion(21)
                        }
                    }


                    abstract class MergeTomlSources: DefaultTask() {

                        @get:InputFiles
                        abstract val sourceFolders: ListProperty<Directory>

                        @get:OutputDirectory
                        abstract val mergedFolder: DirectoryProperty

                        @TaskAction
                        fun taskAction() {

                            sourceFolders.get().forEach { directory ->
                                println("--> Got a Directory ${'$'}directory")
                                directory.asFile.walk().forEach { sourceFile ->
                                    println("Source: " + sourceFile.absolutePath)
                                }
                                println("<-- done")
                            }
                        }
                    }

                    abstract class ConsumeMergedToml: DefaultTask() {

                        @get:InputDirectory
                        abstract val mergedFolder: DirectoryProperty

                        @TaskAction
                        fun taskAction() {

                            println("Merged folder is " + mergedFolder.get().asFile)
                        }
                    }


                    androidComponents {
                        registerSourceType("toml")
                        onVariants { variant ->

                            val outFolder = project.layout.buildDirectory.dir("intermediates/${'$'}{variant.name}/merged_toml")
                            val mergingTask = project.tasks.register<MergeTomlSources>("${'$'}{variant.name}MergeTomlSources") {
                                sourceFolders.set(variant.sources.getByName("toml").all)
                                mergedFolder.set(outFolder)
                            }


                            val consumingTask = project.tasks.register<ConsumeMergedToml>("${'$'}{variant.name}ConsumeMergedToml") {
                                mergedFolder.set(mergingTask.flatMap { it.mergedFolder })
                            }
                        }
                    }
                """.trimIndent()
                testingElements.addManifest( this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Add custom source folders in Kotlin
This sample show how to add a new custom source folders to all source sets. The source folder will
by any AGP tasks (since we do no know about it), however, it can be used by plugins and
tasks participating into the Variant API callbacks.

In this example, it is assumed that a merging activity has to happen before the source folders can
be used to be added to an AGP artifact (like ASSETS for example).

To register the custom sources, you just need to use
`androidComponents { registerSourceType("toml") } `

The merging activity is implemented by the :app:debugMergeTomlSources and the downstream task that
 uses the merged folder is :app:debugConsumeMergedToml

## To Run
./gradlew sourceSets
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Merged folder is")
        }
    }
}

