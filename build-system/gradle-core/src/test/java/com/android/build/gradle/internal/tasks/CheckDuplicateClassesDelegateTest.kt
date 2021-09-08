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

import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path
import kotlin.test.assertFailsWith


private const val RECOMMENDATION =
    "Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>."

class CheckDuplicateClassesDelegateTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    val lineSeparator: String = System.lineSeparator()

    private fun makeEnumeratedClasses(classesFile: Path, classes: List<String>): Path {
        val outputString = classes.joinToString(separator = "\n")

        classesFile.toFile().writeText(outputString)

        return classesFile
    }

    @Test
    fun testNoArtifacts() {
        val classesArtifacts = mapOf<String, File>()

        CheckDuplicateClassesDelegate().run(classesArtifacts)
    }

    @Test
    fun testSingleArtifacts() {

        val jar = tmp.root.toPath().resolve("jar")
        makeEnumeratedClasses(jar, listOf("test.A"))

        val classesArtifacts = mapOf<String, File>("identifier" to jar.toFile())

        // Nothing should happen, no fails
        CheckDuplicateClassesDelegate().run(classesArtifacts)
    }

    @Test
    fun test2Artifacts_noDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1")
        makeEnumeratedClasses(jar1, listOf("test.A"))

        val jar2 = tmp.root.toPath().resolve("jar2")
        makeEnumeratedClasses(jar2, listOf("test.B"))

        val classesArtifacts = mapOf<String, File>(
            "identifier1" to jar1.toFile(),
            "identifier2" to jar2.toFile())

        // Nothing should happen, no fails
        CheckDuplicateClassesDelegate().run(classesArtifacts)
    }

    @Test
    fun test2Artifacts_withDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1")
        makeEnumeratedClasses(jar1, listOf("test.A"))

        val jar2 = tmp.root.toPath().resolve("jar2")
        makeEnumeratedClasses(jar2, listOf("test.A"))

        val classesArtifacts = mapOf<String, File>(
            "identifier1" to jar1.toFile(),
            "identifier2" to jar2.toFile())

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate().run(classesArtifacts)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in modules identifier1 and identifier2$lineSeparator$lineSeparator$RECOMMENDATION")
    }

    @Test
    fun test2Artifacts_with2Duplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1")
        makeEnumeratedClasses(jar1, listOf("test.A", "test.B"))

        val jar2 = tmp.root.toPath().resolve("jar2")
        makeEnumeratedClasses(jar2, listOf("test.A", "test.B"))

        val classesArtifacts = mapOf<String, File>(
            "identifier1" to jar1.toFile(),
            "identifier2" to jar2.toFile())

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate().run(classesArtifacts)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in modules identifier1 and identifier2$lineSeparator" +
                        "Duplicate class test.B found in modules identifier1 and identifier2$lineSeparator" +
                        "$lineSeparator$RECOMMENDATION")
    }

    @Test
    fun test3Artifacts_2ofWhichHasDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1")
        makeEnumeratedClasses(jar1, listOf("test.A"))

        val jar2 = tmp.root.toPath().resolve("jar2")
        makeEnumeratedClasses(jar2, listOf("test.A"))

        val jar3 = tmp.root.toPath().resolve("jar3")
        makeEnumeratedClasses(jar3, listOf("test.B"))

        val classesArtifacts = mapOf<String, File>(
            "identifier1" to jar1.toFile(),
            "identifier2" to jar2.toFile(),
            "identifier3" to jar3.toFile())

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate().run(classesArtifacts)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in modules identifier1 and identifier2$lineSeparator$lineSeparator$RECOMMENDATION")
    }

    @Test
    fun test3Artifacts_withDuplicates() {

        val jar1 = tmp.root.toPath().resolve("jar1")
        makeEnumeratedClasses(jar1, listOf("test.A"))

        val jar2 = tmp.root.toPath().resolve("jar2")
        makeEnumeratedClasses(jar2, listOf("test.A"))

        val jar3 = tmp.root.toPath().resolve("jar3")
        makeEnumeratedClasses(jar3, listOf("test.A"))

        val classesArtifacts = mapOf<String, File>(
            "identifier1" to jar1.toFile(),
            "identifier2" to jar2.toFile(),
            "identifier3" to jar3.toFile())

        val exception = assertFailsWith(RuntimeException::class) {
            CheckDuplicateClassesDelegate().run(classesArtifacts)
        }

        Truth.assertThat(exception.message)
            .contains(
                "Duplicate class test.A found in the following modules: identifier1, identifier2 and identifier3$lineSeparator$lineSeparator$RECOMMENDATION")
    }
}
