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
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.workeractions.AgpWorkAction
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.utils.NullLogger
import com.google.common.truth.Truth
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.io.File
import javax.inject.Inject

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
                BuiltArtifactImpl(
                    outputFile = File(outputFolder, "file1.apk").toPath(),
                    properties = mapOf(),
                    versionCode = 123,
                    versionName = "version_name",
                    isEnabled = true,
                    outputType = VariantOutputConfiguration.OutputType.SINGLE,
                    filters = listOf(),
                    baseName = "someBaseName",
                    fullName = "someFullName"
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
      "baseName": "someBaseName",
      "fullName": "someFullName",
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
      "baseName": "someBaseName",
      "fullName": "someFullName",
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
            Truth.assertThat(builtArtifact.outputFile.fileName.toString())
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
    fun testPublicWorkItemsSubmission() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        val sourceArtifacts = createBuiltArtifacts(
            createBuiltArtifact(
                outputFolder = outputFolder,
                fileName = "file1", versionCode = 123, densityValue = "xhdpi"
            ),
            createBuiltArtifact(
                outputFolder = outputFolder, fileName = "file2",
                versionCode = 123, densityValue = "xxhdpi"
            ),
            createBuiltArtifact(
                outputFolder = outputFolder, fileName = "file3",
                versionCode = 123, densityValue = "xxxhdpi"
            )
        )

        class Parameters : BuiltArtifacts.TransformParams {
            override lateinit var output: File
        }

        class TestAction: WorkAction<Parameters> {
            var params: Parameters? = null
            override fun getParameters(): Parameters = params!!
            override fun execute() {
                Truth.assertThat(parameters.output).isNotNull()
            }
        }
        
        class FakeGradleWorkQueue: WorkQueue {
            override fun <T : WorkParameters?> submit(
                p0: Class<out WorkAction<T>>?,
                p1: Action<in T>?
            ) {
                val testAction = p0?.newInstance() as TestAction
                val parameters = Parameters()
                @Suppress("UNCHECKED_CAST")
                p1?.execute(parameters as T)
                testAction.params = parameters
                testAction.execute()
            }

            override fun await() {}
        }

        val workQueue = FakeGradleWorkQueue()

        val updatedBuiltArtifacts = sourceArtifacts.transform(
            InternalArtifactType.PACKAGED_RES,
            workQueue,
            TestAction::class.java) { builtArtifact, parameters ->
                parameters.output = tmpFolder.newFile(
                            builtArtifact.filters.joinToString { it.toString() })
            }
        val updatedArtifacts = updatedBuiltArtifacts.get()
        Truth.assertThat(updatedArtifacts.applicationId).isEqualTo(sourceArtifacts.applicationId)
        Truth.assertThat(updatedArtifacts.artifactType).isEqualTo(InternalArtifactType.PACKAGED_RES)
        Truth.assertThat(updatedArtifacts.variantName).isEqualTo(sourceArtifacts.variantName)
        Truth.assertThat(updatedArtifacts.elements.count()).isEqualTo(sourceArtifacts.elements.count())
        updatedArtifacts.elements.forEach { updatedArtifact ->
            Truth.assertThat(updatedArtifact.outputFile.fileName.toString())
                .isEqualTo(updatedArtifact.filters.joinToString { it.toString() })
        }
    }

    @Test
    fun testAgpInternalWorkItemsSubmission() {
        val outputFolder = tmpFolder.newFolder("some_folder")
        val sourceArtifacts = createBuiltArtifacts(
            createBuiltArtifact(
                outputFolder = outputFolder,
                fileName = "file1", versionCode = 123, densityValue = "xhdpi"
            ),
            createBuiltArtifact(
                outputFolder = outputFolder, fileName = "file2",
                versionCode = 123, densityValue = "xxhdpi"
            ),
            createBuiltArtifact(
                outputFolder = outputFolder, fileName = "file3",
                versionCode = 123, densityValue = "xxxhdpi"
            )
        )

        // Work item parameters
        class Parameters : BuiltArtifacts.TransformParams {
            override lateinit var output: File
        }

        // work item implementation.
        class TestAction: AgpWorkAction<Parameters>() {
            override fun execute() {
                Truth.assertThat(parameters.output).isNotNull()
            }
        }

        // Concrete parameter class to make Gradle Instantiator happy.
        class ConcreteAdaptedParameters: WorkActionAdapter.Parameters<Parameters>()

        // Concrete ActionAdapter to also make Gradle Instantiator happy
        class ConcreteActionAdapter @Inject constructor(objectFactory: ObjectFactory)
            : WorkActionAdapter<Parameters, ConcreteAdaptedParameters>(objectFactory) {
            lateinit var params: ConcreteAdaptedParameters
            override fun getParameters(): ConcreteAdaptedParameters = params
        }

        // fake object factory that can only create the work item instances.
        val objectFactory = Mockito.mock(ObjectFactory::class.java)
        val parameterArgumentCaptor = ArgumentCaptor.forClass(Class::class.java)
        Mockito.`when`(objectFactory.newInstance(parameterArgumentCaptor.capture())).thenReturn(
            TestAction()
        )

        // fake WorkQueue that will create the adapted work items and execute them serially
        class FakeGradleWorkQueue: WorkQueue {
            override fun <T : WorkParameters?> submit(
                p0: Class<out WorkAction<T>>?,
                p1: Action<in T>?
            ) {
                val testAction = ConcreteActionAdapter(objectFactory)
                val parameters = ConcreteAdaptedParameters()
                testAction.params = parameters
                @Suppress("UNCHECKED_CAST")
                p1?.execute(parameters as T)
                testAction.execute()
            }

            override fun await() {}
        }

        val workQueue = FakeGradleWorkQueue()

        val updatedBuiltArtifacts = sourceArtifacts.transform(
            InternalArtifactType.PACKAGED_RES,
            workQueue,
            TestAction::class.java,
            ConcreteActionAdapter::class.java
            ) { builtArtifact: BuiltArtifact ->
            Parameters().also {parameters ->
                parameters.output = tmpFolder.newFile(
                            builtArtifact.filters.joinToString { it.toString() })
            }
        }
        val updatedArtifacts = updatedBuiltArtifacts.get()
        Truth.assertThat(updatedArtifacts.applicationId).isEqualTo(sourceArtifacts.applicationId)
        Truth.assertThat(updatedArtifacts.artifactType).isEqualTo(InternalArtifactType.PACKAGED_RES)
        Truth.assertThat(updatedArtifacts.variantName).isEqualTo(sourceArtifacts.variantName)
        Truth.assertThat(updatedArtifacts.elements.count()).isEqualTo(sourceArtifacts.elements.count())
        updatedArtifacts.elements.forEach { updatedArtifact ->
            Truth.assertThat(updatedArtifact.outputFile.fileName.toString())
                .isEqualTo(updatedArtifact.filters.joinToString { it.toString() })
        }
        Truth.assertThat(parameterArgumentCaptor.value.simpleName)
            .isEqualTo("testAgpInternalWorkItemsSubmission\$TestAction")
    }

    private fun createBuiltArtifact(
        outputFolder: File,
        fileName: String,
        versionCode: Int,
        densityValue: String
    ) =
        BuiltArtifactImpl(
            outputFile = File(outputFolder, "$fileName.apk").toPath(),
            properties = mapOf("key1" to "value1", "key2" to "value2"),
            versionCode = versionCode,
            versionName = versionCode.toString(),
            isEnabled = true,
            outputType = VariantOutputConfiguration.OutputType.ONE_OF_MANY,
            filters = listOf(
                FilterConfiguration(FilterConfiguration.FilterType.DENSITY, densityValue)
            ),
            baseName = "someBaseName",
            fullName = "someFullName"
        )

    private fun createBuiltArtifacts(vararg elements: BuiltArtifactImpl) =
        BuiltArtifactsImpl(
            artifactType = PublicArtifactType.APK,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = elements.toList()
        )
}