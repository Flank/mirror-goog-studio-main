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

import com.android.build.gradle.tasks.VerifyLibraryResourcesTask
import com.android.builder.files.SerializableChange
import com.android.builder.files.SerializableInputChanges
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.CopyToOutputDirectoryResourceCompilationService
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/*
 * Unit tests for {@link VerifyLibraryResourcesTask}.
 */
class VerifyLibraryResourcesTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

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

        val compilationService = CopyToOutputDirectoryResourceCompilationService
        VerifyLibraryResourcesTask.compileResources(
            inputs = SerializableInputChanges(roots = listOf(mergedDir), changes = inputs),
            outDirectory = outputDir,
            mergeBlameFolder = temporaryFolder.newFolder(),
            compilationService = compilationService
        )

        val fileOut = compilationService.compileOutputFor(CompileResourceRequest(file, outputDir, "values"))
        assertTrue(fileOut.exists())

        val dirOut = compilationService.compileOutputFor(
                CompileResourceRequest(invalidFile, outputDir, mergedDir.name))
        assertFalse(dirOut.exists())
    }

}
