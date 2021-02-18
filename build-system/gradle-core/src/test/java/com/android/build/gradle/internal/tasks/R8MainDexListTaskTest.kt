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
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.ProguardOutputFiles
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.DexSubject.assertThatDex
import com.android.testutils.truth.PathSubject.assertThat
import org.gradle.api.file.RegularFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Path

/**
 * Testing scenarios for R8 task processing class files which outputs DEX.
 */
class R8MainDexListTaskTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var outputDir: Path
    private lateinit var outputProguard: RegularFile

    @Before
    fun setUp() {
        outputDir = tmp.newFolder().toPath()
        outputProguard = Mockito.mock(RegularFile::class.java)
    }

    @Test
    fun testMainDexRules() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java)
        )

        val mainDexRuleFile = tmp.newFile()
        mainDexRuleFile.printWriter().use {
            it.println("-keep class " + Animal::class.java.name)
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            mainDexRulesFiles = listOf(mainDexRuleFile),
            minSdkVersion = 19,
            r8Keep = "class **",
            outputDir = outputDir,
            proguardOutputDir = tmp.root,
            featureJavaResourceJars = listOf(),
            featureJavaResourceOutputDir = null
        )

        val mainDex = Dex(outputDir.resolve("main").resolve("classes.dex"))
        assertThat(mainDex)
            .containsExactlyClassesIn(
                listOf(
                    Type.getDescriptor(CarbonForm::class.java),
                    Type.getDescriptor(Animal::class.java)
                )
            )

        val secondaryDex = Dex(outputDir.resolve("main").resolve("classes2.dex"))
        assertThat(secondaryDex).containsExactlyClassesIn(listOf(Type.getDescriptor(Toy::class.java)))
    }

    @Test
    fun testMonoDex() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java)
        )

        val mainDexRuleFile = tmp.newFile()
        mainDexRuleFile.printWriter().use {
            it.println("-keep class " + CarbonForm::class.java.name)
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            mainDexRulesFiles = listOf(mainDexRuleFile),
            minSdkVersion = 19,
            dexingType = DexingType.MONO_DEX,
            r8Keep = "class **",
            outputDir = outputDir,
            mappingFile = tmp.newFolder("mapping"),
            proguardOutputDir = tmp.root,
            featureJavaResourceJars = listOf(),
            featureJavaResourceOutputDir = null
        )

        assertThatDex(outputDir.resolve("main/classes.dex").toFile())
            .containsExactlyClassesIn(
                listOf(
                    Type.getDescriptor(CarbonForm::class.java),
                    Type.getDescriptor(Animal::class.java),
                    Type.getDescriptor(Toy::class.java)
                )
            )
        assertThat(outputDir.resolve("main/classes2.dex")).doesNotExist()
    }
}

fun runR8(
    classes: List<File>,
    resources: List<File>,
    referencedInputs: List<File> = listOf(),
    mainDexRulesFiles: List<File> = listOf(),
    minSdkVersion: Int = 21,
    dexingType: DexingType = DexingType.LEGACY_MULTIDEX,
    r8Keep: String? = null,
    outputDir: Path,
    mappingFile: File = outputDir.resolve("mapping.txt").toFile(),
    proguardOutputDir: File,
    featureClassJars: List<File> = listOf(),
    featureJavaResourceJars: List<File>,
    featureDexDir: File? = null,
    featureJavaResourceOutputDir: File?
) {


    val proguardConfigurations: MutableList<String> = mutableListOf(
        "-ignorewarnings")

    r8Keep?.let { proguardConfigurations.add("-keep $it") }


    val output: File = outputDir.resolve("main").toFile()

    R8Task.shrink(
        bootClasspath = listOf(TestUtils.resolvePlatformPath("android.jar").toFile()),
        minSdkVersion = minSdkVersion,
        isDebuggable = true,
        enableDesugaring = false,
        disableTreeShaking = false,
        disableMinification = true,
        mainDexListFiles = listOf(),
        mainDexRulesFiles = mainDexRulesFiles,
        inputProguardMapping = null,
        proguardConfigurationFiles = listOf(),
        proguardConfigurations = proguardConfigurations,
        variantType = VariantTypeImpl.BASE_APK,
        messageReceiver = NoOpMessageReceiver(),
        dexingType = dexingType,
        useFullR8 = false,
        referencedInputs = referencedInputs,
        classes = classes,
        resources = resources,
        proguardOutputFiles =
            ProguardOutputFiles(
                mappingFile.toPath(),
                proguardOutputDir.resolve("seeds.txt").toPath(),
                proguardOutputDir.resolve("usage.txt").toPath(),
                proguardOutputDir.resolve("configuration.txt").toPath(),
                proguardOutputDir.resolve("missing_rules.txt").toPath(),
            ),
        output = output,
        outputResources = outputDir.resolve("java_res.jar").toFile(),
        mainDexListOutput = null,
        featureClassJars = featureClassJars,
        featureJavaResourceJars = featureJavaResourceJars,
        featureDexDir = featureDexDir,
        featureJavaResourceOutputDir = featureJavaResourceOutputDir,
        libConfiguration = null,
        outputKeepRulesDir = null
    )
}
