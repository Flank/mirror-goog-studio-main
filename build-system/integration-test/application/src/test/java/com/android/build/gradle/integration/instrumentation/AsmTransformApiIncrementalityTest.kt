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

package com.android.build.gradle.integration.instrumentation

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.appClassesDescriptorPrefix
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.checkClassesAreInstrumented
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.configureExtensionForAnnotationAddingVisitor
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.configureExtensionForInterfaceAddingVisitor
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.libClasses
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.libClassesDescriptorPrefix
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.projectClasses
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.instrumentation.loadClassData
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests incremental changes to project and dependencies classes.
 */
class AsmTransformApiIncrementalityTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("asmTransformApi")
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF).create()

    @Test
    fun testIncrementalProjectCodeChange() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(
                project = project,
                classesToInstrument = listOf(
                        "com.example.myapplication.ClassWithNoInterfacesOrSuperclasses",
                        "com.example.myapplication.ClassExtendsOneClassAndImplementsTwoInterfaces",
                        "com.example.lib.InterfaceExtendsI",
                        "com.example.myapplication.ANewClassImplementsI"
                )
        )

        project.executor().run(":app:assembleDebug")

        // Add class ANewClassImplementsI
        FileUtils.writeToFile(
                project.getSubproject(":app")
                        .file("src/main/java/com/example/myapplication/ANewClassImplementsI.kt"),
                """package com.example.myapplication

                import com.example.lib.I

                class ANewClassImplementsI : I {
                    override fun f1() {}
                    fun f4() {}
                }
            """.trimIndent()
        )

        // modify class ClassExtendsOneClassAndImplementsTwoInterfaces (add method f4)
        TestFileUtils.searchAndReplace(
                project.getSubproject(":app")
                        .file("src/main/java/com/example/myapplication/ClassExtendsOneClassAndImplementsTwoInterfaces.kt"),
                "override fun f3() {}",
                "override fun f3() {} fun f4() {}"
        )

        // delete class ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces
        project.getSubproject(":app")
                .file("src/main/java/com/example/myapplication/ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces.kt")
                .delete()

        val taskOutputDir = FileUtils.join(
                project.getSubproject(":app").intermediatesDir,
                "asm_instrumented_project_classes",
                "debug"
        )
        val originalFiles = getClassFilesModifiedTimeMap(taskOutputDir)

        val result = project.executor().run(":app:assembleDebug")

        assertThat(result.didWorkTasks).contains(":app:transformDebugClassesWithAsm")

        val filesAfterModification = getClassFilesModifiedTimeMap(taskOutputDir)

        assertThat(originalFiles.keys).containsExactlyElementsIn(
                listOf(
                        "ClassImplementsI.class",
                        "ClassWithNoInterfacesOrSuperclasses.class",
                        "ClassExtendsOneClassAndImplementsTwoInterfaces.class",
                        "ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces.class",
                        "BuildConfig.class"
                )
        )

        assertThat(filesAfterModification.keys).containsExactlyElementsIn(
                listOf(
                        "ClassImplementsI.class",
                        "ClassWithNoInterfacesOrSuperclasses.class",
                        "ClassExtendsOneClassAndImplementsTwoInterfaces.class",
                        "ANewClassImplementsI.class",
                        "BuildConfig.class"
                )
        )

        // Only ClassExtendsOneClassAndImplementsTwoInterfaces should be modified
        filesAfterModification.forEach { (name, modifiedTime) ->
            if (name == "ClassExtendsOneClassAndImplementsTwoInterfaces.class") {
                assertThat(originalFiles[name]).isNotEqualTo(modifiedTime)
            } else if (name != "ANewClassImplementsI.class") {
                assertThat(originalFiles[name]).isEqualTo(modifiedTime)
            }
        }

        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        // app classes
        checkClassesAreInstrumented(
                apk = apk,
                classesDescriptorPackagePrefix = appClassesDescriptorPrefix,
                expectedClasses = projectClasses.toMutableList().apply {
                    remove("ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces")
                    add("ANewClassImplementsI")
                },
                expectedAnnotatedMethods = mapOf(
                        "ClassImplementsI" to listOf("f1"),
                        "ClassExtendsOneClassAndImplementsTwoInterfaces" to listOf("f3", "f4"),
                        "ANewClassImplementsI" to listOf("f1", "f4")
                ),
                expectedInstrumentedClasses = listOf(
                        "ClassWithNoInterfacesOrSuperclasses",
                        "ClassExtendsOneClassAndImplementsTwoInterfaces",
                        "ANewClassImplementsI"
                )
        )

        // lib classes
        checkClassesAreInstrumented(
                apk = apk,
                classesDescriptorPackagePrefix = libClassesDescriptorPrefix,
                expectedClasses = libClasses,
                expectedAnnotatedMethods = mapOf(
                        "InterfaceExtendsI" to listOf("f3")
                ),
                expectedInstrumentedClasses = listOf("InterfaceExtendsI")
        )
    }

    @Test
    fun testChangeInLibraryCode() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(
                project = project,
                classesToInstrument = listOf(
                        "com.example.myapplication.ClassWithNoInterfacesOrSuperclasses",
                        "com.example.myapplication.ClassExtendsOneClassAndImplementsTwoInterfaces",
                        "com.example.lib.InterfaceExtendsI",
                        "com.example.lib.NewInterfaceExtendsI"
                )
        )
        project.executor().run(":app:assembleDebug")

        // Add interface NewInterfaceExtendsI in lib
        FileUtils.writeToFile(
                project.getSubproject(":lib")
                        .file("src/main/java/com/example/lib/NewInterfaceExtendsI.kt"),
                """package com.example.lib

                interface NewInterfaceExtendsI : I {
                    fun f3()
                    fun f4()
                }
            """.trimIndent()
        )

        project.executor().run(":app:assembleDebug")
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        // lib classes
        checkClassesAreInstrumented(
                apk = apk,
                classesDescriptorPackagePrefix = libClassesDescriptorPrefix,
                expectedClasses = libClasses.toMutableList().apply {
                    add("NewInterfaceExtendsI")
                },
                expectedAnnotatedMethods = mapOf(
                        "InterfaceExtendsI" to listOf("f3"),
                        "NewInterfaceExtendsI" to listOf("f3", "f4")
                ),
                expectedInstrumentedClasses = listOf("InterfaceExtendsI", "NewInterfaceExtendsI")
        )
    }

    @Test
    fun loadedClassChanged() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(project)

        // Make the AnnotationAddingClassVisitorFactory query for ClassImplementsI class data
        TestFileUtils.searchAndReplace(
            project.getSubproject(":buildSrc")
                .file("src/main/java/com/example/buildsrc/instrumentation/AnnotationAddingClassVisitorFactory.kt"),
            "return AnnotationAddingClassVisitor(",
            "classContext.loadClassData(\"com.example.myapplication.ClassImplementsI\")" +
                    System.lineSeparator() +
                    "return AnnotationAddingClassVisitor("
        )

        project.executor().run(":app:transformDebugClassesWithAsm")

        val incrementalDir = FileUtils.join(
            project.getSubproject(":app").intermediatesDir,
            "incremental",
            "transformDebugClassesWithAsm"
        )

        assertThat(incrementalDir.listFiles()).hasLength(1)
        var classData = loadClassData(incrementalDir.listFiles()!![0])!!
        assertThat(classData.className).isEqualTo("com.example.myapplication.ClassImplementsI")
        assertThat(classData.interfaces).containsExactly("com.example.lib.I")
        assertThat(classData.superClasses).containsExactly("java.lang.Object")
        assertThat(classData.classAnnotations).isEmpty()

        val taskOutputDir = FileUtils.join(
            project.getSubproject(":app").intermediatesDir,
            "asm_instrumented_project_classes",
            "debug"
        )
        var originalFiles = getClassFilesModifiedTimeMap(taskOutputDir)

        // change ClassImplementsI in a way that doesn't affect the class data, and so we should
        // be still running incrementally
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/main/java/com/example/myapplication/ClassImplementsI.kt"),
            "fun f2() {}",
            "fun f2() {}" + System.lineSeparator() +
                    "fun f() {}"
        )

        var result = project.executor().run(":app:transformDebugClassesWithAsm")

        assertThat(result.didWorkTasks).contains(":app:transformDebugClassesWithAsm")

        var modifiedFiles = getClassFilesModifiedTimeMap(taskOutputDir).filter {
            originalFiles[it.key] != it.value
        }

        assertThat(modifiedFiles).hasSize(1)
        assertThat(modifiedFiles.keys).containsExactly("ClassImplementsI.class")

        originalFiles = getClassFilesModifiedTimeMap(taskOutputDir).filterNot {
            it.key == "BuildConfig.class"
        }

        // change ClassImplementsI in a way that will change the class data, and so we should run
        // non incrementally
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/main/java/com/example/myapplication/ClassImplementsI.kt"),
            "ClassImplementsI : I",
            "ClassImplementsI : I, java.io.Serializable"
        )

        result = project.executor().run(":app:transformDebugClassesWithAsm")

        assertThat(result.didWorkTasks).contains(":app:transformDebugClassesWithAsm")

        modifiedFiles = getClassFilesModifiedTimeMap(taskOutputDir).filter {
            it.key != "BuildConfig.class" && originalFiles[it.key] != it.value
        }

        // all classes should be modified
        assertThat(modifiedFiles).hasSize(originalFiles.size)

        // new class data should be outputted
        assertThat(incrementalDir.listFiles()).hasLength(1)
        classData = loadClassData(incrementalDir.listFiles()!![0])!!
        assertThat(classData.className).isEqualTo("com.example.myapplication.ClassImplementsI")
        assertThat(classData.interfaces).containsExactly("com.example.lib.I", "java.io.Serializable")
        assertThat(classData.superClasses).containsExactly("java.lang.Object")
        assertThat(classData.classAnnotations).isEmpty()
    }

    private fun getClassFilesModifiedTimeMap(outputDir: File): Map<String, Long> {
        return FileUtils.getAllFiles(outputDir).filter { it!!.name.endsWith(SdkConstants.DOT_CLASS) }.map {
            it.name to it.lastModified()
        }.toMap()
    }
}
