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

import com.android.build.api.variant.FilterConfiguration.FilterType
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.FilterConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.DirectoryProperty
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.fail

/**
 * Tests for [PackageForUnitTest]
 */
class PackageForUnitTestTest {

    @Rule
    @JvmField
    val tmpDir: TemporaryFolder = TemporaryFolder()

    lateinit var task: PackageForUnitTest
    lateinit var directoryProperty: DirectoryProperty

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(
            tmpDir.newFolder()).build()

        task = project.tasks.create("test", PackageForUnitTest::class.java)
        directoryProperty = project.objects.directoryProperty()
        directoryProperty.set(tmpDir.newFolder())
    }

    @Test(expected = RuntimeException::class)
    fun testNoResources() {
        task.apkFrom(directoryProperty)
    }


    @Test
    fun testNoSplits() {
        val outputFolder = directoryProperty.get().asFile

        createBuiltArtifacts(listOf(
            createBuiltArtifact(File(outputFolder,"the_right_file").absolutePath)
        )).saveToDirectory(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).isEqualTo("the_right_file")
    }

    @Test
    fun testWithABI() {
        val outputFolder = directoryProperty.get().asFile

        createBuiltArtifacts(listOf(
            createBuiltArtifact(File(outputFolder,"the_right_file").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.ABI, "x86"))))
        )).saveToDirectory(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).isEqualTo("the_right_file")
    }

    @Test
    fun testWithMultipleABIs() {
        val outputFolder = directoryProperty.get().asFile

        createBuiltArtifacts(listOf(
            createBuiltArtifact(File(outputFolder,"the_right_file_1").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.ABI, "x86")))),
            createBuiltArtifact(File(outputFolder,"the_right_file_2").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.ABI, "arm"))))
        )).saveToDirectory(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).startsWith("the_right_file")
    }

    @Test
    fun testWithOnlyDensity() {
        val outputFolder = directoryProperty.get().asFile

        createBuiltArtifacts(listOf(
            createBuiltArtifact(File(outputFolder,"the_wrong_file").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.DENSITY, "xhdpi")))),
            createBuiltArtifact(File(outputFolder,"the_wrong_file").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.DENSITY, "xxhdpi"))))
        )).saveToDirectory(outputFolder)

        try {
            task.apkFrom(directoryProperty)
        } catch(e: RuntimeException) {
            assertThat(e.toString()).contains("Cannot find a build output with all resources")
            return
        }
        fail("Expected exception not raised.")
    }

    @Test
    fun testWithCombinedButNoUniversal() {
        val outputFolder = directoryProperty.get().asFile

        createBuiltArtifacts(listOf(
            createBuiltArtifact(File(outputFolder,"the_wrong_file_1").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(
                        FilterConfigurationImpl(FilterType.DENSITY, "xhdpi"),
                        FilterConfigurationImpl(FilterType.ABI, "x86")
                    ))),
            createBuiltArtifact(File(outputFolder,"the_right_file_1").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.ABI, "x86")))),
            createBuiltArtifact(File(outputFolder,"the_wrong_file_2").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(
                        FilterConfigurationImpl(FilterType.DENSITY, "xxhdpi"),
                        FilterConfigurationImpl(FilterType.ABI, "arm")
                    ))),
            createBuiltArtifact(File(outputFolder,"the_right_file_2").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.ABI, "arm"))))
        )).saveToDirectory(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).startsWith("the_right_file")
    }

    @Test
    fun testDensitySplitsWithUniversal() {
        val outputFolder = directoryProperty.get().asFile

        createBuiltArtifacts(listOf(

            createBuiltArtifact(File(outputFolder,"the_wrong_file_1").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(
                        FilterConfigurationImpl(FilterType.DENSITY, "hdpi")
                    ))),

            createBuiltArtifact(File(outputFolder,"the_wrong_file_2").absolutePath,
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterType.DENSITY, "xhdpi")))),

            createBuiltArtifact(File(outputFolder,"the_right_file").absolutePath,
                VariantOutputConfigurationImpl(true,
                    listOf()))
        )).saveToDirectory(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).isEqualTo("the_right_file")
    }

    private fun createBuiltArtifact(
        outputFile: String,
        variantOutputConfiguration: VariantOutputConfiguration = VariantOutputConfigurationImpl()
    ) = BuiltArtifactImpl.make(
        outputFile = outputFile,
        variantOutputConfiguration = variantOutputConfiguration
    )

    private fun createBuiltArtifacts(artifacts: Collection<BuiltArtifactImpl>) = BuiltArtifactsImpl(
        artifactType = InternalArtifactType.PROCESSED_RES,
        applicationId = "com.app.example",
        variantName = "debug",
        elements = artifacts)
}
