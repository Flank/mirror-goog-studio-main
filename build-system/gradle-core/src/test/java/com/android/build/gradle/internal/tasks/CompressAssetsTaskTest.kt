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

import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileType
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.ChangeType
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.function.Predicate
import java.util.zip.Deflater

/**
 * Unit tests for [CompressAssetsTask].
 */
class CompressAssetsTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var workQueue: WorkQueue
    private lateinit var inputDir: File
    private lateinit var asset1: File
    private lateinit var asset2: File
    private lateinit var noCompressAsset: File
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        workQueue = object: WorkQueue {
            override fun <T : WorkParameters?> submit(
                workActionClass: Class<out WorkAction<T>>?,
                parameterAction: Action<in T>?
            ) {
                val workParameters =
                    project.objects.newInstance(CompressAssetsWorkParameters::class.java)
                @Suppress("UNCHECKED_CAST")
                parameterAction?.execute(workParameters as T)
                workActionClass?.let { project.objects.newInstance(it, workParameters).execute() }
            }

            override fun await() {}
        }

        // create input dir with various assets, one of which won't be compressed
        inputDir = temporaryFolder.newFolder("inputDir")
        asset1 = File(inputDir, "asset1")
        FileUtils.createFile(asset1, "asset1")
        assertThat(asset1).exists()
        asset2 = FileUtils.join(inputDir, "dir", "asset2")
        FileUtils.createFile(asset2, "asset2")
        assertThat(asset2).exists()
        noCompressAsset = File(inputDir, "noCompressAsset")
        FileUtils.createFile(noCompressAsset, "noCompressAsset")
        assertThat(noCompressAsset).exists()

        outputDir = temporaryFolder.newFolder("outputDir")
    }

    @Test
    fun testBasic() {
        // first test non-incremental run
        val noCompressPredicate = Predicate<String> { it.endsWith("noCompressAsset") }
        val compressionLevel = Deflater.BEST_SPEED
        val nonIncrementalChanges =
            listOf(
                FakeFileChange(asset1, ChangeType.ADDED, FileType.FILE, "asset1"),
                FakeFileChange(asset2, ChangeType.ADDED, FileType.FILE, "dir/asset2"),
                FakeFileChange(noCompressAsset, ChangeType.ADDED, FileType.FILE, "noCompressAsset"),
                FakeFileChange(asset2.parentFile, ChangeType.ADDED, FileType.DIRECTORY, "dir")
            )
        CompressAssetsDelegate(
            workQueue,
            inputDir,
            outputDir,
            noCompressPredicate,
            compressionLevel,
            nonIncrementalChanges
        ).run()

        // check asset1
        val assetJar1 = FileUtils.join(outputDir, "assets", "asset1.jar")
        assertThat(assetJar1).exists()
        val entryMap1 = ZipArchive.listEntries(assetJar1)
        assertThat(entryMap1.keys).containsExactly("assets/asset1")
        assertThat(entryMap1["assets/asset1"]?.isCompressed).isTrue()

        // check asset2
        val assetJar2 = FileUtils.join(outputDir, "assets", "dir", "asset2.jar")
        assertThat(assetJar2).exists()
        val entryMap2 = ZipArchive.listEntries(assetJar2)
        assertThat(entryMap2.keys).containsExactly("assets/dir/asset2")
        assertThat(entryMap2["assets/dir/asset2"]?.isCompressed).isTrue()

        // check noCompressAsset
        val noCompressAssetJar = FileUtils.join(outputDir, "assets", "noCompressAsset.jar")
        assertThat(noCompressAssetJar).exists()
        val noCompressEntryMap = ZipArchive.listEntries(noCompressAssetJar)
        assertThat(noCompressEntryMap.keys).containsExactly("assets/noCompressAsset")
        assertThat(noCompressEntryMap["assets/noCompressAsset"]?.isCompressed).isFalse()

        // then test incremental run
        asset1.writeText("changed")
        FileUtils.deleteRecursivelyIfExists(asset2.parentFile)
        val incrementalChanges =
            listOf(
                FakeFileChange(asset1, ChangeType.MODIFIED, FileType.FILE, "asset1"),
                FakeFileChange(asset2, ChangeType.REMOVED, FileType.FILE, "dir/asset2"),
                FakeFileChange(asset2.parentFile, ChangeType.REMOVED, FileType.DIRECTORY, "dir")
            )
        CompressAssetsDelegate(
            workQueue,
            inputDir,
            outputDir,
            noCompressPredicate,
            compressionLevel,
            incrementalChanges
        ).run()

        // check asset1
        assertThat(assetJar1).exists()
        val incrementalEntryMap1 = ZipArchive.listEntries(assetJar1)
        assertThat(incrementalEntryMap1.keys).containsExactly("assets/asset1")
        assertThat(incrementalEntryMap1["assets/asset1"]?.isCompressed).isTrue()

        // check asset2
        assertThat(assetJar2).doesNotExist()
        // assetJar2's parent directory still exists. This is intentional because deleting empty
        // directories would require calling workers.await() in the task, which is undesirable, and
        // empty directories are ignored by the downstream packaging task.
        assertThat(assetJar2.parentFile).exists()

        // check noCompressAsset
        assertThat(noCompressAssetJar).exists()
        val incrementalNoCompressEntryMap = ZipArchive.listEntries(noCompressAssetJar)
        assertThat(incrementalNoCompressEntryMap.keys).containsExactly("assets/noCompressAsset")
        assertThat(incrementalNoCompressEntryMap["assets/noCompressAsset"]?.isCompressed).isFalse()
    }
}
