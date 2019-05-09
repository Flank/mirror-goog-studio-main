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
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.NewClass
import com.android.build.gradle.internal.transforms.testdata.SomeClass
import com.android.build.gradle.internal.transforms.testdata.SomeOtherClass
import com.android.testutils.TestInputsGenerator
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Arrays
import kotlin.test.assertEquals

class DependenciesAnalyzerTest {

    @Rule
    @JvmField
    val tempDir = TemporaryFolder()

    private val expectedOutput = mutableListOf("kotlin.Metadata", "java.lang.Object")

    /** Tests the analyzer finds the types from the class header (superclass, interfaces
     * and generics) */
    @Test
    fun testTypesInClassHeader() {
        val input = tempDir.newFolder("classes")
        val classes = listOf(SomeClass::class.java)

        TestInputsGenerator.pathWithClasses(input.toPath(), classes)

        expectedOutput.addAll(
            Arrays.asList(
                "com.android.build.gradle.internal.transforms.testdata.YetAnotherClass",
                "com.android.build.gradle.internal.transforms.testdata.SomeInterface",
                "com.android.build.gradle.internal.transforms.testdata.CarbonForm",
                "com.android.build.gradle.internal.transforms.testdata.SomeClass",
                "com.android.build.gradle.internal.transforms.testdata.Animal",
                "com.android.build.gradle.internal.transforms.testdata.Dog",
                "com.android.build.gradle.internal.transforms.testdata.Cat",
                "java.util.List"
            )
        )

        val analyzer = DependenciesAnalyzer()
        val output = mutableListOf<String>()
        input.walk().forEach {
            if (it.isFile && it.name.endsWith(SdkConstants.DOT_CLASS)) {
                output.addAll(analyzer.findAllDependencies(it))
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
            Arrays.asList(
                "com.android.build.gradle.internal.transforms.testdata.YetAnotherClass",
                "com.android.build.gradle.internal.transforms.testdata.SomeOtherClass",
                "com.android.build.gradle.internal.transforms.testdata.CarbonForm",
                "com.android.build.gradle.internal.transforms.testdata.SomeClass",
                "com.android.build.gradle.internal.transforms.testdata.NewClass",
                "com.android.build.gradle.internal.transforms.testdata.Animal",
                "com.android.build.gradle.internal.transforms.testdata.Tiger",
                "com.android.build.gradle.internal.transforms.testdata.Toy",
                "com.android.build.gradle.internal.transforms.testdata.Cat",
                "com.android.build.gradle.internal.transforms.testdata.Dog",
                "org.jetbrains.annotations.Nullable",
                "org.jetbrains.annotations.NotNull",
                "kotlin.jvm.internal.Intrinsics",
                "java.lang.Exception",
                "java.io.IOException",
                "java.util.Map"
            )
        )

        val analyzer = DependenciesAnalyzer()
        val output = mutableSetOf<String>()
        input.walk().forEach {
            if (it.isFile && it.name.endsWith(SdkConstants.DOT_CLASS)) {
                output.addAll(analyzer.findAllDependencies(it))
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
            Arrays.asList(
                "com.android.build.gradle.internal.transforms.testdata.NewClass\$Companion",
                "com.android.build.gradle.internal.transforms.testdata.YetAnotherClass",
                "com.android.build.gradle.internal.transforms.testdata.SomeClassKt",
                "com.android.build.gradle.internal.transforms.testdata.NewClass",
                "com.android.build.gradle.internal.transforms.testdata.Animal",
                "com.android.build.gradle.internal.transforms.testdata.Toy",
                "org.jetbrains.annotations.NotNull",
                "kotlin.jvm.internal.Intrinsics",
                "java.lang.String"
            )
        )

        val analyzer = DependenciesAnalyzer()
        val output = mutableSetOf<String>()
        input.walk().forEach {
            if (it.isFile && it.name.endsWith(SdkConstants.DOT_CLASS)) {
                output.addAll(analyzer.findAllDependencies(it))
            }
        }

        assertEquals(expectedOutput.sorted(), output.sorted())
    }

}