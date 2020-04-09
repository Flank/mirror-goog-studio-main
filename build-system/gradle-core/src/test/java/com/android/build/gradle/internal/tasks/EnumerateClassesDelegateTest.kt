/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.dependency.EnumerateClassesDelegate
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

class EnumerateClassesDelegateTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    private fun getClassListFromOutput(outputFile: Path): List<String> {
        return outputFile.toFile().readLines()
    }

    @Test
    fun testNoClasses() {
        val jar = tmp.root.toPath().resolve("jar.jar")
        TestInputsGenerator.jarWithEmptyClasses(jar, listOf())

        val output = tmp.root.toPath().resolve("out.json")

        EnumerateClassesDelegate().run(
            jar.toFile(),
            output.toFile()
        )

        val enumeratedClasses = getClassListFromOutput(output)

        Truth.assertThat(enumeratedClasses).isEmpty()
    }

    @Test
    fun testWithClasses() {
        val jar = tmp.root.toPath().resolve("jar.jar")
        TestInputsGenerator.jarWithEmptyClasses(
            jar, listOf("test/A", "test/B", "com/example/A", "com/example/C"))

        val output = tmp.root.toPath().resolve("out.json")

        EnumerateClassesDelegate().run(
            jar.toFile(),
            output.toFile()
        )

        val enumeratedClasses = getClassListFromOutput(output)

        Truth.assertThat(enumeratedClasses)
            .containsExactly("test.A", "test.B", "com.example.A", "com.example.C")
    }
}