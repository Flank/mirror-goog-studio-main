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

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.ClassWithDesugarApi
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.ProguardOutputFiles
import com.android.builder.dexing.R8OutputType
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.testutils.truth.DexSubject.assertThat

import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.Pair
import com.android.zipflinger.ZipArchive
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.RegularFile
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.streams.toList

/**
 * Testing the basic scenarios for R8 task processing class files. Both dex and class file
 * backend are tested.
 */
@RunWith(Parameterized::class)
class R8Test(val r8OutputType: R8OutputType) {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var outputDir: Path
    private lateinit var featureDexDir: File
    private lateinit var featureJavaResourceOutputDir: File
    private lateinit var outputProguard: RegularFile

    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun setups() = R8OutputType.values().map { arrayOf(it) }
    }

    @Before
    fun setUp() {
        outputDir = tmp.newFolder().toPath()
        featureDexDir = tmp.newFolder()
        featureJavaResourceOutputDir = tmp.newFolder()
        outputProguard = Mockito.mock(RegularFile::class.java)
    }

    @Test
    fun testClassesProcessed() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            r8Keep = "class **"
        )

        assertClassExists("test/A")
        assertClassExists("test/B")
    }

    @Test
    fun testOneClassIsKept_noExtractableRules() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(classes.toFile()),
            r8Keep = "class test.A"
        )

        assertClassExists("test/A")
        assertClassDoesNotExist("test/B")
    }

    // This test verifies that R8 task does NOT extract the rules from the jars if these jars
    // are not explicitly set as a source for rule extraction. This is done in order to control
    // the proguard rules, being able to filter out undesired ones in a non-command-line scenario
    @Test
    fun testOneClassIsKept_hasExtractableRulesInResources() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/proguard/rules.pro"))
            zip.write("-keep class test.B".toByteArray())
            zip.closeEntry()
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(classes.toFile()),
            r8Keep = "class test.A"
        )

        assertClassExists("test/A")
        assertClassDoesNotExist("test/B")
    }

    // This test verifies that R8 task does NOT extract the rules from the jars if these jars
    // are not explicitly set as a source for rule extraction. This is done in order to control
    // the proguard rules, being able to filter out undesired ones in a non-command-line scenario
    @Test
    fun testOneClassIsKept_hasExtractableRulesInClasses() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/proguard/rules.pro"))
            zip.write("-keep class test.B".toByteArray())
            zip.closeEntry()
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            r8Keep = "class test.A"
        )

        assertClassExists("test/A")
        assertClassDoesNotExist("test/B")
    }

    @Test
    fun testLibraryClassesPassedToR8() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(classes, listOf(Animal::class.java))

        val libraryClasses = tmp.root.toPath().resolve("library_classes.jar")
        TestInputsGenerator.pathWithClasses(libraryClasses, listOf(CarbonForm::class.java))

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            referencedInputs = listOf(libraryClasses.toFile()),
            r8Keep = "class **"
        )

        assertClassExists(Animal::class.java)
        assertClassDoesNotExist(CarbonForm::class.java)
    }

    @Test
    fun testDesugaring() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            disableTreeShaking = true,
            r8Keep = "class ***"
        )

        assertClassExists(Animal::class.java)
        assertClassExists(CarbonForm::class.java)
        assertClassExists(Cat::class.java)
        assertClassExists(Toy::class.java)

        if (r8OutputType == R8OutputType.DEX) {
            val dex = getDex()
            assertThat(dex.version).isEqualTo(35)
            // desugared classes are synthesized
            assertThat(dex.classes.size).isGreaterThan(4)
        } else {
            // no desugared classes are synthesized
            assertThat(Zip(outputDir.resolve("main.jar")).entries).hasSize(4)
        }
    }

    @Test
    fun testProguardConfiguration() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )

        val proguardConfiguration = tmp.newFile()
        proguardConfiguration.printWriter().use {
            it.println("-keep class " + Cat::class.java.name + " {*;}")
            it.println("-keep class " + Toy::class.java.name + " {*;}")
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            proguardRulesFiles = listOf(proguardConfiguration)
        )

        // Super classes are not explicitly kept and thus may be merged into Cat.
        assertClassExists(Cat::class.java)
        assertClassExists(Toy::class.java)
        // Check proguard compatibility mode
        assertClassHasAnnotations(Type.getInternalName(Toy::class.java))

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            r8Keep = "class " + CarbonForm::class.java.name
        )

        assertClassExists(CarbonForm::class.java)
        assertClassDoesNotExist(Animal::class.java)
        assertClassDoesNotExist(Cat::class.java)
        assertClassDoesNotExist(Toy::class.java)
    }

    @Test
    fun testProguardConfiguration_fullR8() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java, Cat::class.java, Toy::class.java)
        )

        val proguardConfiguration = tmp.newFile()
        proguardConfiguration.printWriter().use {
            it.println("-keep class " + Cat::class.java.name + " {*;}")
            it.println("-keep class " + Toy::class.java.name + " {*;}")
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            proguardRulesFiles = listOf(proguardConfiguration),
            useFullR8 = true
        )

        // Super classes are not explicitly kept and thus may be merged into Cat.
        assertClassExists(Cat::class.java)
        assertClassExists(Toy::class.java)
        // Check full R8 mode
        assertClassDoesNotHaveAnnotations(Type.getInternalName(Toy::class.java))

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            useFullR8 = true,
            r8Keep = "class " + CarbonForm::class.java.name
        )

        assertClassExists(CarbonForm::class.java)
        assertClassDoesNotExist(Animal::class.java)
        assertClassDoesNotExist(Cat::class.java)
        assertClassDoesNotExist(Toy::class.java)
    }

    @Test
    fun testProguardConfiguration_withDynamicFeatures() {
        Assume.assumeTrue(r8OutputType == R8OutputType.DEX)
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            listOf(Animal::class.java, CarbonForm::class.java)
        )

        val featureClassesJar = tmp.root.toPath().resolve("feature.jar")
        TestInputsGenerator.pathWithClasses(
            featureClassesJar,
            listOf(Cat::class.java, Toy::class.java)
        )

        val featureJavaResJar = tmp.newFolder().toPath().resolve("feature.jar")
        TestInputsGenerator.writeJarWithTextEntries(featureJavaResJar, Pair.of("foo.txt", "foo"))

        val proguardConfiguration = tmp.newFile()
        proguardConfiguration.printWriter().use {
            it.println("-keep class " + Cat::class.java.name + " {*;}")
            it.println("-keep class " + Toy::class.java.name + " {*;}")
            it.println("-keep class " + CarbonForm::class.java.name + " {*;}")
        }

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            proguardRulesFiles = listOf(proguardConfiguration),
            featureClassJars = listOf(featureClassesJar.toFile()),
            featureJavaResourceJars = listOf(featureJavaResJar.toFile()),
            featureDexDir = featureDexDir,
            featureJavaResourceOutputDir = featureJavaResourceOutputDir
        )

        // Animal class is not explicitly kept and thus may be merged into Cat.
        checkBaseAndFeatureDex(
            baseClasses = listOf(
                Type.getDescriptor(CarbonForm::class.java)
            ),
            featureClasses = listOf(
                Type.getDescriptor(Cat::class.java),
                "L" + Type.getInternalName(Cat::class.java) + "\$\$ExternalSyntheticLambda0;",
                Type.getDescriptor(Toy::class.java)
            ),
            featureName = "feature"
        )

        // Check proguard compatibility mode
        assertThat(Dex(featureDexDir.resolve("feature/classes.dex")))
            .containsClass(Type.getDescriptor(Toy::class.java))
            .that()
            .hasAnnotations()

        // Check feature java resource output
        val featureJavaResOutput = featureJavaResourceOutputDir.resolve("feature.jar")
        assertThat(featureJavaResOutput).exists()
        ZipArchive(featureJavaResOutput.toPath()).use {
            assertThat(it.listEntries()).containsExactly("foo.txt")
        }

        // run again in full R8 mode
        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            proguardRulesFiles = listOf(proguardConfiguration),
            featureClassJars = listOf(featureClassesJar.toFile()),
            featureJavaResourceJars = listOf(featureJavaResJar.toFile()),
            featureDexDir = featureDexDir,
            featureJavaResourceOutputDir = featureJavaResourceOutputDir,
            useFullR8 = true
        )

        // Animal class is not explicitly kept and thus may be merged into Cat.
        checkBaseAndFeatureDex(
            baseClasses = listOf(
                Type.getDescriptor(CarbonForm::class.java)
            ),
            featureClasses = listOf(
                Type.getDescriptor(Cat::class.java),
                "L" + Type.getInternalName(Cat::class.java) + "\$\$ExternalSyntheticLambda0;",
                Type.getDescriptor(Toy::class.java)
            ),
            featureName = "feature"
        )

        // Check full R8 mode
        assertThat(Dex(featureDexDir.resolve("feature/classes.dex")))
            .containsClass("L${Type.getInternalName(Toy::class.java)};")
            .that()
            .doesNotHaveAnnotations()

        // Check feature java resource output
        assertThat(featureJavaResOutput).exists()
        ZipArchive(featureJavaResOutput.toPath()).use {
            assertThat(it.listEntries()).containsExactly("foo.txt")
        }

        // run again with different keep rules such that we expect no classes in feature
        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            java8Support = VariantScope.Java8LangSupport.R8,
            r8Keep = "class " + CarbonForm::class.java.name,
            featureClassJars = listOf(featureClassesJar.toFile()),
            featureJavaResourceJars = listOf(featureJavaResJar.toFile()),
            featureDexDir = featureDexDir,
            featureJavaResourceOutputDir = featureJavaResourceOutputDir
        )

        val baseDex = Dex(outputDir.resolve("main").resolve("classes.dex"))
        assertThat(baseDex).containsExactlyClassesIn(
            listOf(Type.getDescriptor(CarbonForm::class.java))
        )

        // there are no classes for the feature, but we expect the empty parent directory
        val featureDexParent = featureDexDir.resolve("feature")
        assertThat(featureDexParent).exists()
        assertThat(featureDexParent).isDirectory()
        assertThat(featureDexParent.listFiles()).hasLength(0)

        // Check feature java resource output
        assertThat(featureJavaResOutput).exists()
        ZipArchive(featureJavaResOutput.toPath()).use {
            assertThat(it.listEntries()).containsExactly("foo.txt")
        }

    }

    @Test
    fun testNonAsciiClassName() {
        // test for http://b.android.com/221057
        val nonAsciiName = "com/android/tests/basic/Ubicación"
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf(nonAsciiName))

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            r8Keep = "class " + nonAsciiName.replace("/", ".")
        )

        assertClassExists(nonAsciiName)
    }

    @Test
    fun testMappingProduced() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A"))
        val outputMapping = tmp.newFile()

        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            disableMinification = false,
            outputProguardMapping = outputMapping,
            r8Keep = "class **"
        )

        assertThat(outputMapping).exists()
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

        runR8(
            classes = listOf(mixedResources.toFile()),
            resources = listOf(mixedResources.toFile(), resources.toFile()),
            r8Keep = "class **"
        )

        assertClassExists("test/A")

        assertThat(outputDir.resolve("java_res.jar")) {
            it.containsFileWithContent("metadata1.txt", "")
            it.containsFileWithContent("metadata2.txt", "")
            it.containsFileWithContent("data/metadata.txt", "")
            it.containsFileWithContent("a/b/c//metadata.txt", "")
            it.doesNotContain("test/A.class")
        }
    }

    private fun assertClassExists(clazz: Class<*>) {
       assertClassExists(Type.getInternalName(clazz))
    }

    private fun assertClassExists(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            val dex = getDex()
            assertThat(dex).containsClass("L$className;")
        } else {
            assertThat(outputDir.resolve("main.jar")) {
                it.contains("$className.class")
            }
        }
    }

    private fun assertClassDoesNotExist(clazz: Class<*>) {
        assertClassDoesNotExist(Type.getInternalName(clazz))
    }

    private fun assertClassDoesNotExist(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            val dex = getDex()
            assertThat(dex).doesNotContainClasses("L$className;")
        } else {
            assertThat(outputDir.resolve("main.jar")) {
                it.doesNotContain("$className.class")
            }
        }
    }

    private fun assertClassHasAnnotations(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            assertThat(getDex()).containsClass(Type.getDescriptor(Toy::class.java)).that()
                .hasAnnotations()
        } else {
            assertThat(hasAnnotations(className)).named("class has annotations").isTrue()
        }
    }

    private fun assertClassDoesNotHaveAnnotations(className: String) {
        if (r8OutputType == R8OutputType.DEX) {
            // Check proguard compatibility mode
            assertThat(getDex()).containsClass("L$className;").that()
                .doesNotHaveAnnotations()
        } else {
            assertThat(hasAnnotations(className)).named("class does not have annotations").isFalse()
        }
    }

    private fun hasAnnotations(className: String): Boolean {
        var foundAnnotation = false
        ZipFile(outputDir.resolve("main.jar").toFile()).use {
            val input =
                it.getInputStream(it.getEntry("$className.class"))
            ClassReader(input).accept(object : ClassVisitor(Opcodes.ASM7) {
                override fun visitAnnotation(
                    desc: String?,
                    visible: Boolean
                ): AnnotationVisitor? {
                    foundAnnotation = true
                    return super.visitAnnotation(desc, visible)
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
        }
        return foundAnnotation
    }

    @Test
    fun testClassesIgnoredFromResources() {
        val resDir = tmp.root.resolve("res_dir").also {
            it.mkdir()
            it.resolve("res.txt").createNewFile()
            it.resolve("A.class").createNewFile()
        }
        val resJar = tmp.root.resolve("res.jar")
            ZipOutputStream(resJar.outputStream()).use {
                it.putNextEntry(ZipEntry("data.txt"))
                it.closeEntry()
                it.putNextEntry(ZipEntry("B.class"))
                it.closeEntry()
            }

        runR8(
            classes = listOf(),
            resources = listOf(resDir, resJar)
        )

        assertThat(outputDir.resolve("main/classes.dex")).doesNotExist()
        assertThat(outputDir.resolve("java_res.jar")) {
            it.contains("res.txt")
            it.contains("data.txt")
            it.doesNotContain("A.class")
            it.doesNotContain("B.class")
        }
    }

    @Test
    fun testKeepRulesGeneration() {
        Assume.assumeTrue(r8OutputType == R8OutputType.DEX)
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(
            classes,
            ImmutableList.of<Class<*>>(ClassWithDesugarApi::class.java)
        )
        val libConfiguration =  TestUtils.getDesugarLibConfigContent()
        val outputKeepRulesDir = tmp.newFolder()
        runR8(
            classes = listOf(classes.toFile()),
            resources = listOf(),
            disableMinification = false,
            java8Support = VariantScope.Java8LangSupport.R8,
            r8Keep = "class " + ClassWithDesugarApi::class.java.name + "{*;}",
            libConfiguration = libConfiguration,
            outputKeepRulesDir = outputKeepRulesDir
        )
        val expectedKeepRules = "-keep class j\$.time.LocalTime {$lineSeparator" +
                "    j\$.time.LocalTime MIDNIGHT;$lineSeparator" +
                "}$lineSeparator"
        assertThat(outputKeepRulesDir.resolve("output")).contains(expectedKeepRules)
    }

    /** Regression test for b/151605314. */
    @Test
    fun testNonExistingFileAsInput() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A"))

        runR8(
            classes = listOf(classes.toFile(), tmp.root.resolve("non_existing")),
            resources = listOf(),
            r8Keep = "class **"
        )

        assertClassExists("test/A")
    }

    private fun getDex(): Dex {
        val dexFiles = Files.walk(outputDir).filter { it.toString().endsWith(".dex") }.toList()
        return Dex(dexFiles.single())
    }

    private fun checkBaseAndFeatureDex(
        baseClasses: List<String>,
        featureClasses: List<String>,
        featureName: String
    ) {
        val baseDex = Dex(outputDir.resolve("main").resolve("classes.dex"))
        assertThat(baseDex).containsClassesIn(baseClasses)
        assertThat(baseDex).doesNotContainClasses(*featureClasses.toTypedArray())

        val featureDex = Dex(featureDexDir.resolve("$featureName/classes.dex"))
        assertThat(featureDex).containsExactlyClassesIn(featureClasses)
        assertThat(featureDex).doesNotContainClasses(*baseClasses.toTypedArray())
    }


    private fun runR8(
        classes: List<File>,
        resources: List<File>,
        mainDexRulesFiles: List<File> = listOf(),
        java8Support: VariantScope.Java8LangSupport = VariantScope.Java8LangSupport.UNUSED,
        proguardRulesFiles: List<File> = listOf(),
        outputProguardMapping: File = outputDir.resolve("mapping.txt").toFile(),
        disableMinification: Boolean = true,
        disableTreeShaking: Boolean = false,
        minSdkVersion: Int = 21,
        useFullR8: Boolean = false,
        r8Keep: String? = null,
        referencedInputs: List<File> = listOf(),
        featureClassJars: List<File> = listOf(),
        featureJavaResourceJars: List<File> = listOf(),
        featureDexDir: File? = null,
        featureJavaResourceOutputDir: File? = null,
        libConfiguration: String? = null,
        outputKeepRulesDir: File? = null
    ) {
        val proguardConfigurations: MutableList<String> = mutableListOf(
            "-ignorewarnings")

        r8Keep?.let { proguardConfigurations.add("-keep $it") }

        val variantType =
            if (r8OutputType == R8OutputType.DEX)
                VariantTypeImpl.BASE_APK
            else
                VariantTypeImpl.LIBRARY



        val output: File =
            if (variantType.isAar) {
                outputDir.resolve("main.jar").toFile()
            } else {
                outputDir.resolve("main").toFile()
            }

        R8Task.shrink(
            bootClasspath = listOf(TestUtils.resolvePlatformPath("android.jar").toFile()),
            minSdkVersion = minSdkVersion,
            isDebuggable = true,
            enableDesugaring =
                java8Support == VariantScope.Java8LangSupport.R8
                    && !variantType.isAar,
            disableTreeShaking = disableTreeShaking,
            disableMinification = disableMinification,
            mainDexListFiles = listOf(),
            mainDexRulesFiles = mainDexRulesFiles,
            inputProguardMapping = null,
            proguardConfigurationFiles = proguardRulesFiles,
            proguardConfigurations = proguardConfigurations,
            variantType = variantType,
            messageReceiver = NoOpMessageReceiver(),
            dexingType = DexingType.NATIVE_MULTIDEX,
            useFullR8 = useFullR8,
            referencedInputs = referencedInputs,
            classes = classes,
            resources = resources,
            proguardOutputFiles =
                ProguardOutputFiles(
                    outputProguardMapping.toPath(),
                    tmp.root.resolve("seeds.txt").toPath(),
                    tmp.root.resolve("usage.txt").toPath(),
                    tmp.root.resolve("configuration.txt").toPath(),
                    tmp.root.resolve("misssing_rules.txt").toPath()),
            output = output,
            outputResources = outputDir.resolve("java_res.jar").toFile(),
            mainDexListOutput = null,
            featureClassJars = featureClassJars,
            featureJavaResourceJars = featureJavaResourceJars,
            featureDexDir = featureDexDir,
            featureJavaResourceOutputDir = featureJavaResourceOutputDir,
            libConfiguration = libConfiguration,
            outputKeepRulesDir = outputKeepRulesDir
        )
    }

    private val lineSeparator: String = System.lineSeparator()
}
