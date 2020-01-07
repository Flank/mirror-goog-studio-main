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
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
                BuiltArtifactImpl(
                    outputFile = File(outputFolder, "file1.apk").toPath(),
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    outputType = VariantOutputConfiguration.OutputType.SINGLE,
                    filters = listOf()
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
}""")
    }

    @Test
    fun testMultipleOutputWithFilters() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        BuiltArtifactsImpl(
            artifactType = PublicArtifactType.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl(
                    outputFile = File(outputFolder, "file1.apk").toPath(),
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    outputType = VariantOutputConfiguration.OutputType.ONE_OF_MANY,
                    filters = listOf(
                        FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xhdpi")
                    )
                ),
                BuiltArtifactImpl(
                    outputFile = File(outputFolder, "file2.apk").toPath(),
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    outputType = VariantOutputConfiguration.OutputType.ONE_OF_MANY,
                    filters = listOf(
                        FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xxhdpi")
                    )
                ),
                BuiltArtifactImpl(
                    outputFile = File(outputFolder, "file3.apk").toPath(),
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    outputType = VariantOutputConfiguration.OutputType.ONE_OF_MANY,
                    filters = listOf(
                        FilterConfiguration(FilterConfiguration.FilterType.DENSITY, "xxxhdpi")
                    )
                )
            )
        ).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo("""{
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
}""")
    }

    @Test
    fun testWithProperties() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        BuiltArtifactsImpl(
            artifactType = PublicArtifactType.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl(
                    outputFile = File(outputFolder, "file1.apk").toPath(),
                    properties = mapOf("key1" to "value1", "key2" to "value2"),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    outputType = VariantOutputConfiguration.OutputType.SINGLE,
                    filters = listOf()
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
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file1.apk"
    }
  ]
}""")
    }
}