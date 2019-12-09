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

import com.android.build.VariantOutput
import com.android.build.gradle.internal.ide.FilterDataImpl
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.DirectoryProperty
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.RuntimeException
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
        val buildOutput = BuildOutput(
            InternalArtifactType.PROCESSED_RES,
            ApkData.of(
                VariantOutput.OutputType.MAIN,
                listOf(),
                1
            ),
            File(outputFolder,"the_right_file")
        )
        val buildElements = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = listOf(buildOutput))
        buildElements.save(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).isEqualTo("the_right_file")
    }

    @Test
    fun testWithABI() {
        val outputFolder = directoryProperty.get().asFile
        val buildOutput = BuildOutput(
            InternalArtifactType.PROCESSED_RES,
            ApkData.of(
                VariantOutput.OutputType.FULL_SPLIT,
                listOf(FilterDataImpl(VariantOutput.FilterType.ABI, "x86")), 1
            ),
            File(outputFolder,"the_right_file")
        )
        val buildElements = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = listOf(buildOutput))
        buildElements.save(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).isEqualTo("the_right_file")
    }

    @Test
    fun testWithMultipleABIs() {
        val outputFolder = directoryProperty.get().asFile
        val buildElements = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = listOf(
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(FilterDataImpl(VariantOutput.FilterType.ABI, "x86")), 1
                    ),
                    File(outputFolder,"the_right_file_1")
                ),
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(FilterDataImpl(VariantOutput.FilterType.ABI, "arm")), 1
                    ),
                    File(outputFolder,"the_right_file_2")
                )
        ))
        buildElements.save(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).startsWith("the_right_file")
    }

    @Test
    fun testWithOnlyDensity() {
        val outputFolder = directoryProperty.get().asFile
        val buildElements = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = listOf(
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(FilterDataImpl(VariantOutput.FilterType.DENSITY, "xhdpi")), 1
                    ),
                    File(outputFolder,"the_wrong_file")
                ),
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(FilterDataImpl(VariantOutput.FilterType.DENSITY, "xhdpi")), 1
                    ),
                    File(outputFolder,"the_wrong_file")
                )))
        buildElements.save(outputFolder)

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
        val buildElements = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = listOf(
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(
                            FilterDataImpl(VariantOutput.FilterType.ABI, "x86"),
                            FilterDataImpl(VariantOutput.FilterType.DENSITY, "xxhdpi")),
                        1
                    ),
                    File(outputFolder,"the_wrong_file_1")
                ),
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(FilterDataImpl(VariantOutput.FilterType.ABI, "x86")),
                        1
                    ),
                    File(outputFolder,"the_right_file_1")
                ),
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(FilterDataImpl(VariantOutput.FilterType.ABI, "arm")),
                        1
                    ),
                    File(outputFolder,"the_right_file_2")
                ),
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(
                            FilterDataImpl(VariantOutput.FilterType.ABI, "arm"),
                            FilterDataImpl(VariantOutput.FilterType.DENSITY, "xxhdpi")),
                        1
                    ),
                    File(outputFolder,"the_wrong_file_2")
                )
        ))
        buildElements.save(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).startsWith("the_right_file")
    }

    @Test
    fun testDensitySplitsWithUniversal() {
        val outputFolder = directoryProperty.get().asFile
        val buildElements = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = listOf(
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(
                            FilterDataImpl(VariantOutput.FilterType.DENSITY, "hdpi")),
                        1
                    ),
                    File(outputFolder,"the_wrong_file_1")
                ),
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(
                            FilterDataImpl(VariantOutput.FilterType.DENSITY, "xhdpi")),
                        1
                    ),
                    File(outputFolder,"the_wrong_file_2")
                ),
                BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    ApkData.of(
                        VariantOutput.OutputType.FULL_SPLIT,
                        listOf(),
                        1
                    ),
                    File(outputFolder,"the_right_file")
                )
        ))
        buildElements.save(outputFolder)

        assertThat(task.apkFrom(directoryProperty).name).isEqualTo("the_right_file")
    }
}