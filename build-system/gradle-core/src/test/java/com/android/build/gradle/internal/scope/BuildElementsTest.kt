/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import com.android.build.VariantOutput
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.InternalArtifactType.*
import com.android.builder.core.VariantTypeImpl
import com.android.utils.Pair
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterators
import com.google.common.io.FileWriteMode
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import org.apache.commons.io.FileUtils
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException
import java.io.StringReader

/**
 * Tests for the {@link KBuildOutputs} class
 */
class BuildElementsTest {

    @Mock private val variantDslInfo: VariantDslInfo? = null

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(variantDslInfo!!.variantType).thenReturn(VariantTypeImpl.BASE_APK)
    }

    @Test
    @Throws(IOException::class)
    fun testPersistence() {
        val outputFactory = OutputFactory("project", variantDslInfo)

        outputFactory.addUniversalApk()
        val densityApkData = outputFactory.addFullSplit(
                ImmutableList.of(Pair.of(VariantOutput.FilterType.DENSITY, "xxhdpi")))

        // simulate output
        val outputForSplit = temporaryFolder.newFile()
        val persistedState = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = ImmutableList.of(BuildOutput(
                COMPATIBLE_SCREEN_MANIFEST,
                densityApkData,
                outputForSplit)))
                .persist(temporaryFolder.root.toPath())

        // load the persisted state.
        val reader = StringReader(persistedState)
        val buildOutputs = ExistingBuildElements.load(temporaryFolder.root.toPath(),
                        COMPATIBLE_SCREEN_MANIFEST,
                        reader)

        // check that persisted was loaded correctly.
        assertThat(buildOutputs).hasSize(1)
        val buildOutput = Iterators.getOnlyElement<BuildOutput>(buildOutputs.iterator())
        assertThat(buildOutput.outputFile).isEqualTo(outputForSplit)
        assertThat(buildOutput.apkData.filters).hasSize(1)
        val filter = Iterators.getOnlyElement(buildOutput.apkData.filters.iterator())
        assertThat(filter.identifier).isEqualTo("xxhdpi")
        assertThat(filter.filterType).isEqualTo(VariantOutput.FilterType.DENSITY.name)
    }

    @Test
    @Throws(IOException::class)
    fun getBuildMetadataFileTest() {
        val folder = temporaryFolder.newFolder()
        val outputFile = ExistingBuildElements.getMetadataFile(folder)
        assertThat(outputFile.name).isEqualTo("output.json")
        assertThat(outputFile.parentFile).isEqualTo(folder)
    }

    @Test
    @Throws(IOException::class)
    fun testNoRequestedTypesLoading() {
        val folder = temporaryFolder.newFolder()
        val outputFile = File(folder, "output.json")
        FileUtils.write(
                outputFile,
                "{\n" +
                        "  \"version\": 1,\n" +
                        "  \"applicationId\": \"com.app.example\",\n" +
                        "  \"variantType\": \"debug\",\n" +
                        "  \"elements\": [{\"outputType\":{\"type\":\"MERGED_MANIFESTS\"},"
                        + "\"apkData\":{\"type\":\"MAIN\",\"splits\":[],\"versionCode\":12},"
                        + "\"path\":\"/foo/bar/AndroidManifest.xml\","
                        + "\"properties\":{\"packageId\":\"com.android.tests.basic.debug\","
                        + "\"split\":\"\"}}]}")
        val mergedManifests = ExistingBuildElements.from(MERGED_MANIFESTS, folder)
        assertThat(mergedManifests
                .asSequence()
                .filter { it.type.name() == MERGED_MANIFESTS.name() }
                .count())
                .isEqualTo(1)
    }

    @Test
    @Throws(IOException::class)
    fun testElementByType() {
        val folder = temporaryFolder.newFolder()
        val outputFile = File(folder, "output.json")
        FileUtils.write(
            outputFile,
            "{\n" +
                    "  \"applicationId\": \"com.app.example\",\n" +
                    "  \"variantType\": \"debug\",\n" +
                    "  \"elements\": [{\"outputType\":{\"type\":\"MERGED_MANIFESTS\"},"
                    + "\"apkData\":{\"type\":\"MAIN\",\"splits\":[],\"versionCode\":12},"
                    + "\"path\":\"/foo/bar/AndroidManifest.xml\","
                    + "\"properties\":{\"packageId\":\"com.android.tests.basic.debug\","
                    + "\"split\":\"\"}}]}")

        val buildElements = ExistingBuildElements.from(MERGED_MANIFESTS, folder)
        val elementByType = buildElements.elementByType(VariantOutput.OutputType.MAIN)
        assertThat(elementByType).isNotNull()
        assertThat(elementByType!!.outputPath.toString()).contains(
                File("/foo/bar/AndroidManifest.xml").path.toString())
    }

    @Test
    @Throws(IOException::class)
    fun testNoFilterNoPropertiesLoading() {
        val folder = temporaryFolder.newFolder()
        val outputFile = File(folder, "output.json")
        FileUtils.write(
                outputFile,
                "{\n" +
                        "  \"applicationId\": \"com.app.example\",\n" +
                        "  \"variantType\": \"debug\",\n" +
                        "  \"elements\": [{\"outputType\":{\"type\":\"MERGED_MANIFESTS\"},"
                        + "\"apkData\":{\"type\":\"MAIN\",\"splits\":[],\"versionCode\":12},"
                        + "\"path\":\"/foo/bar/AndroidManifest.xml\","
                        + "\"properties\":{\"packageId\":\"com.android.tests.basic.debug\","
                        + "\"split\":\"\"}}]}")
        assertThat(ExistingBuildElements.from(PROCESSED_RES, outputFile)).isEmpty()
        val buildOutputs = ExistingBuildElements.from(MERGED_MANIFESTS, folder)
        assertThat(buildOutputs).hasSize(1)
        val buildOutput = buildOutputs.iterator().next()
        assertThat(buildOutput.type).isEqualTo(MERGED_MANIFESTS)
        assertThat(buildOutput.filters).isEmpty()
        assertThat(buildOutput.outputFile.absolutePath)
                .isEqualTo(File("/foo/bar/AndroidManifest.xml").absolutePath)
        assertThat(buildOutput.apkData.type).isEqualTo(VariantOutput.OutputType.MAIN)
        assertThat(buildOutput.apkData.filters).isEmpty()
    }

    @Test
    @Throws(IOException::class)
    fun testBuildOutputPropertiesLoading() {
        val folder = temporaryFolder.newFolder()
        val outputFile = File(folder, "output.json")
        FileUtils.write(
                outputFile,
                ("{\n" +
                        "  \"applicationId\": \"com.app.example\",\n" +
                        "  \"variantType\": \"debug\",\n" +
                        "  \"elements\": [{\"outputType\":{\"type\":\"MERGED_MANIFESTS\"},"
                        + "\"apkData\":{\"type\":\"MAIN\",\"splits\":[],\"versionCode\":12},"
                        + "\"path\":\"/foo/bar/AndroidManifest.xml\","
                        + "\"properties\":{\"packageId\":\"com.android.tests.basic\","
                        + "\"split\":\"\"}}]}"))
        val buildOutputs = ExistingBuildElements.from(MERGED_MANIFESTS, folder)
        assertThat(buildOutputs).hasSize(1)
        val buildOutput = Iterators.getOnlyElement(buildOutputs.iterator())
        assertThat(buildOutput.properties).hasSize(2)
        assertThat(buildOutput.properties[BuildOutputProperty.PACKAGE_ID])
                .isEqualTo("com.android.tests.basic")
        assertThat(buildOutput.properties[BuildOutputProperty.SPLIT]).isEmpty()
    }

    @Test
    @Throws(IOException::class)
    fun testRelativePath() {
        val apkInfo = Mockito.mock(ApkData::class.java)
        `when`<VariantOutput.OutputType>(apkInfo.type).thenReturn(VariantOutput.OutputType.MAIN)
        `when`<Int>(apkInfo.versionCode).thenReturn(123)

        val outputFolder = File(temporaryFolder.root, "out/apk")
        assertTrue(outputFolder.mkdirs())
        val apk = File(outputFolder, "location.apk")
        Files.asCharSink(apk, Charsets.UTF_8, FileWriteMode.APPEND).write("content")
        val buildOutput = BuildOutput(PROCESSED_RES, apkInfo, apk)

        assertThat(buildOutput.outputFile.absolutePath).contains(temporaryFolder.root.absolutePath)

        val gsonOutput = BuildElements(
            applicationId = "com.app.example",
            variantType = "debug",
            elements = ImmutableList.of(buildOutput)).persist(temporaryFolder.root.toPath())

        assertThat(gsonOutput).isNotEmpty()
        assertThat(gsonOutput).doesNotContain(temporaryFolder.root.name)

        // load the saved project in a "new" project location
        val newProjectLocation = temporaryFolder.newFolder()
        val loadedBuildOutputs = ExistingBuildElements.load(
                newProjectLocation.toPath(),
                PROCESSED_RES,
                StringReader(gsonOutput))

        assertThat(loadedBuildOutputs).hasSize(1)
        loadedBuildOutputs.forEach { loadedBuildOutput ->
            assertThat(loadedBuildOutput.outputFile.absolutePath)
                    .startsWith(newProjectLocation.absolutePath)
        }
    }
}