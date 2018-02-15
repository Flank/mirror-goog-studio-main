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

package com.android.builder.dexing

import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sanity test that make sure we can invoke R8 with some basic configurations.
 */
class R8ToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testClassesFromDir() {
        val proguardConfig = ProguardConfig(listOf(), null, null, listOf())
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath()
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val output = tmp.newFolder().toPath()
        runR8(listOf(classes), output, bootClasspath, toolConfig, proguardConfig, mainDexConfig)

        assertThat(getDexFileCount(output)).isEqualTo(1)
    }

    @Test
    fun testClassesFromJar() {
        val proguardConfig = ProguardConfig(listOf(), null, null, listOf())
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val output = tmp.newFolder().toPath()
        runR8(listOf(classes), output, bootClasspath, toolConfig, proguardConfig, mainDexConfig)
        assertThat(getDexFileCount(output)).isEqualTo(1)
    }

    @Test
    fun testMainDexList() {
        val proguardConfig = ProguardConfig(listOf(), null, null, listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val mainDexList = tmp.newFile().toPath()
        Files.write(mainDexList, listOf("test/A.class"))
        val mainDexConfig = MainDexListConfig(listOf(), listOf(mainDexList))

        val output = tmp.newFolder().toPath()
        runR8(listOf(classes), output, bootClasspath, toolConfig, proguardConfig, mainDexConfig)
        assertThat(getDexFileCount(output)).isEqualTo(2)
    }

    @Test
    fun testMainDexListRules() {
        val proguardConfig = ProguardConfig(listOf(), null, null, listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val mainDexRules = tmp.newFile().toPath()
        Files.write(mainDexRules, listOf("-keep class test.A"))
        val mainDexConfig = MainDexListConfig(listOf(mainDexRules), listOf())

        val output = tmp.newFolder().toPath()
        runR8(listOf(classes), output, bootClasspath, toolConfig, proguardConfig, mainDexConfig)
        assertThat(getDexFileCount(output)).isEqualTo(2)
    }

    @Test
    fun testKeepRules() {
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = false,
                disableDesugaring = true,
                disableMinification = false,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val proguardRules = tmp.newFile().toPath()
        Files.write(proguardRules, listOf("-keep class test.A"))
        val proguardConfig = ProguardConfig(listOf(proguardRules), null, null, listOf())

        val output = tmp.newFolder().toPath()
        runR8(listOf(classes), output, bootClasspath, toolConfig, proguardConfig, mainDexConfig)
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThatDex(output.resolve("classes.dex").toFile()).containsClass("Ltest/A;")
        assertThatDex(output.resolve("classes.dex").toFile()).doesNotContainClasses("Ltest/B;")
    }

    @Test
    fun testProguardMapping() {
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val proguardInputMapping = tmp.newFile().toPath()
        Files.write(proguardInputMapping, listOf("test.A -> a.Changed:"))
        val proguardConfig =
                ProguardConfig(
                        listOf(),
                        tmp.root.toPath().resolve("mapping.txt"),
                        proguardInputMapping,
                        listOf()
                )

        val output = tmp.newFolder().toPath()
        runR8(listOf(classes), output, bootClasspath, toolConfig, proguardConfig, mainDexConfig)
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThatDex(output.resolve("classes.dex").toFile()).containsClass("La/Changed;")
        assertThat(Files.exists(proguardConfig.proguardMapOutput)).isTrue()
    }

    private fun getDexFileCount(dir: Path): Long =
        Files.list(dir).filter { it.toString().endsWith(".dex") }.count()

    companion object {
        val bootClasspath = listOf(TestUtils.getPlatformFile("android.jar").toPath())
    }
}