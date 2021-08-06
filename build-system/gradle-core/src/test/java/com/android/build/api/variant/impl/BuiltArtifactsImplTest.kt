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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.FilterConfiguration
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.ide.common.build.ListingFileRedirect
import com.android.utils.NullLogger
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import kotlin.test.fail

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
            artifactType = SingleArtifact.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl.make(
                    outputFile = createOutputFile(outputFolder, "file1.apk").absolutePath,
                    versionCode = 123,
                    versionName = "version_name"
                )
            )
        ).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo(
            """{
  "version": 3,
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
      "attributes": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "file1.apk"
    }
  ],
  "elementType": "File"
}"""
        )
    }

    @Test
    fun simpleDirectoryWriting() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        val innerFolder = tmpFolder.newFolder("some_folder", "inner_folder")
        BuiltArtifactsImpl(
                artifactType = InternalArtifactType.EXTRACTED_APKS,
                applicationId = "com.android.test",
                variantName = "debug",
                elements = listOf(
                        BuiltArtifactImpl.make(
                                outputFile = innerFolder.absolutePath,
                                versionCode = 123,
                                versionName = "version_name"
                        )
                )
        ).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo(
                """{
  "version": 3,
  "artifactType": {
    "type": "EXTRACTED_APKS",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "attributes": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "inner_folder"
    }
  ],
  "elementType": "Directory"
}"""
        )
    }

    @Test
    fun testMultipleOutputWithFilters() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        BuiltArtifactsImpl(
            artifactType = SingleArtifact.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl.make(
                    outputFile = createOutputFile(outputFolder, "file1.apk").absolutePath,
                    versionCode = 123,
                    versionName = "version_name",
                    variantOutputConfiguration = VariantOutputConfigurationImpl(
                        isUniversal = false,
                        filters = listOf(
                            FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xhdpi")
                        ))
                ),
                BuiltArtifactImpl.make(
                    outputFile = createOutputFile(outputFolder, "file2.apk").absolutePath,
                    versionCode = 123,
                    versionName = "version_name",
                    variantOutputConfiguration = VariantOutputConfigurationImpl(
                        isUniversal = false,
                        filters = listOf(
                            FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xxhdpi")
                        ))
                ),
                BuiltArtifactImpl.make(
                    outputFile = createOutputFile(outputFolder, "file3.apk").absolutePath,
                    versionCode = 123,
                    versionName = "version_name",
                    variantOutputConfiguration = VariantOutputConfigurationImpl(
                        isUniversal = false,
                        filters = listOf(
                            FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xxxhdpi")
                        ))
                )
            )
        ).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo(
            """{
  "version": 3,
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
      "attributes": [],
      "versionCode": 123,
      "versionName": "version_name",
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
      "attributes": [],
      "versionCode": 123,
      "versionName": "version_name",
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
      "attributes": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "file3.apk"
    }
  ],
  "elementType": "File"
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
  "version": 3,
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
      "attributes": [],
      "versionCode": 123,
      "versionName": "123",
      "outputFile": "file1.apk"
    }
  ],
  "elementType": "File"
}"""
        )
    }

    @Test
    fun testWithAttributes() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        createBuiltArtifacts(
            createBuiltArtifact(
                outputFolder = outputFolder,
                fileName = "file1", versionCode = 123, densityValue = "xhdpi", attributes = mapOf("DeliveryType" to "install-time")
            )).save(FakeGradleDirectory(outputFolder))

        val outputJsonFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        Truth.assertThat(outputJsonFile.exists())
        val jsonContent = outputJsonFile.readText(Charsets.UTF_8)
        Truth.assertThat(jsonContent).isEqualTo(
            """{
  "version": 3,
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
      "attributes": [
        {
          "key": "DeliveryType",
          "value": "install-time"
        }
      ],
      "versionCode": 123,
      "versionName": "123",
      "outputFile": "file1.apk"
    }
  ],
  "elementType": "File"
}"""
        )
    }

    @Test
    fun testStudioLoading() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        createBuiltArtifacts(
            createBuiltArtifact(
                outputFolder = outputFolder,
                fileName = "file1", versionCode = 123, densityValue = "xhdpi", attributes = mapOf("deliveryType" to "install-time", "a" to "b")
            ),
            createBuiltArtifact(
                outputFolder = outputFolder, fileName = "file2",
                versionCode = 124, densityValue = "xxhdpi", attributes = mapOf("deliveryType" to "install-time")
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
            SingleArtifact.APK.name()
        )
        Truth.assertThat(builtArtifacts.artifactType.kind).isEqualTo(
            SingleArtifact.APK.kind.dataType().simpleName
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
            Truth.assertThat(builtArtifact.outputType).isEqualTo("ONE_OF_MANY")
            val filters = builtArtifact.filters
            Truth.assertThat(filters).hasSize(1)
            filters.forEach { filter ->
                Truth.assertThat(filter.filterType).isEqualTo("DENSITY")
                Truth.assertThat(filter.identifier).isAnyOf("xxhdpi", "xhdpi")
            }
            Truth.assertThat(builtArtifact.attributes).containsEntry("deliveryType", "install-time")
        }
    }

    @Test
    fun testVersion2Loading() {
        val listingFile = tmpFolder.newFile()
        listingFile.writeText(
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
      "versionCode": 123,
      "versionName": "version_name",
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
      "versionCode": 123,
      "versionName": "version_name",
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
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "file3.apk"
    }
  ]
}"""
        )

        val builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(
                listingFile,
                NullLogger()
        )
        Truth.assertThat(builtArtifacts).isNotNull()
        Truth.assertThat(builtArtifacts!!.version).isEqualTo(2)
        Truth.assertThat(builtArtifacts.elements.size).isEqualTo(3)

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

    @Test
    fun testMixedFileTypes() {
        val folder = tmpFolder.newFolder()
        val file = tmpFolder.newFile()
        try {
            createBuiltArtifacts(
                    BuiltArtifactImpl.make(folder.absolutePath),
                    BuiltArtifactImpl.make(file.absolutePath)
            ).saveToFile(tmpFolder.newFile())
        } catch(e: IllegalArgumentException) {
            Truth.assertThat(e.message).contains("${file.name} is a file")
            Truth.assertThat(e.message).contains("${folder.name} is a directory")
            return
        }
        fail("exception not thrown")
    }

    @Test
    fun testMixedFileTypesWithMultipleFiles() {
        val folder = tmpFolder.newFolder()
        val fileOne = tmpFolder.newFile()
        val fileTwo = tmpFolder.newFile()
        try {
            createBuiltArtifacts(
                    BuiltArtifactImpl.make(folder.absolutePath),
                    BuiltArtifactImpl.make(fileOne.absolutePath),
                    BuiltArtifactImpl.make(fileTwo.absolutePath)
            ).saveToFile(tmpFolder.newFile())
        } catch(e: IllegalArgumentException) {
            Truth.assertThat(e.message).contains(
                    "${fileOne.name},${fileTwo.name} are files")
            Truth.assertThat(e.message).contains("${folder.name} is a directory")
            return
        }
        fail("exception not thrown")
    }

    @Test
    fun testMixedFileTypesWithMultipleDirectories() {
        val folderOne = tmpFolder.newFolder()
        val folderTwo = tmpFolder.newFolder()
        val file = tmpFolder.newFile()
        try {
            createBuiltArtifacts(
                    BuiltArtifactImpl.make(folderOne.absolutePath),
                    BuiltArtifactImpl.make(folderTwo.absolutePath),
                    BuiltArtifactImpl.make(file.absolutePath)
            ).saveToFile(tmpFolder.newFile())
        } catch(e: IllegalArgumentException) {
            Truth.assertThat(e.message).contains(
                    "${folderOne.name},${folderTwo.name} are directories")
            Truth.assertThat(e.message).contains("${file.name} is a file")
            return
        }
        fail("exception not thrown")
    }

    @Test
    fun testIncrementalBuildWithFilters() {
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

        val updatedArtifacts = artifacts.addElement(createBuiltArtifact(
            outputFolder = tmpFolder.root, fileName = "file2b",
            versionCode = 123, densityValue = "xxhdpi"
        ))

        Truth.assertThat(updatedArtifacts.elements).hasSize(3)
        val updatedArtifact = updatedArtifacts.getBuiltArtifact(VariantOutputConfigurationImpl(
            isUniversal = false,
            filters = listOf(
                FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xxhdpi")
            )),
        )
        Truth.assertThat(updatedArtifact?.outputFile).endsWith("file2b.apk")
    }

    @Test
    fun testIncrementalBuildWithoutFilters() {
        val artifacts = createBuiltArtifacts(
            BuiltArtifactImpl.make(
                outputFile = createOutputFile(tmpFolder.root, "main.apk").absolutePath,
                versionCode = 123,
                versionName = "123",
                variantOutputConfiguration = VariantOutputConfigurationImpl(
                    isUniversal = true,
                    filters = listOf()),
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

        val updatedArtifacts = artifacts.addElement(BuiltArtifactImpl.make(
            outputFile = createOutputFile(tmpFolder.root, "main2b.apk").absolutePath,
            versionCode = 123,
            versionName = "123",
            variantOutputConfiguration = VariantOutputConfigurationImpl(
                isUniversal = true,
                filters = listOf()),
        ))

        Truth.assertThat(updatedArtifacts.elements).hasSize(3)
        val updatedArtifact = updatedArtifacts.getBuiltArtifact(VariantOutputConfigurationImpl(
            isUniversal = true),
        )
        Truth.assertThat(updatedArtifact?.outputFile).endsWith("main2b.apk")
    }

    /**
     * Test that passing a redirect file to the [GenericBuiltArtifactsLoader] is functional.
     */
    @Test
    fun testRedirect() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        BuiltArtifactsImpl(
            artifactType = SingleArtifact.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl.make(
                    outputFile = createOutputFile(outputFolder, "file1.apk").absolutePath,
                    versionCode = 123,
                    versionName = "version_name"
                )
            )
        ).save(FakeGradleDirectory(outputFolder))
        val listingFile = File(outputFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)

        // now writes the file redirect.
        val redirectFolder = tmpFolder.newFolder("redirect_folder")
        val redirectFile = File(redirectFolder, BuiltArtifactsImpl.REDIRECT_FILE_NAME)
        ListingFileRedirect.writeRedirect(listingFile, redirectFile)

        Truth.assertThat(redirectFile.exists()).isTrue()
        val loadedFromFile = GenericBuiltArtifactsLoader.loadFromFile(redirectFile, NullLogger())
        // perform some sanity that json file got loaded correctly.
        Truth.assertThat(loadedFromFile).isNotNull()
        Truth.assertThat(loadedFromFile?.artifactType?.type).isEqualTo( SingleArtifact.APK.name())
        Truth.assertThat(loadedFromFile?.applicationId).isEqualTo("com.android.test")
        Truth.assertThat(loadedFromFile?.elements?.size).isEqualTo(1)

    }

    private fun createBuiltArtifact(
        outputFolder: File,
        fileName: String,
        versionCode: Int,
        densityValue: String,
        attributes: Map<String, String> = mapOf()
    ) =
        BuiltArtifactImpl.make(
            outputFile = createOutputFile(outputFolder, "$fileName.apk").absolutePath,
            versionCode = versionCode,
            versionName = versionCode.toString(),
            variantOutputConfiguration = VariantOutputConfigurationImpl(
                isUniversal = false,
                filters = listOf(
                    FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, densityValue)
                )),
            attributes = attributes
        )

    private fun createBuiltArtifacts(vararg elements: BuiltArtifactImpl) =
        BuiltArtifactsImpl(
            artifactType = SingleArtifact.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = elements.toList()
        )

    private fun createOutputFile(outputFolder: File, name: String): File =
        File(outputFolder, name).also {
            it.writeText("SomeFile")
        }
}
