/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.profile

import com.android.builder.profile.Recorder
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType
import com.google.common.base.Joiner
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

class AnalyticsResourceManagerTest {

    @get:Rule
    var outputDir = TemporaryFolder()

    private lateinit var profileDir: File
    private lateinit var resourceManager: AnalyticsResourceManager

    private val finishEvent = Mockito.mock(TaskFinishEvent::class.java)
    private val operationDescriptor = Mockito.mock(TaskOperationDescriptor::class.java)
    private val taskPath = "taskPath"
    private var finishResult = Mockito.mock(TaskSuccessResult::class.java)
    private val startTime = 1L
    private val endTime = 2L
    private val projectPath = "projectPath"
    private val variantName = "variantName"
    private val projectId = 3L
    private val variantId = 4L
    private val typeName = GradleTaskExecutionType.AIDL_COMPILE.name
    private val secondTaskPath = "secondTaskPath"
    private val secondTypeName = "secondTypeName"

    @Before
    fun setUp() {
        Mockito.doReturn(operationDescriptor).`when`(finishEvent).descriptor
        Mockito.doReturn(taskPath).`when`(operationDescriptor).taskPath
        Mockito.doReturn(finishResult).`when`(finishEvent).result
        Mockito.doReturn(false).`when`(finishResult).isUpToDate
        Mockito.doReturn(startTime).`when`(finishResult).startTime
        Mockito.doReturn(endTime).`when`(finishResult).endTime

        profileDir = outputDir.newFolder("profile_proto")
        resourceManager = AnalyticsResourceManager(
            GradleBuildProfile.newBuilder(),
            getProjects(),
            true,
            profileDir,
            getTaskMetaData(),
            null
        )
    }

    @Test
    fun testRecordTaskExecutionSpan() {
        resourceManager.recordTaskExecutionSpan(finishEvent)
        Truth.assertThat(resourceManager.executionSpans.size).isEqualTo(1)
        val span = resourceManager.executionSpans.first()
        Truth.assertThat(span.id).isEqualTo(2)
        Truth.assertThat(span.parentId).isEqualTo(0)
        Truth.assertThat(span.threadId).isEqualTo(Thread.currentThread().id)
        Truth.assertThat(span.type).isEqualTo(GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION)
        Truth.assertThat(span.startTimeInMs).isEqualTo(startTime)
        Truth.assertThat(span.durationInMs).isEqualTo(endTime - startTime)

        val task = span.task
        Truth.assertThat(task.type).isEqualTo(1)
        Truth.assertThat(task.didWork).isTrue()
        Truth.assertThat(task.skipped).isFalse()
        Truth.assertThat(task.upToDate).isFalse()
        Truth.assertThat(task.failed).isFalse()
    }

    @Test
    fun testRecordBlockExecutionAtConfiguration() {
        resourceManager.recordBlockAtConfiguration(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
            projectPath,
            variantName,
            Recorder.VoidBlock {  }
        )
        Truth.assertThat(resourceManager.configurationSpans.size).isEqualTo(1)
        val span = resourceManager.configurationSpans.first()
        Truth.assertThat(span.id).isEqualTo(2)
        Truth.assertThat(span.parentId).isEqualTo(0)
        Truth.assertThat(span.type).isEqualTo(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING)
        Truth.assertThat(span.threadId).isNotEqualTo(0)
        Truth.assertThat(span.project).isEqualTo(projectId)
        Truth.assertThat(span.variant).isEqualTo(variantId)
    }

    @Test
    fun testRecordBlockExecutionAtExecution() {
        resourceManager.recordBlockAtExecution(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
            null,
            projectPath,
            variantName,
            Recorder.VoidBlock {  }
        )
        Truth.assertThat(resourceManager.executionSpans.size).isEqualTo(1)
        val span = resourceManager.executionSpans.first()
        Truth.assertThat(span.id).isEqualTo(2)
        Truth.assertThat(span.parentId).isEqualTo(0)
        Truth.assertThat(span.type).isEqualTo(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING)
        Truth.assertThat(span.threadId).isNotEqualTo(0)
    }

    @Test
    fun testRecordMixedExecutionSpans() {
        resourceManager.recordBlockAtExecution(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
            null,
            projectPath,
            variantName,
            Recorder.VoidBlock {  }
        )
        resourceManager.recordTaskExecutionSpan(finishEvent)
        Truth.assertThat(resourceManager.executionSpans.size).isEqualTo(2)
        val blockSpan = getRecordForId(resourceManager.executionSpans, 2)
        Truth.assertThat(blockSpan.parentId).isEqualTo(0)
        Truth.assertThat(blockSpan.type).isEqualTo(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING)
        Truth.assertThat(blockSpan.threadId).isNotEqualTo(0)

        val taskSpan = getRecordForId(resourceManager.executionSpans, 3)
        Truth.assertThat(taskSpan.parentId).isEqualTo(0)
        Truth.assertThat(taskSpan.threadId).isEqualTo(Thread.currentThread().id)
    }

