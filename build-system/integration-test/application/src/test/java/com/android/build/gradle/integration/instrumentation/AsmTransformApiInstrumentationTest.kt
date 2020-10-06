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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.appClassesDescriptorPrefix
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.checkClassesAreInstrumented
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.configureExtensionForAnnotationAddingVisitor
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.configureExtensionForInterfaceAddingVisitor
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.featureClasses
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.featureClassesDescriptorPrefix
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.libClasses
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.libClassesDescriptorPrefix
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.projectClasses
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Tests the instrumentation pipeline for different plugins and scenarios.
 */
class AsmTransformApiInstrumentationTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("asmTransformApi")
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF).create()

    private fun assertClassesAreInstrumentedInDebugVariant() {
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        // app classes
        checkClassesAreInstrumented(
                apk = apk,
                classesDescriptorPackagePrefix = appClassesDescriptorPrefix,
                expectedClasses = projectClasses,
                expectedAnnotatedMethods = mapOf(
                        "ClassImplementsI" to listOf("f1"),
                        "ClassExtendsOneClassAndImplementsTwoInterfaces" to listOf("f3"),
                        "ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces" to
                                listOf("f4")
                ),
                expectedInstrumentedClasses = listOf(
                        "ClassWithNoInterfacesOrSuperclasses",
                        "ClassExtendsOneClassAndImplementsTwoInterfaces"
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
    fun classesAreInstrumentedInDebugVariant() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(project)

        project.executor().run(":app:assembleDebug")

        assertClassesAreInstrumentedInDebugVariant()

        // check task is up-to-date
        val result = project.executor().run(":app:assembleDebug")
        assertThat(result.upToDateTasks).contains(":app:transformDebugClassesWithAsm")
    }

    @Test
    fun featureClassesAreInstrumentedInDebugVariant() {
        configureExtensionForAnnotationAddingVisitor(
                project = project,
                subProject = ":feature",
                methodsToAnnotate = listOf("f3")
        )
        configureExtensionForInterfaceAddingVisitor(
                project = project,
                subProject = ":feature",
                classesToInstrument = listOf("com.example.feature.ClassExtendsAnAppClass")
        )

        project.executor().run(":feature:assembleDebug")

        // feature classes
        checkClassesAreInstrumented(
                apk = project.getSubproject(":feature").getApk(GradleTestProject.ApkType.DEBUG),
                classesDescriptorPackagePrefix = featureClassesDescriptorPrefix,
                expectedClasses = featureClasses,
                expectedAnnotatedMethods = mapOf(
                        "ClassExtendsAnAppClass" to listOf("f3")
                ),
                expectedInstrumentedClasses = listOf(
                        "ClassExtendsAnAppClass"
                )
        )
    }

    @Test
    fun unitTestClassesAreInstrumentedInDebugVariant() {
        project.getSubproject(":app")
                .file("src/test/java/com/example/unittest/InstrumentTest.kt").apply {
                    parentFile.mkdirs()
                    writeText(
                            // language=kotlin
                            """
                    package com.example.unittest

                    interface InstrumentTest
                    """.trimIndent()
                    )
                }

        project.getSubproject(":app")
                .file("src/test/java/com/example/unittest/UnitTestSourcesInstrumentationTest.kt")
                .writeText(
                        // language=kotlin
                        """
                    package com.example.unittest

                    import com.example.instrumentationlib.instrumentation.InstrumentedAnnotation
                    import com.example.instrumentationlib.instrumentation.InstrumentedInterface
                    import org.junit.Test

                    class UnitTestSourcesInstrumentationTest: InstrumentTest {

                        @Test
                        fun thisClassIsInstrumented() {
                            assert(this::class.java.interfaces.contains(InstrumentedInterface::class.java))
                        }

                        @Test
                        fun thisMethodIsInstrumented() {
                            assert(this::class.java.getMethod("thisMethodIsInstrumented")
                                .annotations.map { it.annotationClass }
                                .contains(InstrumentedAnnotation::class)
                            )
                        }
                    }
                    """.trimIndent()
                )

        configureExtensionForAnnotationAddingVisitor(
                project = project,
                subProject = ":app",
                methodsToAnnotate = listOf("thisMethodIsInstrumented"),
                interfacesOfClassesToInstrument = listOf("com.example.unittest.InstrumentTest")
        )
        configureExtensionForInterfaceAddingVisitor(
                project = project,
                subProject = ":app",
                classesToInstrument = listOf("com.example.unittest.UnitTestSourcesInstrumentationTest")
        )

        project.executor().run(":app:testDebugUnitTest")
    }

    @Test
    fun onlyAnnotationVisitorShouldInstrumentClassesInReleaseVariant() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(project)

        project.executor().run(":app:assembleRelease")

        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.RELEASE)

        // app classes
        checkClassesAreInstrumented(
                apk = apk,
                classesDescriptorPackagePrefix = appClassesDescriptorPrefix,
                expectedClasses = projectClasses,
                expectedAnnotatedMethods = mapOf(
                        "ClassImplementsI" to listOf("f1"),
                        "ClassExtendsOneClassAndImplementsTwoInterfaces" to listOf("f3"),
                        "ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces" to listOf(
                                "f4")
                ),
                expectedInstrumentedClasses = emptyList()
        )

        // lib classes
        checkClassesAreInstrumented(
                apk = apk,
                classesDescriptorPackagePrefix = libClassesDescriptorPrefix,
                expectedClasses = libClasses,
                expectedAnnotatedMethods = mapOf(
                        "InterfaceExtendsI" to listOf("f3")
                ),
                expectedInstrumentedClasses = emptyList()
        )
    }

    @Test
    fun testClassesAreInstrumentedWithDexingArtifactTransformDisabled() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(project)

        project.executor().with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
                .run(":app:assembleDebug")

        assertClassesAreInstrumentedInDebugVariant()
    }

    @Test
    fun instrumentedLibClassesShouldBeConsumedByTheApp() {
        configureExtensionForAnnotationAddingVisitor(
                project = project,
                subProject = ":lib",
                methodsToAnnotate = listOf("f3"),
                interfacesOfClassesToInstrument = listOf("com.example.lib.I")
        )
        configureExtensionForInterfaceAddingVisitor(
                project = project,
                subProject = ":lib",
                classesToInstrument = listOf("com.example.lib.InterfaceExtendsI")
        )

        project.executor().run(":app:assembleDebug")

        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        // app classes
        checkClassesAreInstrumented(
                apk = apk,
                classesDescriptorPackagePrefix = appClassesDescriptorPrefix,
                expectedClasses = projectClasses,
                expectedAnnotatedMethods = emptyMap(),
                expectedInstrumentedClasses = emptyList()
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
}
