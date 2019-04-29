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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ERROR_DUPLICATE
import com.android.builder.dexing.ERROR_DUPLICATE_HELP_PAGE
import com.android.builder.multidex.D8MainDexList
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

/**
 * Test for calculating the main dex list using D8.
 */
class D8MainDexListTaskTest {

    @Rule
    @JvmField
    val tmpDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun testProguardRules() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A"))

        val proguardRules = tmpDir.root.resolve("proguard_rules")
        proguardRules.writeText("-keep class test.A")

        D8MainDexListTask.MainDexListRunnable(
            D8MainDexListTask.MainDexListRunnable.Params(
                listOf(proguardRules),
                listOf(inputJar),
                getBootClasspath(),
                null,
                output,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE
            )
        ).run()

        assertThat(output.readLines()).containsExactly("test/A.class")
    }

    @Test
    fun testAllInputs() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A", "test/B"))

        val proguardRules = tmpDir.root.resolve("proguard_rules")
        proguardRules.writeText("-keep class test.A")

        val userProguardRules = tmpDir.root.resolve("user_proguard_rules")
        userProguardRules.writeText("-keep class test.B")

        D8MainDexListTask.MainDexListRunnable(
            D8MainDexListTask.MainDexListRunnable.Params(
                listOf(proguardRules, userProguardRules),
                listOf(inputJar),
                getBootClasspath(),
                null,
                output,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE
            )
        ).run()

        assertThat(output.readLines()).containsExactly("test/A.class", "test/B.class")
    }

    @Test
    fun testUserClassesKeptAndDeDuped() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A"))

        val userClasses = tmpDir.root.resolve("user_rules.txt")
        userClasses.writeText(
            listOf(
                "test/User1.class",
                "test/User2.class",
                "test/User2.class"
            ).joinToString(separator = System.lineSeparator())
        )

        D8MainDexListTask.MainDexListRunnable(
            D8MainDexListTask.MainDexListRunnable.Params(
                listOf(),
                listOf(inputJar),
                getBootClasspath(),
                userClasses,
                output,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE
            )
        ).run()

        assertThat(output.readLines()).containsExactly("test/User1.class", "test/User2.class")
    }

    @Test
    fun testNoneKept() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A"))

        D8MainDexListTask.MainDexListRunnable(
            D8MainDexListTask.MainDexListRunnable.Params(
                listOf(),
                listOf(inputJar),
                getBootClasspath(),
                null,
                output,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE
            )
        ).run()

        assertThat(output.readLines()).isEmpty()
    }

    @Test
    fun testThrowsIfDuplicateClasses() {
        val output = tmpDir.newFile()

        val inputJar1 = tmpDir.root.resolve("input1.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar1.toPath(), listOf("test/A"))

        val inputJar2 = tmpDir.root.resolve("input2.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar2.toPath(), listOf("test/A"))

        val exception = assertFailsWith(D8MainDexList.MainDexListException::class) {
            D8MainDexListTask.MainDexListRunnable(
                D8MainDexListTask.MainDexListRunnable.Params(
                    listOf(),
                    listOf(inputJar1, inputJar2),
                    getBootClasspath(),
                    null,
                    output,
                    SyncOptions.ErrorFormatMode.HUMAN_READABLE
                )
            ).run()
        }

        assertThat(exception.message).contains(ERROR_DUPLICATE)
        assertThat(exception.message).contains(ERROR_DUPLICATE_HELP_PAGE)
    }

    @Test
    fun testMultiReleaseClassesInDir() {
        val output = tmpDir.newFile()

        val inputDir = tmpDir.newFolder("input")
        TestInputsGenerator.dirWithEmptyClasses(inputDir.toPath(), listOf("test/A"))
        inputDir.resolve("META-INF/versions/9/module-info.class").also {
            it.parentFile.mkdirs()
            it.createNewFile()
        }

        D8MainDexListTask.MainDexListRunnable(
            D8MainDexListTask.MainDexListRunnable.Params(
                listOf(),
                listOf(inputDir),
                getBootClasspath(),
                null,
                output,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE
            )
        ).run()

        assertThat(output.readLines()).isEmpty()
    }

    @Test
    fun testMultiReleaseClassesInJar() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar").also { file ->
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("test/A.class"))
                it.write(TestClassesGenerator.emptyClass("test", "A"))
                it.closeEntry()
                it.putNextEntry(ZipEntry("META-INF/versions/9/module-info.class"))
                it.closeEntry()
            }
        }

        D8MainDexListTask.MainDexListRunnable(
            D8MainDexListTask.MainDexListRunnable.Params(
                listOf(),
                listOf(inputJar),
                getBootClasspath(),
                null,
                output,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE
            )
        ).run()

        assertThat(output.readLines()).isEmpty()
    }

    private fun getBootClasspath(): Collection<File> {
        return listOf(TestUtils.getPlatformFile("android.jar"))
    }
}