    @Test
    fun testRecordMultipleTaskExecutionSpans() {
        val secondFinishEvent = Mockito.mock(TaskFinishEvent::class.java)
        val secondOperationDescriptor = Mockito.mock(TaskOperationDescriptor::class.java)
        val secondFinishResult = Mockito.mock(TaskSkippedResult::class.java)
        val secondStartTime = 3L
        val secondEndTime = 4L

        Mockito.doReturn(secondOperationDescriptor).`when`(secondFinishEvent).descriptor
        Mockito.doReturn(secondTaskPath).`when`(secondOperationDescriptor).taskPath
        Mockito.doReturn(secondFinishResult).`when`(secondFinishEvent).result
        Mockito.doReturn(secondStartTime).`when`(secondFinishResult).startTime
        Mockito.doReturn(secondEndTime).`when`(secondFinishResult).endTime

        resourceManager.recordTaskExecutionSpan(finishEvent)
        resourceManager.recordTaskExecutionSpan(secondFinishEvent)

        val spans = resourceManager.executionSpans
        Truth.assertThat(spans.size).isEqualTo(2)
        var taskSpan = getRecordForId(spans, 2)
        Truth.assertThat(taskSpan.parentId).isEqualTo(0)
        Truth.assertThat(taskSpan.threadId).isEqualTo(Thread.currentThread().id)

        taskSpan = getRecordForId(spans, 3)
        Truth.assertThat(taskSpan.parentId).isEqualTo(0)
        Truth.assertThat(taskSpan.project).isEqualTo(projectId)
        Truth.assertThat(taskSpan.threadId).isEqualTo(Thread.currentThread().id)
    }

    @Test
    fun testRecordNestedBlockExecutionSpan() {
        resourceManager.recordBlockAtConfiguration(
            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
            projectPath,
            variantName,
            Recorder.VoidBlock {
                resourceManager.recordBlockAtConfiguration(
                    GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
                    projectPath,
                    variantName,
                    Recorder.VoidBlock {
                        resourceManager.recordBlockAtConfiguration(
                            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
                            projectPath,
                            variantName,
                            Recorder.VoidBlock {  })
                        resourceManager.recordBlockAtConfiguration(
                            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
                            projectPath,
                            variantName,
                            Recorder.VoidBlock {  })
                        resourceManager.recordBlockAtConfiguration(
                            GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
                            projectPath,
                            variantName,
                            Recorder.VoidBlock {
                                resourceManager.recordBlockAtConfiguration(
                                    GradleBuildProfileSpan.ExecutionType.SOME_RANDOM_PROCESSING,
                                    projectPath,
                                    variantName,
                                    Recorder.VoidBlock {  }
                                )
                            }
                        )
                    }
                )
            }
        )
        val spans = resourceManager.configurationSpans
        Truth.assertThat(spans.size).isEqualTo(6)
        val records = mutableListOf<GradleBuildProfileSpan>()
        records.addAll(spans)
        records.sortBy { it.id }

        Truth.assertThat(records[0].id).isEqualTo(records[1].parentId)
        Truth.assertThat(records[1].id).isEqualTo(records[2].parentId)
        Truth.assertThat(records[1].id).isEqualTo(records[3].parentId)
        Truth.assertThat(records[1].id).isEqualTo(records[4].parentId)
        Truth.assertThat(records[4].id).isEqualTo(records[5].parentId)
        Truth.assertThat(records[1].durationInMs).isAtLeast(
            records[2].durationInMs + records[3].durationInMs + records[4].durationInMs)
        Truth.assertThat(records[4].durationInMs).isAtLeast(records[5].durationInMs)
    }

    @Test
    fun testRecordIdNumberingInMultiThreadInvocation() {
        val recordRunnable = Runnable {
            for (i in 0..19) {
                resourceManager
                    .recordBlockAtExecution(
                        GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION,
                        null,
                        projectPath,
                        variantName,
                        Recorder.VoidBlock {  }
                    )
            }
        }
        val threads = Stream.generate { Thread(recordRunnable) }
                .limit(20)
                .collect(Collectors.toList())
        threads.forEach( Consumer { obj: Thread -> obj.start() })
        for (thread in threads) {
            thread.join()
        }
        val spans = resourceManager.executionSpans
        Truth.assertThat(spans.size).isEqualTo(400)
        val spanIds = spans.map { it.id }.toList()
        Truth.assertThat(spanIds).containsNoDuplicates()
    }

    private fun getProjects(): ConcurrentHashMap<String, ProjectData> {
        val projects = ConcurrentHashMap<String, ProjectData>()
        val projectData = ProjectData(GradleBuildProject.newBuilder().setId(projectId))
        projectData.variantBuilders[variantName] = GradleBuildVariant.newBuilder().setId(variantId)
        projects[projectPath] = projectData
        return projects
    }

    private fun getTaskMetaData(): ConcurrentHashMap<String, TaskMetadata> {
        val taskMetadata = ConcurrentHashMap<String, TaskMetadata>()
        taskMetadata[taskPath] = TaskMetadata(projectPath, variantName, typeName)
        taskMetadata[secondTaskPath] = TaskMetadata(projectPath, variantName, secondTypeName)
        return taskMetadata
    }

    private fun getRecordForId(
        records: Collection<GradleBuildProfileSpan>,
        recordId: Long
    ): GradleBuildProfileSpan {
        for (record in records) {
            if (record.id == recordId) {
                return record
            }
        }
        throw AssertionError(
            "No record with id $recordId found in [${Joiner.on(", ").join(records)}]")
    }
}
