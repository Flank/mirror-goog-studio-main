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
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BuiltArtifactsLoaderImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @get:Rule
    val outFolder = TemporaryFolder()

    @Test
    fun testSingleFileTransformation() {
        createSimpleMetadataFile()

        val builtArtifacts= BuiltArtifacts.Loader.load(
            FakeGradleDirectory(tmpFolder.root))

        assertThat(builtArtifacts).isNotNull()

        val newBuiltArtifacts = builtArtifacts!!.transform(PublicArtifactType.APK) {
            assertThat(it.readText(Charsets.UTF_8)).isEqualTo("some manifest")
            outFolder.newFile("${it.name}.new").also { file ->
                file.writeText("updated APK")
            }
        }

        newBuiltArtifacts.save(FakeGradleDirectory(outFolder.root))

        // load the new file
        val updatedBuiltArtifacts = BuiltArtifacts.Loader.load(
            FakeGradleDirectory(outFolder.root))

        assertThat(updatedBuiltArtifacts).isNotNull()

        assertThat(updatedBuiltArtifacts!!.applicationId).isEqualTo(builtArtifacts.applicationId)
        assertThat(updatedBuiltArtifacts.variantName).isEqualTo(builtArtifacts.variantName)
        assertThat(updatedBuiltArtifacts.artifactType).isEqualTo(PublicArtifactType.APK)
        assertThat(updatedBuiltArtifacts.elements).hasSize(1)
        val updatedBuiltArtifact = updatedBuiltArtifacts.elements.first()
        assertThat(updatedBuiltArtifact.outputFile.toFile().name).isEqualTo("file1.xml.new")
        assertThat(updatedBuiltArtifact.versionCode).isEqualTo(123)
        assertThat(updatedBuiltArtifact.versionName).isEqualTo("version_name")
        assertThat(updatedBuiltArtifact.outputType).isEqualTo(VariantOutputConfiguration.OutputType.SINGLE)
        assertThat(updatedBuiltArtifact.filters).isEmpty()
        assertThat(updatedBuiltArtifact.properties).isEmpty()
    }

    @Test
    fun testMultipleFileTransformation() {
        tmpFolder.newFile("file1.xml").writeText("xxxhdpi")
        tmpFolder.newFile("file2.xml").writeText("xhdpi")
        tmpFolder.newFile(BuiltArtifactsImpl.METADATA_FILE_NAME).writeText(
            """{
  "version": 1,
  "artifactType": {
    "type": "MERGED_MANIFESTS",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xxxhdpi"
        }
      ],      "versionCode": 123,
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file1.xml"
    },
    {
      "type": "SINGLE",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xhdpi"
        }
      ],
      "versionCode": 123,
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file2.xml"
    }
  ]
}""", Charsets.UTF_8)

        val builtArtifacts= BuiltArtifacts.Loader.load(
            FakeGradleDirectory(tmpFolder.root))

        assertThat(builtArtifacts).isNotNull()
        val newBuiltArtifacts = builtArtifacts!!.transform(PublicArtifactType.APK) {
            val manifestContent = it.readText(Charsets.UTF_8)
            outFolder.newFile("${it.name}.new").also { file ->
                file.writeText("updated APK : $manifestContent")
            }
        }

        newBuiltArtifacts.save(FakeGradleDirectory(outFolder.root))

        // load the new file
        val updatedBuiltArtifacts = BuiltArtifacts.Loader.load(
            FakeGradleDirectory(outFolder.root))

        assertThat(updatedBuiltArtifacts).isNotNull()
        assertThat(updatedBuiltArtifacts!!.applicationId).isEqualTo(builtArtifacts.applicationId)
        assertThat(updatedBuiltArtifacts.variantName).isEqualTo(builtArtifacts.variantName)
        assertThat(updatedBuiltArtifacts.artifactType).isEqualTo(PublicArtifactType.APK)
        assertThat(updatedBuiltArtifacts.elements).hasSize(2)
        updatedBuiltArtifacts.elements.forEach { builtArtifact ->
            assertThat(builtArtifact.filters).hasSize(1)
            assertThat(builtArtifact.filters.first().filterType).isEqualTo(
                FilterConfiguration.FilterType.DENSITY)
            val filterValue = builtArtifact.filters.first().identifier
            assertThat(builtArtifact.outputFile.toFile().readText(Charsets.UTF_8)).isEqualTo(
                "updated APK : $filterValue"
            )
        }
    }

    @Test
    fun testSimpleLoading() {
        createSimpleMetadataFile()

        val builtArtifacts= BuiltArtifacts.Loader.load(
            FakeGradleDirectory(tmpFolder.root))

        assertThat(builtArtifacts).isNotNull()
        assertThat(builtArtifacts!!.artifactType).isEqualTo(InternalArtifactType.MERGED_MANIFESTS)
        assertThat(builtArtifacts.applicationId).isEqualTo("com.android.test")
        assertThat(builtArtifacts.variantName).isEqualTo("debug")
        assertThat(builtArtifacts.elements).hasSize(1)
        val builtArtifact = builtArtifacts.elements.first()
        assertThat(builtArtifact.outputFile).isEqualTo(File(tmpFolder.root, "file1.xml").toPath())
        assertThat(builtArtifact.isEnabled).isTrue()
        assertThat(builtArtifact.versionCode).isEqualTo(123)
        assertThat(builtArtifact.versionName).isEqualTo("version_name")
        assertThat(builtArtifact.outputType).isEqualTo(VariantOutputConfiguration.OutputType.SINGLE)
    }

    private fun createSimpleMetadataFile() {
        tmpFolder.newFile("file1.xml").writeText("some manifest")
        tmpFolder.newFile(BuiltArtifactsImpl.METADATA_FILE_NAME).writeText(
            """{
  "version": 1,
  "artifactType": {
    "type": "MERGED_MANIFESTS",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "versionCode": 123,
      "versionName": "version_name",
      "enabled": true,
      "outputFile": "file1.xml"
    }
  ]
}""", Charsets.UTF_8)
    }
}