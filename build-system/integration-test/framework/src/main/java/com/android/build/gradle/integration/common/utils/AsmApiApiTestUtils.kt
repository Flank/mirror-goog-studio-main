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

package com.android.build.gradle.integration.common.utils

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.apk.Apk
import com.google.common.truth.Truth
import org.jf.dexlib2.dexbacked.DexBackedClassDef

/**
 * Utils for tests that are based on asmTransformApi test project.
 */
object AsmApiApiTestUtils {
    private const val instrumentedAnnotationDescriptor =
            "Lcom/example/instrumentationlib/instrumentation/InstrumentedAnnotation;"
    private const val instrumentedInterfaceDescriptor =
            "Lcom/example/instrumentationlib/instrumentation/InstrumentedInterface;"
    const val appClassesDescriptorPrefix = "Lcom/example/myapplication/"
    const val libClassesDescriptorPrefix = "Lcom/example/lib/"
    const val featureClassesDescriptorPrefix = "Lcom/example/feature/"

    val projectClasses = listOf(
            "BuildConfig",
            "R",
            "ClassImplementsI",
            "ClassWithNoInterfacesOrSuperclasses",
            "ClassExtendsOneClassAndImplementsTwoInterfaces",
            "ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces"
    )

    val libClasses = listOf(
            "BuildConfig",
            "R",
            "I",
            "InterfaceExtendsI"
    )

    val featureClasses = listOf(
            "BuildConfig",
            "R",
            "ClassExtendsAnAppClass"
    )

    private fun listToString(list: List<String>): String {
        return list.joinToString(separator = ",") {
            "\"$it\""
        }
    }

    fun configureExtensionForAnnotationAddingVisitor(
            project: GradleTestProject,
            methodsToAnnotate: List<String> = listOf("f1", "f3", "f4"),
            interfacesOfClassesToInstrument: List<String> = listOf(
                    "com.example.lib.I",
                    "com.example.lib.InterfaceExtendsI"
            ),
            subProject: String = ":app"
    ) {
        TestFileUtils.appendToFile(
                project.getSubproject(subProject).buildFile,
                """
            instrumentation.annotationAddingConfig.methodNamesToBeAnnotated.addAll(
                ${listToString(methodsToAnnotate)}
            )
            instrumentation.annotationAddingConfig.interfacesNamesToBeInstrumented.addAll(
                ${listToString(interfacesOfClassesToInstrument)}
            )
            """.trimIndent()
        )
    }

    fun configureExtensionForInterfaceAddingVisitor(
            project: GradleTestProject,
            enabled: Boolean = true,
            classesToInstrument: List<String> = listOf(
                    "com.example.myapplication.ClassWithNoInterfacesOrSuperclasses",
                    "com.example.myapplication.ClassExtendsOneClassAndImplementsTwoInterfaces",
                    "com.example.lib.InterfaceExtendsI"
            ),
            subProject: String = ":app"
    ) {
        TestFileUtils.appendToFile(
                project.getSubproject(subProject).buildFile,
                """
            instrumentation.interfaceAddingConfig.enabled = $enabled
            instrumentation.interfaceAddingConfig.classesToInstrument.addAll(
                ${listToString(classesToInstrument)}
            )
            """.trimIndent()
        )
    }

    fun checkClassesAreInstrumented(
            apk: Apk,
            classesDescriptorPackagePrefix: String,
            expectedClasses: List<String>,
            expectedAnnotatedMethods: Map<String, List<String>>,
            expectedInstrumentedClasses: List<String>
    ) {
        val filteredClasses = apk.allDexes.flatMap { it.classes.entries }
                .associate { it.key to it.value }
                .filterKeys {
                    it.startsWith(classesDescriptorPackagePrefix)
                }
        Truth.assertThat(filteredClasses.keys)
                .containsExactlyElementsIn(expectedClasses.map { name ->
                    "$classesDescriptorPackagePrefix$name;"
                })
        assertExactlyTheGivenMethodsAreAnnotated(
                filteredClasses,
                expectedAnnotatedMethods.mapKeys { entry -> "$classesDescriptorPackagePrefix${entry.key};" }
        )
        assertExactlyTheGivenClassesAreImplementingInstrumentedInterface(
                filteredClasses,
                expectedInstrumentedClasses.map { name -> "$classesDescriptorPackagePrefix$name;" }
        )
    }

    private fun assertExactlyTheGivenMethodsAreAnnotated(
            classesMap: Map<String, DexBackedClassDef>,
            expectedAnnotatedMethods: Map<String, List<String>>
    ) {
        var annotatedMethodsCount = 0
        classesMap.forEach { (name, clazz) ->
            clazz.methods.forEach { method ->
                if (expectedAnnotatedMethods[name]?.contains(method.name) == true) {
                    Truth.assertThat(method.annotations).hasSize(1)
                    Truth.assertThat(method.annotations.first().type).isEqualTo(
                            instrumentedAnnotationDescriptor
                    )
                    annotatedMethodsCount++
                } else {
                    Truth.assertThat(method.annotations).isEmpty()
                }
            }
        }
        Truth.assertThat(annotatedMethodsCount)
                .isEqualTo(expectedAnnotatedMethods.values.sumBy { it.size })
    }

    private fun assertExactlyTheGivenClassesAreImplementingInstrumentedInterface(
            classesMap: Map<String, DexBackedClassDef>,
            expectedInstrumentedClasses: List<String>
    ) {
        var instrumentedClassesCount = 0
        classesMap.forEach { (name, clazz) ->
            if (expectedInstrumentedClasses.contains(name)) {
                Truth.assertThat(clazz.interfaces).contains(instrumentedInterfaceDescriptor)
                instrumentedClassesCount++
            } else {
                Truth.assertThat(clazz.interfaces).doesNotContain(instrumentedInterfaceDescriptor)
            }
        }
        Truth.assertThat(instrumentedClassesCount).isEqualTo(expectedInstrumentedClasses.size)
    }
}
