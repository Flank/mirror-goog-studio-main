/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.ResourceCompilerRunnable
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask
import com.android.builder.files.SerializableChange
import com.android.builder.files.SerializableInputChanges
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.Serializable

/*
 * Unit tests for {@link VerifyLibraryResourcesTask}.
 */
class VerifyLibraryResourcesTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    object Facade : WorkerExecutorFacade {
        override fun submit(actionClass: Class<out Runnable>, parameter: Serializable) {
            assertThat(actionClass).isIn(
              listOf(Aapt2CompileRunnable::class.java, ResourceCompilerRunnable::class.java))
            if (actionClass == Aapt2CompileRunnable::class.java) {
                parameter as Aapt2CompileRunnable.Params
                for (request in parameter.requests) {
                    Files.copy(request.inputFile, compileOutputFor(request))
                }
            } else {
                parameter as ResourceCompilerRunnable.Params
                Files.copy(parameter.request.inputFile, compileOutputFor(parameter.request))
            }
        }

        override fun await() {}

        override fun close() {}

        fun compileOutputFor(request: CompileResourceRequest): File {
            return File(request.outputDirectory, request.inputFile.name + "-c")
        }
    }

    @Test
    fun otherFilesShouldBeIgnored() {
        val inputs = mutableListOf<SerializableChange>()
        val mergedDir = File(temporaryFolder.newFolder("merged"), "release")
        FileUtils.mkdirs(mergedDir)

        val relativeFilePath = "values/file.xml"
        val file = File(mergedDir, relativeFilePath)
        FileUtils.createFile(file, "content")
        assertTrue(file.exists())
        inputs.add(SerializableChange(file, FileStatus.NEW, relativeFilePath))

        val invalidFilePath = "values/invalid/invalid.xml"
        val invalidFile = File(mergedDir, invalidFilePath)
        FileUtils.createFile(invalidFile, "content")
        assertTrue(invalidFile.exists())
        inputs.add(SerializableChange(invalidFile, FileStatus.NEW, invalidFilePath))

        val invalidFilePath2 = "invalid.xml"
        val invalidFile2 = File(mergedDir, invalidFilePath2)
        FileUtils.createFile(invalidFile2, "content")
        assertTrue(invalidFile2.exists())
        inputs.add(SerializableChange(invalidFile2, FileStatus.NEW, invalidFilePath2))

        val outputDir = temporaryFolder.newFolder("output")

        VerifyLibraryResourcesTask.compileResources(
            SerializableInputChanges(listOf(mergedDir), inputs),
            outputDir,
            Facade,
            mock(Aapt2DaemonServiceKey::class.java),
            SyncOptions.ErrorFormatMode.HUMAN_READABLE,
            temporaryFolder.newFolder(),
            false
        )

        val fileOut = Facade.compileOutputFor(CompileResourceRequest(file, outputDir, "values"))
        assertTrue(fileOut.exists())

        val dirOut = Facade.compileOutputFor(
                CompileResourceRequest(invalidFile, outputDir, mergedDir.name))
        assertFalse(dirOut.exists())
    }

}
