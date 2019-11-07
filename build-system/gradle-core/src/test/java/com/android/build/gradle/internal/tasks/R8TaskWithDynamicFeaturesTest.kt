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

import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.Dog
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.builder.dexing.DexingType
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.Dex
import com.android.testutils.truth.MoreTruth.assertThat
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
 * Testing legacy multidex and monodex for [R8Task] with dynamic features. Similar to
 * [R8MainDexListTaskTest], but with dynamic features.
 */
class R8TaskWithDynamicFeaturesTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var classesJar: Path
    private lateinit var feature1Jar: Path
    private lateinit var feature2Jar: Path
    private lateinit var outputDir: Path
    private lateinit var featureDexDir: File
    private lateinit var outputProguard: RegularFile

    @Before
    fun setUp() {
        classesJar = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classesJar,
            listOf(
                Animal::class.java,
                CarbonForm::class.java,
                Toy::class.java
            )
        )
        feature1Jar = tmp.root.toPath().resolve("feature1.jar")
        TestInputsGenerator.pathWithClasses(
            feature1Jar,
            listOf(
                Cat::class.java
            )
        )
        feature2Jar = tmp.root.toPath().resolve("feature2.jar")
        TestInputsGenerator.pathWithClasses(
            feature2Jar,
            listOf(
                Dog::class.java
            )
        )
        outputDir = tmp.newFolder().toPath()
        featureDexDir = tmp.newFolder()
        outputProguard = Mockito.mock(RegularFile::class.java)
    }

    @Test
    fun testMainDexRules() {
        val mainDexRuleFile = tmp.newFile()
        mainDexRuleFile.printWriter().use {
            it.println("-keep class " + Animal::class.java.name)
        }

        runR8(
            classes = listOf(classesJar.toFile()),
            resources = listOf(),
            mainDexRulesFiles = listOf(mainDexRuleFile),
            minSdkVersion = 19,
            r8Keep = "class **",
            outputDir = outputDir,
            proguardOutputDir = tmp.root,
            featureDexDir = featureDexDir,
            featureJars = listOf(feature1Jar.toFile(), feature2Jar.toFile())
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
        assertThat(secondaryDex)
            .containsExactlyClassesIn(listOf(Type.getDescriptor(Toy::class.java)))

        val feature1Dex = Dex(featureDexDir.resolve("feature1").resolve("classes.dex"))
        assertThat(feature1Dex)
            .containsExactlyClassesIn(listOf(Type.getDescriptor(Cat::class.java)))

        val feature2Dex = Dex(featureDexDir.resolve("feature2").resolve("classes.dex"))
        assertThat(feature2Dex)
            .containsExactlyClassesIn(listOf(Type.getDescriptor(Dog::class.java)))
    }

    @Test
    fun testMonoDex() {
        val mainDexRuleFile = tmp.newFile()
        mainDexRuleFile.printWriter().use {
            it.println("-keep class " + CarbonForm::class.java.name)
        }

        runR8(
            classes = listOf(classesJar.toFile()),
            resources = listOf(),
            mainDexRulesFiles = listOf(mainDexRuleFile),
            minSdkVersion = 19,
            dexingType = DexingType.MONO_DEX,
            r8Keep = "class **",
            outputDir = outputDir,
            mappingFile = tmp.newFolder("mapping"),
            proguardOutputDir = tmp.root,
            featureDexDir = featureDexDir,
            featureJars = listOf(feature1Jar.toFile(), feature2Jar.toFile())
        )

        val mainDex = Dex(outputDir.resolve("main").resolve("classes.dex"))
        assertThat(mainDex)
            .containsExactlyClassesIn(
                listOf(
                    Type.getDescriptor(CarbonForm::class.java),
                    Type.getDescriptor(Animal::class.java),
                    Type.getDescriptor(Toy::class.java)
                )
            )

        assertThat(outputDir.resolve("main").resolve("classes2.dex")).doesNotExist()

        val feature1Dex = Dex(featureDexDir.resolve("feature1").resolve("classes.dex"))
        assertThat(feature1Dex)
            .containsExactlyClassesIn(listOf(Type.getDescriptor(Cat::class.java)))

        val feature2Dex = Dex(featureDexDir.resolve("feature2").resolve("classes.dex"))
        assertThat(feature2Dex)
            .containsExactlyClassesIn(listOf(Type.getDescriptor(Dog::class.java)))
    }
}
