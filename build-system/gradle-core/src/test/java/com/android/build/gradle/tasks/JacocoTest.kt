/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.fixtures.FakeIncrementalTaskInputs
import com.android.build.gradle.internal.fixtures.createBuildArtifact
import com.android.build.gradle.internal.tasks.JacocoTaskDelegate
import com.android.build.gradle.internal.tasks.Workers
import com.android.testutils.truth.PathSubject.assertThat
import org.gradle.workers.WorkerExecutor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File

class JacocoTest {

    @Mock
    lateinit var workerExecutor: WorkerExecutor

    @Rule
    @JvmField
    var tmp = TemporaryFolder()

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        Workers.useDirectWorkerExecutor= true
    }

    @After
    fun tearDown() {
        Workers.useDirectWorkerExecutor= false
    }

    @Test
    fun testCopyFiles() {
        val inputDir = tmp.newFolder()
        inputDir.resolve("META-INF").mkdirs()
        inputDir.resolve("META-INF/copiedFile.kotlin_module").createNewFile()
        inputDir.resolve("META-INF/MANIFEST.MF").createNewFile()

        val outputDir = tmp.newFolder()
        val jacocoDelegate = JacocoTaskDelegate(
            FakeFileCollection(), outputDir, createBuildArtifact(inputDir)
        )
        jacocoDelegate.run(
            Workers.preferWorkers("test", "test", workerExecutor),
            FakeIncrementalTaskInputs())

        assertThat(File(outputDir, "META-INF/copiedFile.kotlin_module")).exists();
        assertThat(File(outputDir, "META-INF/MANIFEST.MF")).doesNotExist();
    }
}
