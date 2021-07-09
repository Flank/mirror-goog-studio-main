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
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.options.BooleanOption
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.apk.analyzer.ApkAnalyzerImpl
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.ArtifactAccess
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
                    kotlin("android.extensions")
            }
            import com.android.build.api.artifact.MultipleArtifact
            ${testingElements.getAllClassesAccessTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                onVariants { variant ->
                    project.tasks.register<GetAllClassesTask>("${'$'}{variant.name}GetAllClasses") {
                        allClasses.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS))
                        allJarsWithClasses.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS))
                    }
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
# artifacts.getAll in Kotlin
This sample show how to obtain all the classes that will be used to create the dex files.
There are two lists that need to be used to obtain the complete set of classes because some
classes are present as .class files in directories and others are present in jar files.
Therefore, you must query both [ListProperty] of [Directory] and [RegularFile] to get the full list.

The [onVariants] block will wire the [GetAllClassesTask] input properties (allClasses and allJarsWithClasses)
by using the [Artifacts.getAll] method with the right [MultipleArtifact].
`allClasses.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS))`
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
                    Truth.assertThat(it.variantApiAccess.artifactAccessList).hasSize(2)
                    it.variantApiAccess.artifactAccessList.forEach { artifactAccess ->
                        Truth.assertThat(artifactAccess.type).isEqualTo(
                            ArtifactAccess.AccessType.GET_ALL
                        )
                    }
                }
            }
        }
    }

    @Test
    fun modifyAllClasses() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleDebug"))
            addClasspath("org.javassist:javassist:3.22.0-GA")
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
            import com.android.build.api.artifact.MultipleArtifact

            import org.gradle.api.DefaultTask
            import org.gradle.api.file.Directory
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction
            import javassist.ClassPool
            import javassist.CtClass
            import java.io.FileInputStream

            abstract class ModifyClassesTask: DefaultTask() {

                @get:InputFiles
                abstract val allClasses: ListProperty<Directory>

                @get:OutputFiles
                abstract val output: DirectoryProperty

                @TaskAction
                fun taskAction() {

                    val pool = ClassPool(ClassPool.getDefault())

                    allClasses.get().forEach { directory ->
                        println("Directory : ${'$'}{directory.asFile.absolutePath}")
                        directory.asFile.walk().filter(File::isFile).forEach { file ->
                            if (file.name == "SomeSource.class") {
                                println("File : ${'$'}{file.absolutePath}")
                                val interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                                println("Adding ${'$'}interfaceClass")
                                interfaceClass.writeFile(output.get().asFile.absolutePath)
                                FileInputStream(file).use {
                                    val ctClass = pool.makeClass(it);
                                    ctClass.addInterface(interfaceClass)
                                    val m = ctClass.getDeclaredMethod("toString");
                                    if (m != null) {
                                        m.insertBefore("{ System.out.println(\"Some Extensive Tracing\"); }");
                                    }
                                    ctClass.writeFile(output.get().asFile.absolutePath)
                                }
                            }
                        }
                    }
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                onVariants { variant ->
                    val taskProvider = project.tasks.register<ModifyClassesTask>("${'$'}{variant.name}ModifyClasses")
                    variant.artifacts.use<ModifyClassesTask>(taskProvider)
                        .wiredWith(ModifyClassesTask::allClasses, ModifyClassesTask::output)
                        .toTransform(MultipleArtifact.ALL_CLASSES_DIRS)
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
This sample show how to transform all the classes that will be used to create the dex files.
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
                    Truth.assertThat(it.variantApiAccess.artifactAccessList).hasSize(1)
                    it.variantApiAccess.artifactAccessList.forEach { artifactAccess ->
                        Truth.assertThat(artifactAccess.type).isEqualTo(
                            ArtifactAccess.AccessType.TRANSFORM
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

    @Test
    fun addToAllClasses() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleDebug"))
            addClasspath("org.javassist:javassist:3.22.0-GA")
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
            import com.android.build.api.artifact.MultipleArtifact

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
                    variant.artifacts.use<AddClassesTask>(taskProvider)
                        .wiredWith(AddClassesTask::output)
                        .toAppendTo(MultipleArtifact.ALL_CLASSES_DIRS)
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
This sample show how to add new classes to the set that will be used to create the dex files.
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
                    Truth.assertThat(it.variantApiAccess.artifactAccessList).hasSize(1)
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
