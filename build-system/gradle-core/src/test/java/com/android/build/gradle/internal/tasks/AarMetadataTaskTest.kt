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

package com.android.build.gradle.internal.tasks

import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.Properties

/**
 * Unit tests for [AarMetadataTask].
 */
class AarMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var workerExecutor: WorkerExecutor

    private lateinit var task: AarMetadataTask
    private lateinit var outputFile: File

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("aarMetadataTask", AarMetadataTask::class.java)
        task = taskProvider.get()
        val workQueue = object: WorkQueue {
            override fun <T : WorkParameters?> submit(
                workActionClass: Class<out WorkAction<T>>?,
                parameterAction: Action<in T>?
            ) {
                val workParameters =
                    project.objects.newInstance(AarMetadataWorkParameters::class.java)
                @Suppress("UNCHECKED_CAST")
                parameterAction?.execute(workParameters as T)
                workActionClass?.let { project.objects.newInstance(it, workParameters).execute() }
            }

            override fun await() {}
        }
        Mockito.`when`(workerExecutor.noIsolation()).thenReturn(workQueue)
        task.workerExecutorProperty.set(workerExecutor)
        outputFile = temporaryFolder.newFile("AarMetadata.xml")
    }

    @Test
    fun testBasic() {
        task.output.set(outputFile)
        task.aarMetadataVersion.set(AarMetadataTask.aarMetadataVersion)
        task.aarVersion.set(AarMetadataTask.aarVersion)
        task.taskAction()

        checkAarMetadataFile(
            outputFile,
            AarMetadataTask.aarMetadataVersion,
            AarMetadataTask.aarVersion
        )
    }

    @Test
    fun testMinCompileSdkVersion() {
        task.output.set(outputFile)
        task.aarMetadataVersion.set(AarMetadataTask.aarMetadataVersion)
        task.aarVersion.set(AarMetadataTask.aarVersion)
        task.minCompileSdk.set(28)
        task.taskAction()

        checkAarMetadataFile(
            outputFile,
            AarMetadataTask.aarMetadataVersion,
            AarMetadataTask.aarVersion,
            minCompileSdk = "28"
        )
    }

    private fun checkAarMetadataFile(
        file: File,
        aarMetadataVersion: String,
        aarVersion: String,
        minCompileSdk: String? = null
    ) {
        assertThat(file).exists()
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        assertThat(properties.getProperty("aarMetadataVersion")).isEqualTo(aarMetadataVersion)
        assertThat(properties.getProperty("aarVersion")).isEqualTo(aarVersion)
        assertThat(properties.getProperty("minCompileSdk")).isEqualTo(minCompileSdk)
    }
}
