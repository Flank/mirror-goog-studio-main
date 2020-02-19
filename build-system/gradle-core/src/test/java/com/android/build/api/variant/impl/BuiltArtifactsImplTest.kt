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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.PublicArtifactType
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.FilterConfiguration
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.utils.NullLogger
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream

/**
 * Tests for [BuiltArtifactsImpl]
 */
class BuiltArtifactsImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun simpleWriting() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        BuiltArtifactsImpl(
            artifactType = PublicArtifactType.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl.make(
                    outputFile = File(outputFolder, "file1.apk").absolutePath,
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true
                )
            )
        ).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo(
            """{
  "version": 2,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "properties": [],
      "versionCode": 123,
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file1.apk"
    }
  ]
}"""
        )
    }

    @Test
    fun testMultipleOutputWithFilters() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        BuiltArtifactsImpl(
            artifactType = PublicArtifactType.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl.make(
                    outputFile = File(outputFolder, "file1.apk").absolutePath,
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    variantOutputConfiguration = VariantOutputConfigurationImpl(
                        isUniversal = false,
                        filters = listOf(
                            FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xhdpi")
                        ))
                ),
                BuiltArtifactImpl.make(
                    outputFile = File(outputFolder, "file2.apk").absolutePath,
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    variantOutputConfiguration = VariantOutputConfigurationImpl(
                        isUniversal = false,
                        filters = listOf(
                            FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xxhdpi")
                        ))
                ),
                BuiltArtifactImpl.make(
                    outputFile = File(outputFolder, "file3.apk").absolutePath,
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    variantOutputConfiguration = VariantOutputConfigurationImpl(
                        isUniversal = false,
                        filters = listOf(
                            FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xxxhdpi")
                        ))
                )
            )
        ).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo(
            """{
  "version": 2,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "ONE_OF_MANY",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xhdpi"
        }
      ],
      "properties": [],
      "versionCode": 123,
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file1.apk"
    },
    {
      "type": "ONE_OF_MANY",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xxhdpi"
        }
      ],
      "properties": [],
      "versionCode": 123,
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file2.apk"
    },
    {
      "type": "ONE_OF_MANY",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xxxhdpi"
        }
      ],
      "properties": [],
      "versionCode": 123,
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file3.apk"
    }
  ]
}"""
        )
    }

    @Test
    fun testWithProperties() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        createBuiltArtifacts(
            createBuiltArtifact(
                outputFolder = outputFolder,
                fileName = "file1", versionCode = 123, densityValue = "xhdpi"
            )).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo(
            """{
  "version": 2,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "ONE_OF_MANY",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xhdpi"
        }
      ],
      "properties": [
        {
          "key": "key1",
          "value": "value1"
        },
        {
          "key": "key2",
          "value": "value2"
        }
      ],
      "versionCode": 123,
      "versionName": "123",
      "enabled": true,
      "outputFile": "file1.apk"
    }
  ]
}"""
        )
    }

    @Test
    fun testStudioLoading() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        createBuiltArtifacts(
            createBuiltArtifact(
                outputFolder = outputFolder,
                fileName = "file1", versionCode = 123, densityValue = "xhdpi"
            ),
            createBuiltArtifact(
                outputFolder = outputFolder, fileName = "file2",
                versionCode = 124, densityValue = "xxhdpi"
            )
        ).save(FakeGradleDirectory(outputFolder))

        val builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(
            File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME),
            NullLogger()
        )

        Truth.assertThat(builtArtifacts).isNotNull()
        Truth.assertThat(builtArtifacts!!.version).isEqualTo(
            BuiltArtifacts.METADATA_FILE_VERSION
        )
        Truth.assertThat(builtArtifacts.artifactType.type).isEqualTo(
            PublicArtifactType.APK.name()
        )
        Truth.assertThat(builtArtifacts.artifactType.kind).isEqualTo(
            PublicArtifactType.APK.kind.dataType().simpleName
        )
        Truth.assertThat(builtArtifacts.applicationId).isEqualTo("com.android.test")
        Truth.assertThat(builtArtifacts.version).isEqualTo(
            BuiltArtifacts.METADATA_FILE_VERSION
        )
        val builtArtifactsList = builtArtifacts.elements
        Truth.assertThat(builtArtifactsList.size).isEqualTo(2)
        builtArtifactsList.forEach { builtArtifact ->
            Truth.assertThat(File(builtArtifact.outputFile).name)
                .isAnyOf("file1.apk", "file2.apk")
            Truth.assertThat(builtArtifact.versionCode).isAnyOf(123, 124)
            Truth.assertThat(builtArtifact.versionName).isAnyOf("123", "124")
            Truth.assertThat(builtArtifact.isEnabled).isTrue()
            Truth.assertThat(builtArtifact.outputType).isEqualTo("ONE_OF_MANY")
            val filters = builtArtifact.filters
            Truth.assertThat(filters).hasSize(1)
            filters.forEach { filter ->
                Truth.assertThat(filter.filterType).isEqualTo("DENSITY")
                Truth.assertThat(filter.identifier).isAnyOf("xxhdpi", "xhdpi")
            }
            val properties = builtArtifact.properties
            Truth.assertThat(properties).hasSize(2)
            properties.forEach {
                Truth.assertThat(it.key).isAnyOf("key1", "key2")
                Truth.assertThat(it.value).isAnyOf("value1", "value2")
            }
        }
    }

    @Test
    fun testSerialization() {
        val artifacts = createBuiltArtifacts(
            createBuiltArtifact(
                outputFolder = tmpFolder.root,
                fileName = "file1", versionCode = 123, densityValue = "xhdpi"
            ),
            createBuiltArtifact(
                outputFolder = tmpFolder.root, fileName = "file2",
                versionCode = 123, densityValue = "xxhdpi"
            ),
            createBuiltArtifact(
                outputFolder = tmpFolder.root, fileName = "file3",
                versionCode = 123, densityValue = "xxxhdpi"
            )
        )

        ObjectOutputStream(ByteArrayOutputStream()).writeObject(artifacts)
    }

    private fun createBuiltArtifact(
        outputFolder: File,
        fileName: String,
        versionCode: Int,
        densityValue: String
    ) =
        BuiltArtifactImpl.make(
            outputFile = File(outputFolder, "$fileName.apk").absolutePath,
            properties = mapOf("key1" to "value1", "key2" to "value2"),
            versionCode = versionCode,
            versionName = versionCode.toString(),
            isEnabled = true,
            variantOutputConfiguration = VariantOutputConfigurationImpl(
                isUniversal = false,
                filters = listOf(
                    FilterConfiguration(FilterConfiguration.FilterType.DENSITY, densityValue)
                ))
        )

    private fun createBuiltArtifacts(vararg elements: BuiltArtifactImpl) =
        BuiltArtifactsImpl(
            artifactType = PublicArtifactType.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = elements.toList()
        )
}