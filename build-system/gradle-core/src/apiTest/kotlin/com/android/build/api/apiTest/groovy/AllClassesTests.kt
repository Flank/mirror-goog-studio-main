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

package com.android.build.api.apiTest.groovy

import com.android.build.api.apiTest.VariantApiBaseTest
import com.android.build.api.variant.impl.BuiltArtifactsImpl
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

class AllClassesTests: VariantApiBaseTest(TestType.Script, ScriptingLanguage.Groovy) {

    @Test
    fun getAllClassesTest() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugGetAllClasses"))
            addModule(":app") {
                addSource("src/main/java/com/android/api/tests/SomeSource.java", """
                    package com.android.api.tests;

                    class SomeSource {
                        void doSomething() {
                            System.out.println("Something !");
                        }
                    }
                """.trimIndent())
                buildFile = """
            plugins {
                id 'com.android.application'
            }

            ${testingElements.getAllClassesAccessTask()}

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            import com.android.build.api.artifact.MultipleArtifact
            androidComponents {
                onVariants(selector().all(), { variant ->
                    project.tasks.register(variant.getName() + "GetAllClasses", GetAllClassesTask.class) {
                        it.allClasses.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE))
                        it.allJarsWithClasses.set(variant.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS.INSTANCE))

                    }
                })
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
# artifacts.getAll in Groovy
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
                addSource("src/main/java/com/android/api/tests/SomeSource.java", """
                    package com.android.api.tests;

                    class SomeSource {
                        public String toString() {
                            return "Something !";
                        }
                    }
                """.trimIndent())
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=java
                    """
            plugins {
                id 'com.android.application'
            }
            import com.android.build.api.artifact.MultipleArtifact;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.Directory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.tasks.InputFiles;
            import org.gradle.api.tasks.TaskAction;
            import javassist.ClassPool;
            import javassist.CtClass;
            import javassist.CtMethod;
            import java.io.FileInputStream;

            abstract class ModifyClassesTask extends DefaultTask {

                @InputFiles
                abstract ListProperty<Directory> getAllClasses();

                @OutputFiles
                abstract DirectoryProperty getOutput();

                @TaskAction
                void taskAction() {

                    ClassPool pool = new ClassPool(ClassPool.getDefault());

                    allClasses.get().forEach { directory ->
                        System.out.println("Directory : ${'$'}{directory.asFile.absolutePath}");
                        directory.asFile.traverse(type: groovy.io.FileType.FILES) { file ->
                            System.out.println(file.absolutePath);
                            if (file.name == "SomeSource.class") {
                                System.out.println("File : ${'$'}{file.absolutePath}");
                                CtClass interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                                System.out.println("Adding ${'$'}interfaceClass");
                                interfaceClass.writeFile(output.get().asFile.absolutePath);
                                new FileInputStream(file).withCloseable {
                                    CtClass ctClass = pool.makeClass(it);
                                    ctClass.addInterface(interfaceClass);
                                    CtMethod m = ctClass.getDeclaredMethod("toString");
                                    if (m != null) {
                                        m.insertBefore("{ System.out.println(\"Some Extensive Tracing\"); }");
                                    }
                                    ctClass.writeFile(output.get().asFile.absolutePath);
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
                onVariants(selector().all(), { variant ->
                    TaskProvider<ModifyClassesTask> taskProvider = project.tasks.register(variant.getName() + "ModifyAllClasses", ModifyClassesTask.class)
                    variant.artifacts.use(taskProvider)
                        .wiredWith( { it.getAllClasses() }, { it.getOutput() })
                        .toTransform(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE)
                })
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
    fun appendToAllClasses() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleDebug"))
            addClasspath("org.javassist:javassist:3.22.0-GA")
            addModule(":app") {
                addSource("src/main/java/com/android/api/tests/SomeSource.java", """
                    package com.android.api.tests;

                    class SomeSource {
                        public String toString() {
                            return "Something !";
                        }
                    }
                """.trimIndent())
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=java
                    """
            plugins {
                id 'com.android.application'
            }
            import com.android.build.api.artifact.MultipleArtifact;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.Directory;
            import org.gradle.api.tasks.TaskAction;
            import javassist.ClassPool;
            import javassist.CtClass;
            import java.io.FileInputStream;

            abstract class AddClassesTask extends DefaultTask {

                @OutputFiles
                abstract DirectoryProperty getOutput();

                @TaskAction
                void taskAction() {

                    ClassPool pool = new ClassPool(ClassPool.getDefault());
                    CtClass interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                    System.out.println("Adding ${'$'}interfaceClass");
                    interfaceClass.writeFile(output.get().asFile.absolutePath);
                }
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants(selector().all(), { variant ->
                    TaskProvider<AddClassesTask> taskProvider = project.tasks.register(variant.getName() + "AddAllClasses", AddClassesTask.class)
                    variant.artifacts.use(taskProvider)
                        .wiredWith( { it.getOutput() })
                        .toAppendTo(MultipleArtifact.ALL_CLASSES_DIRS.INSTANCE)
                })
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
            apkAnalyzer.dexCode(apk, "com.android.api.tests.SomeSource", null, null, null)
            apkAnalyzer.dexCode(apk, "com.android.api.tests.SomeInterface", null, null, null)
            Truth.assertThat(byteArrayOutputStream.toString()).contains("SomeSource")
            Truth.assertThat(byteArrayOutputStream.toString()).contains("SomeInterface")
        }
    }
}
