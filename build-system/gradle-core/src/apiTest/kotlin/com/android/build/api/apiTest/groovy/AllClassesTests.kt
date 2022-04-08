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

            import com.android.build.api.variant.ScopedArtifacts
            import com.android.build.api.artifact.ScopedArtifact
            androidComponents {
                onVariants(selector().all(), { variant ->
                    TaskProvider taskProvider = project.tasks.register(variant.getName() + "GetAllClasses", GetAllClassesTask.class)
                    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toGet(
                            ScopedArtifact.CLASSES.INSTANCE,
                            { it.allJars },
                            { it.allDirectories }
                        )
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
# Scoped Artifacts toGet example in Groovy
This sample shows how to obtain all the classes that will be used to create the dex files.
There are two lists that need to be used to obtain the complete set of classes because some
classes are present as .class files in directories and others are present in jar files.
Therefore, you must process both [ListProperty] of [Directory] and [RegularFile] to get the full
list.

The [onVariants] block will wire the [GetAllClassesTask] input properties (allJars and allDirectories)
by using the [ScopedArtifactsOperation.toGet] method with the right [ScopedArtifact].
`
    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
        .use(taskProvider)
        .toGet(
            ScopedArtifact.CLASSES.INSTANCE,
            { it.allJars },
            { it.allDirectories }
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

            import com.android.build.api.artifact.ScopedArtifact;
            import com.android.build.api.variant.ScopedArtifacts;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.Directory;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.tasks.InputFiles;
            import org.gradle.api.tasks.TaskAction;
            import javassist.ClassPool;
            import javassist.CtClass;
            import javassist.CtMethod;
            import java.io.FileInputStream;
            import java.util.jar.JarEntry
            import java.util.jar.JarFile
            import java.util.jar.JarOutputStream

            abstract class ModifyClassesTask extends DefaultTask {

                @InputFiles
                abstract ListProperty<RegularFile> getAllJars();

                @InputFiles
                abstract ListProperty<Directory> getAllDirectories();

                @OutputFiles
                abstract RegularFileProperty getOutput();

                @TaskAction
                void taskAction() {

                    ClassPool pool = new ClassPool(ClassPool.getDefault());

                    OutputStream jarOutput = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(
                       output.get().getAsFile()
                    )))

                    // Adding new Interface.
                    CtClass interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                    System.out.println("Adding ${'$'}interfaceClass");
                    jarOutput.putNextEntry(new JarEntry("com/android/api/tests/SomeInterface.class"))
                    jarOutput.write(interfaceClass.toBytecode())
                    jarOutput.closeEntry()

                    allJars.get().forEach { file ->
                        println("JarFile : " + file.asFile.getAbsolutePath())
                        JarFile jarFile = new JarFile(file.asFile)
                        for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                            JarEntry jarEntry = e.nextElement();
                            println("Adding from jar ${'$'}{jarEntry.name}")
                            jarOutput.putNextEntry(new JarEntry(jarEntry.name))
                            jarFile.getInputStream(jarEntry).withCloseable {
                                jarOutput << it
                            }
                            jarOutput.closeEntry()
                        }
                        jarFile.close()
                   }

                    allDirectories.get().forEach { directory ->
                        System.out.println("Directory : ${'$'}{directory.asFile.absolutePath}");
                        directory.asFile.traverse(type: groovy.io.FileType.FILES) { file ->
                            System.out.println(file.absolutePath);
                            if (file.name == "SomeSource.class") {
                                System.out.println("File : ${'$'}{file.absolutePath}");
                                new FileInputStream(file).withCloseable {
                                    CtClass ctClass = pool.makeClass(it);
                                    ctClass.addInterface(interfaceClass);
                                    CtMethod m = ctClass.getDeclaredMethod("toString");
                                    if (m != null) {
                                        m.insertBefore("{ System.out.println(\"Some Extensive Tracing\"); }");
                                    }
                                    // write modified class.
                                    jarOutput.putNextEntry(new JarEntry("com/android/api/tests/SomeSource.class"))
                                    jarOutput.write(ctClass.toBytecode())
                                    jarOutput.closeEntry()
                                }
                            } else {
                                String relativePath = directory.asFile.toURI().relativize(file.toURI()).getPath()
                                println("Adding from directory ${'$'}{relativePath.replace(File.separatorChar, '/' as char)}")
                                jarOutput.putNextEntry(new JarEntry(relativePath.replace(File.separatorChar, '/' as char)))
                                new FileInputStream(file).withCloseable { inputStream ->
                                    jarOutput << inputStream
                                }
                                jarOutput.closeEntry()
                            }
                        }
                    }
                    jarOutput.close()
                }
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants(selector().all(), { variant ->
                    TaskProvider<ModifyClassesTask> taskProvider = project.tasks.register(variant.getName() + "ModifyAllClasses", ModifyClassesTask.class)
                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toTransform(ScopedArtifact.CLASSES.INSTANCE,  { it.getAllJars() }, { it.getAllDirectories() }, { it.getOutput() })
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
# Scoped Artifacts to transform project classes in Groovy
This sample shows how to transform all the classes that will be used to create the dex files.
There are two lists that need to be used to obtain the complete set of classes because some
classes are present as .class files in directories and others are present in jar files.
Therefore, you must process both [ListProperty] of [Directory] and [RegularFile] to get the full
list.

The Variant API provides a convenient API to transform bytecodes based on ASM but this example
is using javassist to show how this can be done using a different bytecode enhancer.

The [onVariants] block will wire the [ModifyClassesTask] input properties [allJars] and
[allDirectories] to the [output] folder
`
    variant.artifacts
        .forScope(ScopedArtifacts.Scope.PROJECT)
        .use(taskProvider)
        .toTransform(
            ScopedArtifact.CLASSES.INSTANCE,
            { it.getAllJars() },
            { it.getAllDirectories() },
            { it.getOutput() })
`
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
            import com.android.build.api.variant.ScopedArtifacts;
            import com.android.build.api.artifact.MultipleArtifact;
            import com.android.build.api.artifact.ScopedArtifact;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.InputFiles;
            import org.gradle.api.tasks.TaskAction;
            import javassist.ClassPool;
            import javassist.CtClass;
            import javassist.CtMethod;
            import java.util.jar.JarEntry
            import java.util.jar.JarFile
            import java.util.jar.JarOutputStream

            abstract class ReplaceClassesTask extends DefaultTask {

                @OutputFiles
                abstract RegularFileProperty getOutput();

                @TaskAction
                void taskAction() {

                    ClassPool pool = new ClassPool(ClassPool.getDefault());

                    new JarOutputStream(new BufferedOutputStream(new FileOutputStream(
                       output.get().getAsFile()
                    ))).withCloseable {

                        // Adding new Interface.
                        CtClass interfaceClass = pool.makeInterface("com.android.api.tests.SomeInterface");
                        System.out.println("Adding ${'$'}interfaceClass");
                        it.putNextEntry(new JarEntry("com/android/api/tests/SomeInterface.class"))
                        it.write(interfaceClass.toBytecode())
                        it.closeEntry()
                    }
                }
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants(selector().all(), { variant ->
                    TaskProvider<ReplaceClassesTask> taskProvider = project.tasks.register(variant.getName() + "ReplaceAllClasses", ReplaceClassesTask.class)
                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toReplace(
                            ScopedArtifact.CLASSES.INSTANCE,
                            { it.getOutput() }
                        )
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
# Scoped Artifacts toReplace Project classes in Groovy
This sample shows how to replace all the project classes that will be used to create the dex files.

The [onVariants] block will wire [ReplaceClassesTask]'s [output] folder to contain all the new
project classes :
`
    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
        .use(taskProvider)
        .toReplace(
            ScopedArtifact.CLASSES.INSTANCE,
            { it.getOutput() }
`

## To Run
./gradlew :app:assembleDebug
expected result : all .class files are provided by the [ReplaceClassesTask] will be packaged in the
resulting APK
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
        }
    }


    @Test
    fun appendToProjectClasses() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:assembleDebug"))
            addClasspath("org.javassist:javassist:3.26.0-GA")
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
            import com.android.build.api.variant.ScopedArtifacts
            import com.android.build.api.artifact.ScopedArtifact

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import javassist.ClassPool;
            import javassist.CtClass;

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
                    variant.artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .use(taskProvider)
                        .toAppend(
                            ScopedArtifact.CLASSES.INSTANCE,
                            { it.getOutput() }
                        )
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
# append ScopedArtifact in Kotlin
This sample shows how to add new classes to the project.
There are two lists that need to be used to obtain the complete set of classes because some
classes are present as .class files in directories and others are present in jar files.
Therefore, you must query both [ListProperty] of [Directory] and [RegularFile] to get the full list.

In this example, we only query the [ListProperty] of [Directory] to invoke some bytecode
instrumentation on classes.

The Variant API provides a convenient API to transform bytecodes based on ASM but this example
is using javassist to show how this can be done using a different bytecode enhancer.


The [onVariants] block will wire the [AddClassesTask]'s [output] folder to append
`toAppend(
    ScopedArtifact.CLASSES,
    taskProvider.flatMap(AddClassesTask::getOutput)
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
                    Truth.assertThat(it.variantApiAccess.variantPropertiesAccessList
                        .map(VariantPropertiesAccess::getType))
                        .containsExactly(
                            VariantPropertiesMethodType.ARTIFACTS_VALUE,
                            VariantPropertiesMethodType.FOR_SCOPE_VALUE,
                            VariantPropertiesMethodType.SCOPED_ARTIFACTS_APPEND_VALUE,
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
            apkAnalyzer.dexCode(apk, "com.android.api.tests.SomeSource", null, null, null)
            apkAnalyzer.dexCode(apk, "com.android.api.tests.SomeInterface", null, null, null)
            Truth.assertThat(byteArrayOutputStream.toString()).contains("SomeSource")
            Truth.assertThat(byteArrayOutputStream.toString()).contains("SomeInterface")
        }
    }
}
