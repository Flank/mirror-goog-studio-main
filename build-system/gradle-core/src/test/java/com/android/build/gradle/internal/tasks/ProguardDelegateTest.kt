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

import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Zip
import com.android.testutils.truth.MoreTruth.assertThat
import com.google.common.collect.ImmutableList
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

class ProguardDelegateTest {
    @get: Rule
    val tmp: TemporaryFolder = TemporaryFolder()
    private lateinit var outputDir: Path

    @Before
    fun setUp() {
        outputDir = tmp.newFolder().toPath()
    }

    @Test
    fun testKeepClassAPI() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val builder = createProguardDelegateBuilder()
        builder.classes = listOf(classes.toFile())
        builder.keepRules = listOf("public class test.A")

        builder.build().run()

        val resultJar = resultJar()
        assertThat(resultJar).contains("test/A.class")
        assertThat(resultJar).doesNotContain("test/B.class")
    }

    @Test
    fun testKeepClassFileRule() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val configFile = newFile("-keep public class test.A")

        val builder = createProguardDelegateBuilder()
        builder.classes = listOf(classes.toFile())
        builder.configurationFiles = setOf(configFile)

        builder.build().run()

        val resultJar = resultJar()
        assertThat(resultJar).contains("test/A.class")
        assertThat(resultJar).doesNotContain("test/B.class")
    }

    @Test
    fun testUseProguardConfigurationFile_multipleFiles() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val classes2 = tmp.root.toPath().resolve("classes2.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes2, listOf("test/C", "test/D"))

        val configFileA = newFile("-keep public class test.A")
        val configFileC = newFile("-keep public class test.C")

        val builder = createProguardDelegateBuilder()
        builder.classes = listOf(classes.toFile(), classes2.toFile())
        builder.configurationFiles = listOf(configFileA, configFileC)

        builder.build().run()

        val resultJar = resultJar()
        assertThat(resultJar).contains("test/A.class")
        assertThat(resultJar).contains("test/C.class")
        assertThat(resultJar).doesNotContain("test/B.class")
        assertThat(resultJar).doesNotContain("test/D.class")
    }

    @Test
    fun testUseProguardConfigurationFile_multilineRule() {
        val classes = tmp.root.toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val configFile = newFile("-keep\n    public class test.A")

        val builder = createProguardDelegateBuilder()
        builder.classes = listOf(classes.toFile())
        builder.configurationFiles = listOf(configFile)

        builder.build().run()

        val resultJar = resultJar()
        assertThat(resultJar).contains("test/A.class")
        assertThat(resultJar).doesNotContain("test/B.class")
    }

    private fun resultJar(): Zip {
        return Zip(Files.walk(outputDir).filter {
            it.toString().endsWith(".jar")
        }.toList().single())
    }

    private fun newFile(withContent: String): File {
        val f = tmp.newFile()
        f.writeText(withContent)
        return f
    }

    private fun createProguardDelegateBuilder(): ProguardDelegateBuilder {
        val builder = ProguardDelegateBuilder()

        //TODO: write tests that exercise these inputs
        builder.resources = ImmutableList.of();
        builder.referencedClasses = ImmutableList.of();
        builder.referencedResources = ImmutableList.of();

        //TODO: idiomatic way to do this?
        builder.outFile = outputDir.resolve("output.jar").toFile()
        builder.mappingFile = tmp.root.resolve("mapping/mapping.txt")
        builder.seedsFile = tmp.root.resolve("mapping/seeds.txt")
        builder.usageFile= tmp.root.resolve("mapping/usage.txt")

        builder.configurationFiles = ImmutableList.of()

        val bootClasspath = ImmutableList.of(TestUtils.getPlatformFile("android.jar"))
        builder.bootClasspath = bootClasspath
        builder.fullBootClasspath = bootClasspath

        builder.keepRules = ImmutableList.of()
        builder.dontWarnRules = ImmutableList.of()

        return builder
    }
}

// Local Builder class to make test setup more fluent
private class ProguardDelegateBuilder() {
    lateinit var classes: Collection<File>
    lateinit var resources: Collection<File>
    lateinit var referencedClasses: Collection<File>
    lateinit var referencedResources: Collection<File>
    lateinit var outFile: File
    lateinit var mappingFile: File
    lateinit var seedsFile: File
    lateinit var usageFile: File
    var testedMappingFile: File? = null
    lateinit var configurationFiles: Collection<File>
    lateinit var bootClasspath: Collection<File>
    lateinit var fullBootClasspath: Collection<File>
    lateinit var keepRules: Collection<String>
    lateinit var dontWarnRules: Collection<String>
    var optimizationEnabled: Boolean? = null
    var shrinkingEnabled: Boolean? = null
    var obfuscationEnabled: Boolean? = null

    fun build() = ProguardDelegate(
        classes,
        resources,
        referencedClasses,
        referencedResources,
        outFile,
        mappingFile,
        seedsFile,
        usageFile,
        testedMappingFile,
        configurationFiles,
        bootClasspath,
        fullBootClasspath,
        keepRules,
        dontWarnRules,
        optimizationEnabled,
        shrinkingEnabled,
        obfuscationEnabled
    )
}
