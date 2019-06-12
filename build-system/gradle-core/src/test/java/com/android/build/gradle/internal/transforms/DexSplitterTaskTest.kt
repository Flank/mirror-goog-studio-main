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

package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.Context
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.DexSplitterTask
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
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
    private lateinit var r8Context: Context
    private lateinit var r8OutputProvider: TransformOutputProvider
    private lateinit var r8OutputProviderDir: File
    private lateinit var dexSplitterContext: Context
    private lateinit var dexSplitterBaseOutputDir: File
    private lateinit var dexSplitterFeatureOutputDir: File
    private lateinit var baseClasses: File
    private lateinit var featureClasses: File

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        r8OutputProviderDir = tmp.newFolder()
        r8OutputProvider = TestTransformOutputProvider(r8OutputProviderDir.toPath())
        r8Context = Mockito.mock(Context::class.java)
        dexSplitterContext = Mockito.mock(Context::class.java)
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
        val r8Dex = getDex(r8OutputProviderDir.toPath())
        assertThat(r8Dex).containsClasses(
            "Lbase/A;",
            "Lbase/B;",
            "Lfeature/A;",
            "Lfeature/B;")

        runDexSplitter(
            File(r8OutputProviderDir, "main"),
            setOf(featureClasses),
            baseClasses)

        checkDexSplitterOutputs()
    }

    @Test
    fun testNonExistentMappingFile() {

        // We run R8 first to generate dex file from jar files.
        runR8(listOf(baseClasses, featureClasses), "class **")

        // Check that r8 ran as expected before running dexSplitter
        val r8Dex = getDex(r8OutputProviderDir.toPath())
        assertThat(r8Dex).containsClasses(
            "Lbase/A;",
            "Lbase/B;",
            "Lfeature/A;",
            "Lfeature/B;")

        runDexSplitter(
            File(r8OutputProviderDir, "main"),
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
            File(r8OutputProviderDir, "main"),
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
        val jarInputs =
            jars.asSequence().map {
                TransformTestHelper.singleJarBuilder(it).setContentTypes(RESOURCES, CLASSES).build()
            }.toSet()
        val r8Invocation =
                TransformTestHelper
                        .invocationBuilder()
                        .setInputs(jarInputs)
                        .setContext(this.r8Context)
                        .setTransformOutputProvider(r8OutputProvider)
                        .build()

        val r8Transform = getR8Transform()
        r8Transform.setActions(PostprocessingFeatures(false, false, false))
        r8Keep?.let { r8Transform.keep(it) }

        r8Transform.transform(r8Invocation)
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

    private fun getR8Transform(
        mainDexRulesFiles: FileCollection = FakeFileCollection(),
        java8Support: VariantScope.Java8LangSupport = VariantScope.Java8LangSupport.UNUSED,
        proguardRulesFiles: ConfigurableFileCollection = FakeConfigurableFileCollection(),
        outputProguardMapping: File = tmp.newFile(),
        disableMinification: Boolean = true,
        minSdkVersion: Int = 21
    ): R8Transform {
        val classpath = FakeFileCollection(TestUtils.getPlatformFile("android.jar"))
        val transform= R8Transform(
                bootClasspath = classpath,
                minSdkVersion = minSdkVersion,
                isDebuggable = true,
                java8Support = java8Support,
                disableTreeShaking = false,
                disableMinification = disableMinification,
                mainDexListFiles = FakeFileCollection(),
                mainDexRulesFiles = mainDexRulesFiles,
                inputProguardMapping = FakeFileCollection(),
                proguardConfigurationFiles = proguardRulesFiles,
                variantType = VariantTypeImpl.BASE_APK,
                includeFeaturesInScopes = false,
                dexingType = DexingType.NATIVE_MULTIDEX,
                messageReceiver= NoOpMessageReceiver()
        )
        val regularFileMock = Mockito.mock(RegularFile::class.java)
        `when`(regularFileMock.asFile).thenReturn(outputProguardMapping)
        transform.setOutputFile(FakeGradleProperty<RegularFile>(regularFileMock))

        return transform
    }
}