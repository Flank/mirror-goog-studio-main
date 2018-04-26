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

package com.android.build.gradle.internal.tasks.featuresplit

import com.android.testutils.truth.FileSubject
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

/** Tests for the [FeatureSetMetadataWriterTask] class  */
class FeatureSetMetadataWriterTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    lateinit var project: Project
    lateinit var task: FeatureSetMetadataWriterTask

    @Mock lateinit var fileCollection: FileCollection
    @Mock lateinit var fileTree: FileTree
    val files = mutableSetOf<File>()

    @get:Rule
    val exception = ExpectedException.none()

    @Before
    @Throws(IOException::class)
    fun setUp() {

        MockitoAnnotations.initMocks(this)
        val testDir = temporaryFolder.newFolder()

        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", FeatureSetMetadataWriterTask::class.java)
        task.outputFile = File(temporaryFolder.newFolder(), FeatureSetMetadata.OUTPUT_FILE_NAME)
        task.inputFiles = fileCollection

        `when`(fileCollection.asFileTree).thenReturn(fileTree)
        `when`(fileTree.files).thenReturn(files)
    }

    @Test
    @Throws(IOException::class)
    fun testTask() {
        val inputDirs = ImmutableSet.builder<File>()
        for (i in 0..4) {
            inputDirs.add(generateInputDir("id_" + i, "foo.bar.baz" + i))
        }
        files.addAll(inputDirs.build())

        task.fullTaskAction()
        FileSubject.assertThat(task.outputFile).isFile()

        val loaded = FeatureSetMetadata.load(task.outputFile)
        for (i in 0..4) {
            assertThat(loaded.getResOffsetFor("id_" + i)).isEqualTo(FeatureSetMetadata.BASE_ID + i)
        }
    }

    @Test
    fun testComputeFeatureNames() {
        val features =
                listOf(
                        FeatureSplitDeclaration(":A", "id"),
                        FeatureSplitDeclaration(":foo:B", "id"),
                        FeatureSplitDeclaration(":C", "id"))

        assertThat(task.computeFeatureNames(features).values).containsExactly("A", "B", "C")
    }

    @Test
    fun testDuplicatedFeatureNames() {
        val features =
            listOf(
                FeatureSplitDeclaration(":A", "id"),
                FeatureSplitDeclaration(":foo:A", "id"))

        exception.expect(RuntimeException::class.java)
        task.computeFeatureNames(features)
    }

    @Throws(IOException::class)
    private fun generateInputDir(id: String, appId: String): File {
        val inputDir = temporaryFolder.newFolder()
        val featureSplitDeclaration = FeatureSplitDeclaration(id, appId)
        featureSplitDeclaration.save(inputDir)
        return FeatureSplitDeclaration.getOutputFile(inputDir)
    }
}
