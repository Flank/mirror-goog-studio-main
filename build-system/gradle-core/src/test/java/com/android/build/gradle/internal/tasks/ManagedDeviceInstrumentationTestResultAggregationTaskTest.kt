/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.testing.utp.TEST_RESULT_PB_FILE_NAME
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.logging.Logger
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.api.specs.Spec
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mock
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit

/**
 * Unit tests for [ManagedDeviceInstrumentationTestResultAggregationTask].
 */
class ManagedDeviceInstrumentationTestResultAggregationTaskTest {

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    var temporaryFolderRule = TemporaryFolder()

    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var creationConfig: VariantCreationConfig

    @Before
    fun setUpMocks() {
        `when`(creationConfig.computeTaskName(any(), any())).then {
            val prefix = it.getArgument<String>(0)
            val suffix = it.getArgument<String>(1)
            "${prefix}AndroidDebugTest${suffix}"
        }
        `when`(creationConfig.name).thenReturn("AndroidDebugTest")
        `when`(creationConfig.services.buildServiceRegistry
               .registrations.getByName(any()))
            .thenReturn(
                mock(BuildServiceRegistration::class.java, RETURNS_DEEP_STUBS))
    }

    @Test
    fun creationTask() {
        val rootResultsDir = temporaryFolderRule.newFolder("rootResultsDir")
        val pixel3Dir = File(rootResultsDir, "Pixel3").apply { mkdirs() }
        val action = ManagedDeviceInstrumentationTestResultAggregationTask.CreationAction(
            creationConfig,
            listOf(File(pixel3Dir, TEST_RESULT_PB_FILE_NAME)),
            File(rootResultsDir, TEST_RESULT_PB_FILE_NAME)
        )

        assertThat(action.name)
            .isEqualTo("mergeAndroidDebugTestTestResultProtos")
        assertThat(action.type)
            .isEqualTo(ManagedDeviceInstrumentationTestResultAggregationTask::class.java)
    }

    @Test
    fun configureTaskByCreationTask() {
        val rootResultsDir = temporaryFolderRule.newFolder("rootResultsDir")
        val pixel3Dir = File(rootResultsDir, "Pixel3").apply { mkdirs() }
        val action = ManagedDeviceInstrumentationTestResultAggregationTask.CreationAction(
            creationConfig,
            listOf(File(pixel3Dir, TEST_RESULT_PB_FILE_NAME)),
            File(rootResultsDir, TEST_RESULT_PB_FILE_NAME)
        )
        val task = mock(
            ManagedDeviceInstrumentationTestResultAggregationTask::class.java,
            RETURNS_DEEP_STUBS)

        `when`(task.project.buildDir).thenReturn(File("buildDir"))

        action.configure(task)

        verify(task.inputTestResultProtos).from(eq(listOf(File(pixel3Dir, "test-result.pb"))))
    }

    @Test
    fun taskAction() {
        val task = mock(
            ManagedDeviceInstrumentationTestResultAggregationTask::class.java,
            CALLS_REAL_METHODS)
        `when`(task.analyticsService).thenReturn(MockitoKt.mock())
        doReturn("path").`when`(task).path
        doReturn(mock(TaskOutputsInternal::class.java, RETURNS_DEEP_STUBS))
            .`when`(task).outputs
        doReturn(MockitoKt.mock<Logger>()).`when`(task).logger

        val inputFiles = mock(ConfigurableFileCollection::class.java)
        `when`(inputFiles.filter(any<Spec<File>>())).thenReturn(
            FakeConfigurableFileCollection(createResultProto(), createResultProto()))
        `when`(inputFiles.isEmpty).thenReturn(false)
        doReturn(inputFiles).`when`(task).inputTestResultProtos

        val outputFile = temporaryFolderRule.newFile()
        val outputFileProperty = mock(
            RegularFileProperty::class.java, RETURNS_DEEP_STUBS)
        `when`(outputFileProperty.get().asFile).thenReturn(outputFile)
        doReturn(outputFileProperty).`when`(task).outputTestResultProto

        task.taskAction()

        val mergedResult = TestSuiteResult.parseFrom(outputFile.inputStream())
        assertThat(mergedResult.testSuiteMetaData.scheduledTestCaseCount)
            .isEqualTo(2)
    }

    private fun createResultProto(): File {
        val protoFile = temporaryFolderRule.newFile()
        TestSuiteResult.newBuilder().apply {
            testSuiteMetaDataBuilder.apply {
                scheduledTestCaseCount = 1
            }
        }.build().writeTo(protoFile.outputStream())
        return protoFile
    }
}
