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

import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.ProguardOutputFiles
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.toList

/**
 * Testing the basic scenarios for DexSplitterTask.
 */
class DexSplitterTaskTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var shrunkDexDir: File
    private lateinit var dexSplitterBaseOutputDir: File
    private lateinit var dexSplitterFeatureOutputDir: File
    private lateinit var baseClasses: File
    private lateinit var featureClasses: File

    @Before
    fun setUp() {
        shrunkDexDir = tmp.newFolder("shrunkDex")
        dexSplitterBaseOutputDir = tmp.newFolder()
        dexSplitterFeatureOutputDir = tmp.newFolder()

        baseClasses = File(tmp.root, "base/base.jar")
        FileUtils.mkdirs(baseClasses.parentFile)
        TestInputsGenerator.jarWithEmptyClasses(baseClasses.toPath(), listOf("base/A", "base/B"))


        featureClasses = File(tmp.root, "feature-foo.jar")
        FileUtils.mkdirs(featureClasses.parentFile)
        TestInputsGenerator.jarWithEmptyClasses(
            featureClasses.toPath(), listOf("feature/A", "feature/B"))
    }

    @Test
    fun testBasic() {
        // We run R8 first to generate dex file from jar files.
        runR8(listOf(baseClasses, featureClasses), "class **")

        // Check that r8 ran as expected before running dexSplitter
        val r8Dex = getDex(shrunkDexDir.toPath())
        assertThat(r8Dex).containsClasses(
            "Lbase/A;",
            "Lbase/B;",
            "Lfeature/A;",
            "Lfeature/B;")

        runDexSplitter(
            shrunkDexDir,
            setOf(featureClasses),
            baseClasses)

        checkDexSplitterOutputs()
    }

    @Test
    fun testNonExistentMappingFile() {

        // We run R8 first to generate dex file from jar files.
        runR8(listOf(baseClasses, featureClasses), "class **")

        // Check that r8 ran as expected before running dexSplitter
        val r8Dex = getDex(shrunkDexDir.toPath())
        assertThat(r8Dex).containsClasses(
            "Lbase/A;",
            "Lbase/B;",
            "Lfeature/A;",
            "Lfeature/B;")

        runDexSplitter(
            shrunkDexDir,
            setOf(featureClasses),
            baseClasses,
            File("/path/to/nowhere"))

        checkDexSplitterOutputs()
    }

    @Test
    fun testMainDexListFile() {
        val numClasses = 0xFFFF/3 + 1
        val baseSplit = tmp.root.resolve("base-split.jar").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                repeat(numClasses) {
                    zip.putNextEntry(ZipEntry("test/A$it.class"))
                    val emptyClass = TestClassesGenerator.classWithEmptyMethods(
                        "A$it",
                        "foo:()V",
                        "foo2:()V")
                    zip.write(emptyClass)
                    zip.closeEntry()
                }
            }
        }
        runR8(listOf(baseSplit, featureClasses), "class ** { *; }")

        val primaryDex = listOf("test/A0", "test/A1", "test/A${numClasses - 1}")
        val primaryClasses = tmp.newFile("mainDexList.txt").also { file ->
            file.writeText(
                primaryDex.joinToString(separator = System.lineSeparator()) { "$it.class" }
            )
        }

        runDexSplitter(
            shrunkDexDir,
            setOf(featureClasses),
            baseSplit,
            mappingFileSrc = null,

            mainDexList = primaryClasses)

        assertThatDex(dexSplitterBaseOutputDir.resolve("classes.dex")).containsClassesIn(
            primaryDex.map { "L$it;" }
        )
    }

    private fun runDexSplitter(
        dexDir: File,
        featureJars: Set<File>,
        baseJar: File,
        mappingFileSrc: File? = null,
        mainDexList: File? = null
    ) {
        DexSplitterTask.splitDex(
            featureJars = featureJars,
            baseJar = baseJar,
            mappingFileSrc = mappingFileSrc,
            mainDexList = mainDexList,
            featureDexDir = dexSplitterFeatureOutputDir,
            baseDexDir =  dexSplitterBaseOutputDir,
            inputDirs = listOf(dexDir)
        )
    }

    private fun runR8(jars: List<File>, r8Keep: String? = null) {

        val proguardConfigurations: MutableList<String> = mutableListOf(
            "-ignorewarnings")

        r8Keep?.let { proguardConfigurations.add("-keep $it") }

        R8Task.shrink(
            bootClasspath = listOf(TestUtils.getPlatformFile("android.jar")),
            minSdkVersion = 21,
            isDebuggable = true,
            enableDesugaring = false,
            disableTreeShaking = false,
            disableMinification = true,
            mainDexListFiles = listOf(),
            mainDexRulesFiles = listOf(),
            inputProguardMapping = null,
            proguardConfigurationFiles = listOf(),
            proguardConfigurations = proguardConfigurations,
            variantType = VariantTypeImpl.BASE_APK,
            messageReceiver = NoOpMessageReceiver(),
            dexingType = DexingType.NATIVE_MULTIDEX,
            useFullR8 = false,
            referencedInputs = listOf(),
            classes = jars,
            resources = jars,
            proguardOutputFiles =
                ProguardOutputFiles(
                    tmp.newFile("mapping.txt").toPath(),
                    tmp.newFile("seeds.txt").toPath(),
                    tmp.newFile("usage.txt").toPath()),
            output = shrunkDexDir,
            outputResources = tmp.newFile("shrunkResources.jar"),
            mainDexListOutput = null,
            featureJars = listOf(),
            featureDexDir = null,
            libConfiguration = null,
            outputKeepRulesDir = null
        )
    }

    private fun checkDexSplitterOutputs() {
        val baseDex = getDex(dexSplitterBaseOutputDir.toPath())
        assertThat(baseDex).containsClasses("Lbase/A;", "Lbase/B;")
        assertThat(baseDex).doesNotContainClasses("Lfeature/A;", "Lfeature/B;")

        val featureDex = getDex(File(dexSplitterFeatureOutputDir, "feature-foo").toPath())
        assertThat(featureDex).containsClasses("Lfeature/A;", "Lfeature/B;")
        assertThat(featureDex).doesNotContainClasses("Lbase/A;", "Lbase/B;")

        Truth.assertThat(dexSplitterFeatureOutputDir.listFiles().map {it.name} )
            .doesNotContain("base")
    }

    private fun getDex(path: Path): Dex {
        val dexFiles = Files.walk(path).filter { it.toString().endsWith(".dex") }.toList()
        return Dex(dexFiles.single())
    }


}