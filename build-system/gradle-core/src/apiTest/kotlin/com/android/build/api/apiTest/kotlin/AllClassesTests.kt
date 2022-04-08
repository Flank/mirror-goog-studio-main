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
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.ArtifactAccess
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.VariantPropertiesAccess
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertNotNull

class AllClassesTests: VariantApiBaseTest(TestType.Script) {

    @Test
    fun getAllClassesTest() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugGetAllClasses"))
            addModule(":app") {
                addSource("src/main/java/com/android/api/tests/SomeSource.kt", """
                    package com.android.api.tests

                    class SomeSource {
                        fun doSomething() {
                            println("Something !")
                        }
                    }
                """.trimIndent())
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
            }
            import com.android.build.api.variant.ScopedArtifacts
            import com.android.build.api.artifact.ScopedArtifact
            ${testingElements.getAllClassesAccessTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                onVariants { variant ->
                    val taskProvider = project.tasks.register<GetAllClassesTask>("${'$'}{variant.name}GetAllClasses")
                    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES,
                            GetAllClassesTask::allJars,
                            GetAllClassesTask::allDirectories
                        )
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Scoped Artifacts toGet example in Kotlin
This sample shows how to obtain all the classes that will be used to create the dex files.
There are two lists that need to be used to obtain the complete set of classes because some
classes are present as .class files in directories and others are present in jar files.
Therefore, you must query both [ListProperty] of [Directory] and [RegularFile] to get the full list.

The [onVariants] block will wire the [GetAllClassesTask] input properties (allClasses and allJarsWithClasses)
by using the [ScopedArtifactsOperation.toGet] method with the right [ScopedArtifact].
`
    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
        .use(taskProvider)
        .toGet(
            ScopedArtifact.CLASSES,
            GetAllClassesTask::allJarsWithClasses,
            GetAllClassesTask::allClasses
        )
`
## To Run
./gradlew debugGetAllClasses
expected result : a list of classes and jar files.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Directory : ")
            Truth.assertThat(output).contains("SomeSource.class")
            Truth.assertThat(output).contains("JarFile")
            Truth.assertThat(output).contains("R.jar")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                if (it.isDebug) {
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(3)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(0).type)
                        .isEqualTo(VariantPropertiesMethodType.ARTIFACTS_VALUE)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(1).type)
                        .isEqualTo(VariantPropertiesMethodType.FOR_SCOPE_VALUE)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(2).type)
                        .isEqualTo(VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_GET_VALUE)
                }
            }
        }
    }

    @Test
    fun modifyProjectClasses() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleDebug"))
            addClasspath("org.javassist:javassist:3.26.0-GA")
            addModule(":app") {
                addSource("src/main/java/com/android/api/tests/SomeSource.kt", """
                    package com.android.api.tests

                    class SomeSource {
                        override fun toString() = "Something !"
                    }
                """.trimIndent())
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
           }
           import com.android.build.api.variant.ScopedArtifacts
           import com.android.build.api.artifact.ScopedArtifact

           import org.gradle.api.DefaultTask
           import org.gradle.api.file.Directory
           import org.gradle.api.provider.ListProperty
           import org.gradle.api.tasks.InputFiles
           import org.gradle.api.tasks.TaskAction
           import javassist.ClassPool
           import javassist.CtClass
           import java.io.FileInputStream
           import java.io.FileOutputStream
           import java.io.BufferedOutputStream
           import java.io.File
           import java.util.jar.JarFile
           import java.util.jar.JarEntry
           import java.util.jar.JarOutputStream

           abstract class ModifyClassesTask: DefaultTask() {

               @get:InputFiles
               abstract val allJars: ListProperty<RegularFile>

               @get:InputFiles
               abstract val allDirectories: ListProperty<Directory>

               @get:OutputFile
               abstract val output: RegularFileProperty

               @TaskAction
               fun taskAction() {

                   val pool = ClassPool(ClassPool.getDefault())

                   val jarOutput = JarOutputStream(BufferedOutputStream(FileOutputStream(
                        output.get().asFile
                   )))
                   allJars.get().forEach { file ->
                       println("handling " + file.asFile.getAbsolutePath())
                       val jarFile = JarFile(file.asFile)
                       jarFile.entries().iterator().forEach { jarEntry ->
                            println("Adding from jar ${'$'}{jarEntry.name}")
                            jarOutput.putNextEntry(JarEntry(jarEntry.name))
                            jarFile.getInputStream(jarEntry).use {
                                it.copyTo(jarOutput)
                            }
                            jarOutput.closeEntry()
                       }
                       jarFile.close()
                   }
                   allDirectories.get().forEach { directory ->
                        println("handling " + directory.asFile.getAbsolutePath())
                        directory.asFile.walk().forEach { file ->
                           if (file.isFile) {
                                if (file.name.endsWith("SomeSource.class")) {
                                    println("Found ${'$'}file.name")
                                    val interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                                    println("Adding ${'$'}interfaceClass")
                                    jarOutput.putNextEntry(JarEntry("com/android/api/tests/SomeInterface.class"))
                                    jarOutput.write(interfaceClass.toBytecode())
                                    jarOutput.closeEntry()
                                    val ctClass = file.inputStream().use {
                                        pool.makeClass(it);
                                    }
                                    ctClass.addInterface(interfaceClass)

                                    val m = ctClass.getDeclaredMethod("toString");
                                    if (m != null) {
                                        m.insertBefore("{ System.out.println(\"Some Extensive Tracing\"); }");

                                    val relativePath = directory.asFile.toURI().relativize(file.toURI()).getPath()
                                    jarOutput.putNextEntry(JarEntry(relativePath.replace(File.separatorChar, '/')))
                                    jarOutput.write(ctClass.toBytecode())
                                    jarOutput.closeEntry()
                               } else {
                                    val relativePath = directory.asFile.toURI().relativize(file.toURI()).getPath()
                                    println("Adding from directory ${'$'}{relativePath.replace(File.separatorChar, '/')}")
                                    jarOutput.putNextEntry(JarEntry(relativePath.replace(File.separatorChar, '/')))
                                    file.inputStream().use { inputStream ->
                                        inputStream.copyTo(jarOutput)
                                    }
                                    jarOutput.closeEntry()
                               }
                           }
                        }
                     }
                   }
                   jarOutput.close()
               }
           }
           android {
               namespace = "com.android.build.example.minimal"
               compileSdkVersion(29)
               defaultConfig {
                   minSdkVersion(21)
               }
           }

           androidComponents {
               onVariants { variant ->
                   val taskProvider = project.tasks.register<ModifyClassesTask>("${'$'}{variant.name}ModifyClasses")
                    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toTransform(
                            ScopedArtifact.CLASSES,
                            ModifyClassesTask::allJars,
                            ModifyClassesTask::allDirectories,
                            ModifyClassesTask::output
                        )
               }
           }
        """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# artifacts.transform MultipleArtifact in Kotlin
This sample shows how to transform all the classes that will be used to create the dex files.
There are two lists that need to be used to obtain the complete set of classes because some
classes are present as .class files in directories and others are present in jar files.
Therefore, you must query both [ListProperty] of [Directory] and [RegularFile] to get the full list.

In this example, we only query the [ListProperty] of [Directory] to invoke some bytecode
instrumentation on classes.

The Variant API provides a convenient API to transform bytecodes based on ASM but this example
is using javassist to show how this can be done using a different bytecode enhancer.


The [onVariants] block will wire the [ModifyClassesTask] input properties (allClasses]
to the [output] folder
`wiredWith(ModifyClassesTask::allClasses, ModifyClassesTask::output)`
to transform [MultipleArtifact.ALL_CLASSES_DIRS]

## To Run
./gradlew :app:assembleDebug
expected result : a list of classes and jar files.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("SomeSource.class")
            Truth.assertThat(output).contains("interface class com.android.api.tests.SomeInterface")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                if (it.isDebug) {
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(3)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(0).type)
                        .isEqualTo(VariantPropertiesMethodType.ARTIFACTS_VALUE)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(1).type)
                        .isEqualTo(VariantPropertiesMethodType.FOR_SCOPE_VALUE)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(2).type)
                        .isEqualTo(VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_TRANSFORM_VALUE)
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
            apkAnalyzer.dexCode(apk, "com.android.api.tests.SomeInterface", null, null, null)
            Truth.assertThat(byteArrayOutputStream.toString()).contains("SomeInterface")
        }
    }

    @Test
    fun replaceProjectClasses() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleDebug"))
            addClasspath("org.javassist:javassist:3.26.0-GA")
            addModule(":app") {
                addSource("src/main/java/com/android/api/tests/SomeSource.kt", """
                    package com.android.api.tests

                    class SomeSource {
                        override fun toString() = "Something !"
                    }
                """.trimIndent())
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
           }
           import com.android.build.api.variant.ScopedArtifacts
           import com.android.build.api.artifact.ScopedArtifact

           import org.gradle.api.DefaultTask
           import org.gradle.api.file.Directory
           import org.gradle.api.provider.ListProperty
           import org.gradle.api.tasks.InputFiles
           import org.gradle.api.tasks.TaskAction
           import javassist.ClassPool
           import javassist.CtClass
           import java.io.FileInputStream
           import java.io.FileOutputStream
           import java.io.BufferedOutputStream
           import java.io.File
           import java.util.jar.JarFile
           import java.util.jar.JarEntry
           import java.util.jar.JarOutputStream

           abstract class ReplaceClassesTask: DefaultTask() {

               @get:OutputFile
               abstract val output: RegularFileProperty

               @TaskAction
               fun taskAction() {

                   val pool = ClassPool(ClassPool.getDefault())

                   JarOutputStream(BufferedOutputStream(FileOutputStream(
                        output.get().asFile
                   ))).use  {
                        val interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                        println("Adding ${'$'}interfaceClass")
                        it.putNextEntry(JarEntry("com/android/api/tests/SomeInterface.class"))
                        it.write(interfaceClass.toBytecode())
                        it.closeEntry()
                   }
               }
           }
           android {
               namespace = "com.android.build.example.minimal"
               compileSdkVersion(29)
               defaultConfig {
                   minSdkVersion(21)
               }
           }

           androidComponents {
               onVariants { variant ->
                   val taskProvider = project.tasks.register<ReplaceClassesTask>("${'$'}{variant.name}ModifyClasses")
                    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toReplace(
                            ScopedArtifact.CLASSES,
                            ReplaceClassesTask::output
                        )
               }
           }
        """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Scoped Artifacts toReplace Project classes in Kotlin
