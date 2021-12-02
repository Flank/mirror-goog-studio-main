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
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.options.BooleanOption
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.apk.analyzer.ApkAnalyzerImpl
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.ArtifactAccess
import com.google.wireless.android.sdk.stats.VariantPropertiesAccess
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertNotNull

class AddJavaSourceTest: VariantApiBaseTest(TestType.Script) {
    @Test
    fun addJavaSourceFromTask() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugDisplayAllSources", ":app:assembleDebug"))
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
                import org.gradle.api.file.DirectoryProperty
                import org.gradle.api.file.FileTree
                import org.gradle.api.tasks.InputFiles
                import org.gradle.api.tasks.TaskAction
                import org.gradle.api.provider.Property
                import org.gradle.api.tasks.Internal

                abstract class AddJavaSources: DefaultTask() {

                    @get:OutputDirectory
                    abstract val outputFolder: DirectoryProperty

                    @TaskAction
                    fun taskAction() {
                        val outputFile = File(outputFolder.asFile.get(), "com/foo/Bar.java")
                        outputFile.parentFile.mkdirs()
                        outputFile.writeText(""${'"'}
                        package com.foo;

                        public class Bar {
                            public String toString() {
                                return "a Bar instance";
                            }
                        }
                        ""${'"'})
                    }
                }

                abstract class DisplayAllSources: DefaultTask() {

                    @get:InputFiles
                    abstract val sourceFolders: ListProperty<Directory>

                    @TaskAction
                    fun taskAction() {

                        sourceFolders.get().forEach { directory ->
                            println(">>> Got a Directory ${ '$' }directory")
                            println("<<<")
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
                        val addSourceTaskProvider =  project.tasks.register<AddJavaSources>("${ '$' }{variant.name}AddSources") {
                            outputFolder.set(project.layout.buildDirectory.dir("gen"))
                        }

                        variant.sources.java.add(addSourceTaskProvider, AddJavaSources::outputFolder)

                        project.tasks.register<DisplayAllSources>("${ '$' }{variant.name}DisplayAllSources") {
                            sourceFolders.set(variant.sources.java.all)
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
# Sources.java.getAll in Kotlin
This sample show how to obtain the java sources and add a [Directory] to the list of java sources
that will be used for compilation.

To access all java sources, you just need to use
`sourceFolders.set(variant.sources.java.all`
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
            Truth.assertThat(output).contains("addJavaSourceFromTask/app/build/gen")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                if (it.isDebug) {
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(6)
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList.map(VariantPropertiesAccess::getType))
                        .containsExactly(
                            VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_JAVA_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_DIRECTORIES_GET_ALL_VALUE,
                            VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_JAVA_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_DIRECTORIES_ADD_VALUE,
                        )
                }
            }

            val outFolder = File(testProjectDir.root, "${testName.methodName}/app/build/outputs/apk/debug/")
            Truth.assertThat(outFolder.listFiles()?.asList()?.map { it.name }).containsExactly(
                "app-debug.apk", BuiltArtifactsImpl.METADATA_FILE_NAME
            )
            // check that resulting APK contains the newly added interface
            val apk = File(outFolder, "app-debug.apk").toPath()
            val byteArrayOutputStream = object : ByteArrayOutputStream() {
                @Synchronized
                override fun toString(): String =
                    super.toString().replace(System.getProperty("line.separator"), "\n")
            }
            val ps = PrintStream(byteArrayOutputStream)
            val apkAnalyzer = ApkAnalyzerImpl(ps, Mockito.mock(AaptInvoker::class.java))
            apkAnalyzer.dexCode(apk, "com.foo.Bar", null, null, null)
            Truth.assertThat(byteArrayOutputStream.toString()).contains(".class public Lcom/foo/Bar;")

        }
    }

    @Test
    fun addJavaSource() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugDisplayAllSources", ":app:assembleDebug"))
            addModule(":app") {

                addDirectory("custom/src/java/debug/com/foo") {
                    File(this, "Bar.java").writeText("""
                        package com.foo;

                        public class Bar {
                            public String toString() {
                                return "a debug Bar instance";
                            }
                        }
                    """.trimIndent())
                }
                addDirectory("custom/src/java/release/com/foo") {
                    File(this, "Bar.java").writeText("""
                        package com.foo;

                        public class Bar {
                            public String toString() {
                                return "a release Bar instance";
                            }
                        }
                    """.trimIndent())
                }
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
                import org.gradle.api.file.FileTree
                import org.gradle.api.tasks.InputFiles
                import org.gradle.api.tasks.TaskAction


                abstract class DisplayAllSources: DefaultTask() {

                    @get:InputFiles
                    abstract val sourceFolders: ListProperty<Directory>

                    @TaskAction
                    fun taskAction() {

                        sourceFolders.get().forEach { directory ->
                            println(">>> Got a Directory ${ '$' }directory")
                            println("<<<")
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
                        variant.sources.java.addSrcDir("custom/src/java/${'$'}{variant.name}")

                        project.tasks.register<DisplayAllSources>("${ '$' }{variant.name}DisplayAllSources") {
                            sourceFolders.set(variant.sources.java.all)
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
# artifacts.get in Kotlin
This sample show how to obtain the java sources and add a [Directory] to the list of java sources
that will be used for compilation.

To access all java sources, you just need to use
`sourceFolders.set(variant.sources.java.all`
which can be used as [Task] input directly.

To add a folder and its content to the list of folders used for compilation, you need
to use the [SourceDirectories.srcDir] family of methods

## To Run
./gradlew :app:debugDisplayAllSources

            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("addJavaSource/app/custom/src/java/debug")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                if (it.isDebug) {
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(6)
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList.map(VariantPropertiesAccess::getType))
                        .containsExactly(
                            VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_JAVA_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_DIRECTORIES_GET_ALL_VALUE,
                            VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_JAVA_ACCESS_VALUE,
                            VariantPropertiesMethodType.SOURCES_DIRECTORIES_SRC_DIR_VALUE,
                    )
                }
            }

            val outFolder = File(testProjectDir.root, "${testName.methodName}/app/build/outputs/apk/debug/")
            Truth.assertThat(outFolder.listFiles()?.asList()?.map { it.name }).containsExactly(
                "app-debug.apk", BuiltArtifactsImpl.METADATA_FILE_NAME
            )
            // check that resulting APK contains the newly added interface
            val apk = File(outFolder, "app-debug.apk").toPath()
            val byteArrayOutputStream = object : ByteArrayOutputStream() {
                @Synchronized
                override fun toString(): String =
                    super.toString().replace(System.getProperty("line.separator"), "\n")
            }
            val ps = PrintStream(byteArrayOutputStream)
            val apkAnalyzer = ApkAnalyzerImpl(ps, Mockito.mock(AaptInvoker::class.java))
            apkAnalyzer.dexCode(apk, "com.foo.Bar", null, null, null)
            Truth.assertThat(byteArrayOutputStream.toString()).contains(".class public Lcom/foo/Bar;")

        }
    }
}

