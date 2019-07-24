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

import com.android.builder.core.NoOpMessageReceiver
import com.android.ide.common.blame.MessageReceiver
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.android.testutils.truth.MoreTruth.assertThatZip
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.fail

/**
 * Sanity test that make sure we can invoke R8 with some basic configurations.
 */
class R8ToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testClassesFromDir() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), null)
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
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(listOf(classes), output, listOf(), javaRes, bootClasspath, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())

        assertThat(getDexFileCount(output)).isEqualTo(1)
    }

    @Test
    fun testClassesFromJar() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), null)
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
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(listOf(classes), output, listOf(), javaRes, bootClasspath, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())
        assertThat(getDexFileCount(output)).isEqualTo(1)
    }

    @Test
    fun testClassesAndResources() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), null)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = true,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))
        Files.createFile(classes.resolve("res.txt"))

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            listOf(classes),
            javaRes,
            bootClasspath,
            toolConfig,
            proguardConfig,
            mainDexConfig,
            NoOpMessageReceiver()
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThatZip(javaRes.toFile()).contains("res.txt")

        // check Java resources are compressed
        ZipFile(javaRes.toFile()).use { zip ->
            for (entry in zip.entries()) {
                assertThat(entry.method).named("entry is compressed").isEqualTo(ZipEntry.DEFLATED)
            }
        }
    }

    @Test
    fun testClassesAndResources_fullR8() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), null)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = true,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))
        Files.createFile(classes.resolve("res.txt"))

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            listOf(classes),
            javaRes,
            bootClasspath,
            toolConfig,
            proguardConfig,
            mainDexConfig,
            NoOpMessageReceiver(),
            true
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThatZip(javaRes.toFile()).contains("res.txt")
    }

    @Test
    fun testMainDexList() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), null)
        val toolConfig = ToolConfig(
                minSdkVersion = 19,
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
        val mainDexConfig = MainDexListConfig(
                mainDexRulesFiles = listOf(),
                mainDexListFiles = listOf(mainDexList),
                mainDexRules = listOf())

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(listOf(classes), output, listOf(), javaRes, bootClasspath, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())
        assertThat(getDexFileCount(output)).isEqualTo(2)
    }

    @Test
    fun testMainDexListRules() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), null)
        val toolConfig = ToolConfig(
                minSdkVersion = 19,
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
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(listOf(classes), output, listOf(), javaRes, bootClasspath, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())
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
        val proguardConfig = ProguardConfig(listOf(proguardRules), null, listOf(), null)

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(listOf(classes), output, listOf(), javaRes, bootClasspath, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())
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

        val testClasses = tmp.newFolder().toPath().resolve("testClasses.jar")
        TestInputsGenerator.pathWithClasses(
                testClasses,
                listOf(ExampleClasses.TestClass::class.java))

        val programClasses = tmp.newFolder().toPath().resolve("programClasses.jar")
        TestInputsGenerator.pathWithClasses(
                programClasses,
                listOf(ExampleClasses::class.java, ExampleClasses.ProgramClass::class.java))

        val libraries = mutableListOf(programClasses)
        libraries.addAll(bootClasspath)

        val proguardInputMapping = tmp.newFile("space in name.txt").toPath()
        Files.write(
                proguardInputMapping,
                listOf(
                        "com.android.builder.dexing.ExampleClasses\$ProgramClass -> foo.Bar:",
                        "  1:1:void method():42:42 -> baz"))
        val proguardConfig =
                ProguardConfig(
                        listOf(),
                        proguardInputMapping,
                        listOf(),
                        ProguardOutputFiles(
                            tmp.root.toPath().resolve("mapping.txt"),
                            tmp.root.toPath().resolve("seeds.txt"),
                            tmp.root.toPath().resolve("usage.txt")
                        )
                )

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(listOf(testClasses), output, listOf(), javaRes, libraries, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThatDex(output.resolve("classes.dex").toFile())
            .containsClass("Lcom/android/builder/dexing/ExampleClasses\$TestClass;")
            .that()
            .hasMethodThatInvokes("test", "Lfoo/Bar;->baz()V")
        assertThat(Files.exists(proguardConfig.proguardOutputFiles?.proguardMapOutput)).isTrue()
    }

    @Test
    fun testUsageAndSeeds() {
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
        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()

        val proguardSeedsOutput = tmp.root.toPath().resolve("seeds.txt")
        val proguardUsageOutput = tmp.root.toPath().resolve("usage.txt")
        val proguardConfig =
            ProguardConfig(
                listOf(),
                null,
                listOf(),
                ProguardOutputFiles(
                    tmp.root.toPath().resolve("mapping.txt"),
                    proguardSeedsOutput,
                    proguardUsageOutput
                )
            )
        runR8(listOf(classes), output, listOf(), javaRes, bootClasspath, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())
        assertThat(Files.exists(proguardSeedsOutput)).isTrue()
        assertThat(Files.exists(proguardUsageOutput)).isTrue()
    }

    @Test
    fun testErrorReporting() {
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = false,
            disableDesugaring = true,
            disableMinification = false,
            r8OutputType = R8OutputType.DEX
        )

        val proguardRules = tmp.newFile().toPath()
        Files.write(proguardRules, listOf("wrongRuleExample"))
        val proguardConfig = ProguardConfig(listOf(proguardRules), null, listOf(), null)

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        val messages = mutableListOf<String>()
        val toolNameTags = mutableListOf<String>()

        try {
            runR8(
                listOf(),
                output,
                listOf(),
                javaRes,
                bootClasspath,
                toolConfig,
                proguardConfig,
                mainDexConfig,
                MessageReceiver { message ->
                    messages.add(message.text)
                    toolNameTags.add(message.toolName!!)
                }
            )
            fail("Parsing proguard configuration should fail.")
        } catch (e: Throwable){
            assertThat(messages.single()).contains("Expected char '-' at")
            assertThat(messages.single()).contains("1:1")
            assertThat(messages.single()).contains("wrongRuleExample")
            assertThat(toolNameTags).containsExactly("R8")
        }
    }

    @Test
    fun testMultiReleaseFromDir() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), null)
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
        classes.resolve("META-INF/versions/9/test/C.class").also {
            it.parent.toFile().mkdirs()
            it.toFile().writeText("malformed class file")
        }

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(listOf(classes), output, listOf(), javaRes, bootClasspath, toolConfig, proguardConfig, mainDexConfig, NoOpMessageReceiver())

        assertThatDex(output.resolve("classes.dex").toFile())
            .containsExactlyClassesIn(listOf("Ltest/A;", "Ltest/B;"))
    }

    private fun getDexFileCount(dir: Path): Long =
        Files.list(dir).filter { it.toString().endsWith(".dex") }.count()

    companion object {
        val bootClasspath = listOf(TestUtils.getPlatformFile("android.jar").toPath())
    }
}