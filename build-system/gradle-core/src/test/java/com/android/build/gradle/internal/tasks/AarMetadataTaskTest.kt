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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [AarMetadataTask].
 */
class AarMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var workQueue: WorkQueue
    private lateinit var outputFile: File

    @Before
    fun setUp() {

        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        workQueue = object: WorkQueue {
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

        outputFile = temporaryFolder.newFile("AarMetadata.xml")
    }

    @Test
    fun testBasic() {
        AarMetadataTaskDelegate(
            workQueue,
            outputFile,
            AarMetadataTask.aarVersion,
            AarMetadataTask.aarMetadataVersion
        ).run()

        assertThat(outputFile).exists()
        assertThat(outputFile.readText()).isEqualTo(
            """
                |<aar-metadata
                |    aarVersion="1.0"
                |    aarMetadataVersion="1.0" />
                |""".trimMargin()
        )
    }
}
