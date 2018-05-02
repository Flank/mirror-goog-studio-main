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
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.builder.core.VariantTypeImpl
import com.android.ide.common.blame.MessageReceiver
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.toList

/**
 * Testing the basic scenarios for R8 transform processing class files.
 */
class R8TransformTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var outputProvider: TransformOutputProvider
    private lateinit var outputDir: Path

    @Before
    fun setUp() {
        outputDir = tmp.newFolder().toPath()
        outputProvider = TestTransformOutputProvider(outputDir)
        context = Mockito.mock(Context::class.java)
    }

    @Test
    fun testClassesDexed() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile()).build()
        val invocation =
                TransformTestHelper
                    .invocationBuilder()
                    .addInput(jarInput)
                    .setContext(this.context)
                    .setTransformOutputProvider(outputProvider)
                    .build()

        val transform = getTransform()
        transform.keep("class **")

        transform.transform(invocation)

        val dex = getDex()
        assertThat(dex).containsClass("Ltest/A;")
        assertThat(dex).containsClass("Ltest/B;")
    }

    @Test
    fun testLibraryClassesPassedToR8() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(classes, listOf(Animal::class.java))
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile()).build()

        val libraryClasses = tmp.root.toPath().resolve("library_classes.jar")
        TestInputsGenerator.pathWithClasses(libraryClasses, listOf(CarbonForm::class.java))
        val jarLibrary = TransformTestHelper.singleJarBuilder(libraryClasses.toFile()).build()

        val invocation =
                TransformTestHelper
                    .invocationBuilder()
                    .addInput(jarInput)
                    .addReferenceInput(jarLibrary)
                    .setContext(this.context)
                    .setTransformOutputProvider(outputProvider)
                    .build()

        val transform = getTransform()
        transform.keep("class **")

        transform.transform(invocation)

        val dex = getDex()
        assertThat(dex).containsClass(Type.getDescriptor(Animal::class.java))
        assertThat(dex).doesNotContainClasses(Type.getDescriptor(CarbonForm::class.java))
    }

    @Test
    fun testMainDexRules() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
                classes,
                listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java)
        )
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile()).build()

        val invocation =
                TransformTestHelper
                    .invocationBuilder()
                    .addInput(jarInput)
                    .setContext(this.context)
                    .setTransformOutputProvider(outputProvider)
                    .build()

        val mainDexRuleFile = tmp.newFile()
        mainDexRuleFile.printWriter().use {
            it.println("-keep class " + Animal::class.java.name)
        }
        val mainDexRulesFileCollection = mockFileCollection(setOf(mainDexRuleFile))

        val transform =
                getTransform(mainDexRulesFiles = mainDexRulesFileCollection, minSdkVersion = 19)
        transform.keep("class **")

        transform.transform(invocation)
        val mainDex = Dex(outputDir.resolve("main").resolve("classes.dex"))
        assertThat(mainDex)
            .containsExactlyClassesIn(
                listOf(
                        Type.getDescriptor(CarbonForm::class.java),
                        Type.getDescriptor(Animal::class.java)))

        val secondaryDex = Dex(outputDir.resolve("main").resolve("classes2.dex"))
        assertThat(secondaryDex).containsExactlyClassesIn(listOf(Type.getDescriptor(Toy::class.java)))
    }

    @Test
    fun testDesugaring() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
                classes,
                listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile()).build()

        val invocation =
                TransformTestHelper
                    .invocationBuilder()
                    .addInput(jarInput)
                    .setContext(this.context)
                    .setTransformOutputProvider(outputProvider)
                    .build()
        val transform = getTransform(java8Support = VariantScope.Java8LangSupport.R8)
        transform.keep("class **")

        transform.transform(invocation)

        val dex = getDex()
        assertThat(dex).containsClass(Type.getDescriptor(Animal::class.java))
        assertThat(dex).containsClass(Type.getDescriptor(CarbonForm::class.java))
        assertThat(dex).containsClass(Type.getDescriptor(Cat::class.java))
        assertThat(dex).containsClass(Type.getDescriptor(Toy::class.java))
        assertThat(dex.version).isEqualTo(35)
    }

    @Test
    fun testProguardConfiguration() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
                classes,
                listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile()).build()

        val invocation =
                TransformTestHelper
                    .invocationBuilder()
                    .addInput(jarInput)
                    .setContext(this.context)
                    .setTransformOutputProvider(outputProvider)
                    .build()

        val proguardConfiguration = tmp.newFile()
        proguardConfiguration.printWriter().use {
            it.println("-keep class " + Cat::class.java.name + " {*;}")
        }
        val proguardConfigurationFileCollection = mockFileCollection(setOf(proguardConfiguration))
        val transform = getTransform(
                java8Support = VariantScope.Java8LangSupport.R8,
                proguardRulesFiles = proguardConfigurationFileCollection)

        transform.transform(invocation)

        val dex = getDex()
        assertThat(dex).containsClass(Type.getDescriptor(Animal::class.java))
        assertThat(dex).containsClass(Type.getDescriptor(CarbonForm::class.java))
        assertThat(dex).containsClass(Type.getDescriptor(Cat::class.java))
        assertThat(dex).containsClass(Type.getDescriptor(Toy::class.java))
        assertThat(dex.version).isEqualTo(35)

        val transform2 = getTransform(java8Support = VariantScope.Java8LangSupport.R8)
        transform2.keep("class " + CarbonForm::class.java.name)

        transform2.transform(invocation)

        val dex2 = getDex()
        assertThat(dex2).containsClass(Type.getDescriptor(CarbonForm::class.java))
        assertThat(dex2).doesNotContainClasses(Type.getDescriptor(Animal::class.java))
        assertThat(dex2).doesNotContainClasses(Type.getDescriptor(Cat::class.java))
        assertThat(dex2).doesNotContainClasses(Type.getDescriptor(Toy::class.java))
        assertThat(dex2.version).isEqualTo(35)
    }

    @Test
    fun testNonAsciiClassName() {
        // test for http://b.android.com/221057
        val nonAsciiName = "com/android/tests/basic/Ubicación"
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf(nonAsciiName))
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile()).build()

        val invocation =
                TransformTestHelper
                    .invocationBuilder()
                    .addInput(jarInput)
                    .setContext(this.context)
                    .setTransformOutputProvider(outputProvider)
                    .build()
        val transform = getTransform()
        transform.keep("class " + nonAsciiName.replace("/", "."))

        transform.transform(invocation)

        val dex = getDex()
        assertThat(dex).containsClass("L$nonAsciiName;")
        assertThat(dex.version).isEqualTo(35)
    }

    @Test
    fun testMappingProduced() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A"))
        val jarInput = TransformTestHelper.singleJarBuilder(classes.toFile()).build()

        val invocation =
                TransformTestHelper
                    .invocationBuilder()
                    .addInput(jarInput)
                    .setContext(this.context)
                    .setTransformOutputProvider(outputProvider)
                    .build()
        val outputMapping = tmp.newFile()
        val transform =
                getTransform(disableMinification = false, outputProguardMapping = outputMapping)
        transform.keep("class **")

        transform.transform(invocation)
        PathSubject.assertThat(outputMapping).exists()
    }

    @Test
    fun testJavaResourcesCopied() {
        val resources = tmp.root.toPath().resolve("java_res.jar")
        ZipOutputStream(resources.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata1.txt"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("metadata2.txt"))
            zip.closeEntry()
        }
        val resInput =
            TransformTestHelper.singleJarBuilder(resources.toFile())
                .setContentTypes(RESOURCES)
                .build()

        val mixedResources = tmp.root.toPath().resolve("classes_and_res.jar")
        ZipOutputStream(mixedResources.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data/metadata.txt"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("a/b/c/metadata.txt"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
        }
        val jarInput =
            TransformTestHelper.singleJarBuilder(mixedResources.toFile())
                .setContentTypes(CLASSES, RESOURCES)
                .build()

        val invocation =
            TransformTestHelper
                .invocationBuilder()
                .setInputs(resInput, jarInput)
                .setContext(this.context)
                .setTransformOutputProvider(outputProvider)
                .build()

        val transform = getTransform()
        transform.keep("class **")
        transform.transform(invocation)

        val dex = getDex()
        assertThat(dex).containsClass("Ltest/A;")

        val resourcesCopied =
            Files.walk(outputDir).filter { it.toString().endsWith(".jar") }.toList().single()
        assertThat(Zip(resourcesCopied)).containsFileWithContent("metadata1.txt", "")
        assertThat(Zip(resourcesCopied)).containsFileWithContent("metadata2.txt", "")
        assertThat(Zip(resourcesCopied)).containsFileWithContent("data/metadata.txt", "")
        assertThat(Zip(resourcesCopied)).containsFileWithContent("a/b/c//metadata.txt", "")
        assertThat(Zip(resourcesCopied)).doesNotContain("test/A.class")
    }

    private fun getDex(): Dex {
        val dexFiles = Files.walk(outputDir).filter { it.toString().endsWith(".dex") }.toList()
        return Dex(dexFiles.single())
    }

    private fun getTransform(
        mainDexRulesFiles: FileCollection = emptyFileCollection,
        java8Support: VariantScope.Java8LangSupport = VariantScope.Java8LangSupport.UNUSED,
        proguardRulesFiles: ConfigurableFileCollection = emptyFileCollection,
        typesToOutput: MutableSet<QualifiedContent.ContentType> = TransformManager.CONTENT_DEX,
        outputProguardMapping: File = tmp.newFile(),
        disableMinification: Boolean = true,
        minSdkVersion: Int = 21
    ): R8Transform {
        return R8Transform(
                bootClasspath = lazy { bootClasspath },
                minSdkVersion = minSdkVersion,
                isDebuggable = true,
                java8Support = java8Support,
                disableTreeShaking = false,
                disableMinification = disableMinification,
                mainDexListFiles = emptyFileCollection,
                mainDexRulesFiles = mainDexRulesFiles,
                inputProguardMapping = emptyFileCollection,
                outputProguardMapping = outputProguardMapping,
                typesToOutput = typesToOutput,
                proguardConfigurationFiles = proguardRulesFiles,
                variantType = VariantTypeImpl.BASE_APK,
                includeFeaturesInScopes = false,
                messageReceiver = NoOpMessageReceiver()
        )
    }

    companion object {
        val bootClasspath = listOf(TestUtils.getPlatformFile("android.jar"))
        val emptyFileCollection: ConfigurableFileCollection = mockFileCollection()

        init {
            Mockito.`when`(emptyFileCollection.isEmpty).thenReturn(true)
            Mockito.`when`(emptyFileCollection.files).thenReturn(setOf())
        }

        fun mockFileCollection(files: Set<File> = setOf()): ConfigurableFileCollection {
            val collection = Mockito.mock(ConfigurableFileCollection::class.java)
            Mockito.`when`(collection.isEmpty).thenReturn(files.isEmpty())
            Mockito.`when`(collection.files).thenReturn(files)
            return collection
        }
    }
}