/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.transforms.testdata.NewClass
import com.android.build.gradle.internal.transforms.testdata.SomeClass
import com.android.build.gradle.internal.transforms.testdata.SomeOtherClass
import com.android.testutils.TestInputsGenerator
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class DependenciesAnalyzerTest {

    @Rule
    @JvmField
    val tempDir = TemporaryFolder()

    private val expectedOutput = mutableListOf("kotlin/Metadata.class", "java/lang/Object.class")

    /** Tests the analyzer finds the types from the class header (superclass, interfaces
     * and generics) */
    @Test
    fun testTypesInClassHeader() {
        val input = tempDir.newFolder("classes")
        val classes = listOf(SomeClass::class.java)

        TestInputsGenerator.pathWithClasses(input.toPath(), classes)

        expectedOutput.addAll(
                arrayOf(
                "com/android/build/gradle/internal/transforms/testdata/YetAnotherClass.class",
                "com/android/build/gradle/internal/transforms/testdata/SomeInterface.class",
                "com/android/build/gradle/internal/transforms/testdata/CarbonForm.class",
                "com/android/build/gradle/internal/transforms/testdata/EnumClass.class",
                "com/android/build/gradle/internal/transforms/testdata/SomeClass.class",
                "com/android/build/gradle/internal/transforms/testdata/Animal.class",
                "com/android/build/gradle/internal/transforms/testdata/Dog.class",
                "com/android/build/gradle/internal/transforms/testdata/Cat.class",
                "kotlin/jvm/internal/Intrinsics.class",
                "org/jetbrains/annotations/NotNull.class",
                "java/util/List.class"
                )
        )

        val analyzer = DependenciesAnalyzer()
        val output = mutableListOf<String>()
        input.walk().forEach {
            if (it.isFile && it.name.endsWith(SdkConstants.DOT_CLASS)) {
                output.addAll(analyzer.findAllDependencies(it.inputStream()))
            }
        }

        assertEquals(expectedOutput.sorted(), output.sorted())
    }

    @Test
    fun testTypesInMethods() {
        val input = tempDir.newFolder("classes")
        val classes = listOf(SomeOtherClass::class.java)

        TestInputsGenerator.pathWithClasses(input.toPath(), classes)

        expectedOutput.addAll(
                arrayOf(
                "com/android/build/gradle/internal/transforms/testdata/YetAnotherClass.class",
                "com/android/build/gradle/internal/transforms/testdata/SomeOtherClass.class",
                "com/android/build/gradle/internal/transforms/testdata/CarbonForm.class",
                "com/android/build/gradle/internal/transforms/testdata/SomeClass.class",
                "com/android/build/gradle/internal/transforms/testdata/EnumClass.class",
                "com/android/build/gradle/internal/transforms/testdata/NewClass.class",
                "com/android/build/gradle/internal/transforms/testdata/Animal.class",
                "com/android/build/gradle/internal/transforms/testdata/Tiger.class",
                "com/android/build/gradle/internal/transforms/testdata/Toy.class",
                "com/android/build/gradle/internal/transforms/testdata/Cat.class",
                "com/android/build/gradle/internal/transforms/testdata/Dog.class",
                "org/jetbrains/annotations/Nullable.class",
                "org/jetbrains/annotations/NotNull.class",
                "kotlin/jvm/internal/Intrinsics.class",
                "java/lang/Exception.class",
                "java/io/IOException.class",
                "java/util/Map.class"
                )
        )

        val analyzer = DependenciesAnalyzer()
        val output = mutableSetOf<String>()
        input.walk().forEach {
            if (it.isFile && it.name.endsWith(SdkConstants.DOT_CLASS)) {
                output.addAll(analyzer.findAllDependencies(it.inputStream()))
            }
        }

        assertEquals(expectedOutput.sorted(), output.sorted())
    }

    @Test
    fun testTypesInClassFields() {
        val input = tempDir.newFolder("classes")
        val classes = listOf(NewClass::class.java)

        TestInputsGenerator.pathWithClasses(input.toPath(), classes)

        expectedOutput.addAll(
                arrayOf(
                "com/android/build/gradle/internal/transforms/testdata/NewClass\$Companion.class",
                "com/android/build/gradle/internal/transforms/testdata/YetAnotherClass.class",
                "com/android/build/gradle/internal/transforms/testdata/SomeClassKt.class",
                "com/android/build/gradle/internal/transforms/testdata/NewClass.class",
                "com/android/build/gradle/internal/transforms/testdata/Animal.class",
                "com/android/build/gradle/internal/transforms/testdata/Toy.class",
                "org/jetbrains/annotations/NotNull.class",
                "kotlin/jvm/internal/Intrinsics.class",
                "java/lang/String.class"
                )
        )

        val analyzer = DependenciesAnalyzer()
        val output = mutableSetOf<String>()
        input.walk().forEach {
            if (it.isFile && it.name.endsWith(SdkConstants.DOT_CLASS)) {
                output.addAll(analyzer.findAllDependencies(it.inputStream()))
            }
        }

        assertEquals(expectedOutput.sorted(), output.sorted())
    }

}