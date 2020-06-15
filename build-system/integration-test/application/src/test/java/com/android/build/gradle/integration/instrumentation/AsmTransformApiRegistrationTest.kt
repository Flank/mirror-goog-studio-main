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
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.libClasses
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.libClassesDescriptorPrefix
import com.android.build.gradle.integration.common.utils.AsmApiApiTestUtils.projectClasses
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/**
 * Tests the API configuration (registration order, changes in supplied parameters, ...)
 */
class AsmTransformApiRegistrationTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("asmTransformApi")
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF).create()

    @Test
    fun testVisitorsAreChainedInRegistrationOrder() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(project)

        TestFileUtils.searchAndReplace(
                project.getSubproject(":buildSrc")
                        .file("src/main/java/com/example/buildsrc/plugin/InstrumentationPlugin.kt"),
                "interfaceVisitorFirst = false", "interfaceVisitorFirst = true"
        )

        // by reversing the order of registration, one of the visitors will throw an exception

        val result = project.executor().expectFailure().run("app:transformDebugClassesWithAsm")
        Truth.assertThat(result.failedTasks)
                .containsExactly(":app:transformDebugClassesWithAsm")
        Truth.assertThat(result.failureMessage)
                .isEqualTo("InterfaceAddingClassVisitor shouldn't visit before AnnotationAddingClassVisitor")
    }

    @Test
    fun changeInInstrumentationParametersShouldRerunInstrumentation() {
        configureExtensionForAnnotationAddingVisitor(project)
        configureExtensionForInterfaceAddingVisitor(project)

        project.executor().run(":app:assembleDebug")

        TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "instrumentation.interfaceAddingConfig.enabled = true",
                "instrumentation.interfaceAddingConfig.enabled = false"
        )

        val result = project.executor().run("assembleDebug")

        Truth.assertThat(result.didWorkTasks).contains(":app:transformDebugClassesWithAsm")

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
}