This sample shows how to replace all the project classes that will be used to create the dex files.

The [onVariants] block will wire [ReplaceClassesTask]'s [output] folder to contain all the new
project classes :
`
    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
        .use(taskProvider)
        .toReplace(
            ScopedArtifact.CLASSES,
            ModifyClassesTask::output
        )
`

## To Run
./gradlew :app:assembleDebug
expected result : a list of classes and jar files.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("interface class com.android.api.tests.SomeInterface")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                if (it.isDebug) {
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(3)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(0).type)
                        .isEqualTo(VariantPropertiesMethodType.ARTIFACTS_VALUE)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(1).type)
                        .isEqualTo(VariantPropertiesMethodType.FOR_SCOPE_VALUE)
                    Truth.assertThat(it.variantApiAccess.getVariantPropertiesAccess(2).type)
                        .isEqualTo(VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_REPLACE_VALUE)
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
            apkAnalyzer.dexCode(apk, "com.android.api.tests.SomeInterface", null, null, null)
            Truth.assertThat(byteArrayOutputStream.toString()).contains("SomeInterface")
            Truth.assertThat(byteArrayOutputStream.toString()).doesNotContain("SourceSource")
        }
    }

    @Test
    fun appendToProjectClasses() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleDebug"))
            addClasspath("org.javassist:javassist:3.26.0-GA")
            addModule(":app") {
                addSource("src/main/java/com/android/api/tests/SomeSource.kt", """
                    package com.android.api.tests

                    class SomeSource {
                        override fun toString() = "Something !"
                    }
                """.trimIndent())
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import com.android.build.api.variant.ScopedArtifacts
            import com.android.build.api.artifact.ScopedArtifact

            import org.gradle.api.DefaultTask
            import org.gradle.api.file.Directory
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction
            import javassist.ClassPool
            import javassist.CtClass
            import java.io.FileInputStream

            abstract class AddClassesTask: DefaultTask() {

                @get:OutputFiles
                abstract val output: DirectoryProperty

                @TaskAction
                fun taskAction() {

                    val pool = ClassPool(ClassPool.getDefault())


                    val interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                    println("Adding ${'$'}interfaceClass")
                    interfaceClass.writeFile(output.get().asFile.absolutePath)
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                onVariants { variant ->
                    val taskProvider = project.tasks.register<AddClassesTask>("${'$'}{variant.name}AddClasses")
                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toAppend(
                            ScopedArtifact.CLASSES,
                            AddClassesTask::output
                        )
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# artifacts.transform MultipleArtifact in Kotlin
This sample shows how to add new classes to the set that will be used to create the dex files.
There are two lists that need to be used to obtain the complete set of classes because some
classes are present as .class files in directories and others are present in jar files.
Therefore, you must query both [ListProperty] of [Directory] and [RegularFile] to get the full list.

In this example, we only add classes the [ListProperty] of [Directory].

The [onVariants] block will wire the [AddClassesTask] [output] folder using
`wiredWith(AddClassesTask::output)`
to add classes to [MultipleArtifact.ALL_CLASSES_DIRS]

## To Run
./gradlew :app:assembleDebug
expected result : an APK with added types in its dex files.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("interface class com.android.api.tests.SomeInterface")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                if (it.isDebug) {
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList).hasSize(3)
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList
                        .map(VariantPropertiesAccess::getType))
                        .containsExactly(
                            VariantPropertiesMethodType.ARTIFACTS_VALUE,
                            VariantPropertiesMethodType.FOR_SCOPE_VALUE,
                            VariantPropertiesMethodType.SCOPED_ARTIFACTS_APPEND_VALUE,
                        )

                    it.variantApiAccess.artifactAccessList.forEach { artifactAccess ->
                        Truth.assertThat(artifactAccess.type).isEqualTo(
                            ArtifactAccess.AccessType.APPEND
                        )
                    }
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
            apkAnalyzer.dexCode(apk, "com.android.api.tests.SomeInterface", null, null, null)
            Truth.assertThat(byteArrayOutputStream.toString()).contains("SomeInterface")
        }
    }
}
