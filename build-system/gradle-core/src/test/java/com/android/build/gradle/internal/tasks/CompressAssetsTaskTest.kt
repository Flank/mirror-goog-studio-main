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
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.FileType
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.ChangeType
import org.gradle.workers.WorkQueue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockitoAnnotations
import java.nio.file.Path
import java.util.function.Predicate
import java.util.zip.Deflater

/**
 * Unit tests for [CompressAssetsTask].
 */
class CompressAssetsTaskTest {
    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var workQueue: WorkQueue
    private lateinit var inputDir: Path
    private lateinit var asset1: Path
    private lateinit var asset2: Path
    private lateinit var noCompressAsset: Path
    private lateinit var outputDir: Path

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        workQueue = FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder()).noIsolation()

        // create input dir with various assets, one of which won't be compressed
        inputDir = temporaryFolder.newFolder("inputDir").toPath()
        asset1 = inputDir.resolve("asset1")
        FileUtils.createFile(asset1.toFile(), "asset1")
        assertThat(asset1).exists()
        asset2 = inputDir.resolve("dir/asset2")
        FileUtils.createFile(asset2.toFile(), "asset2")
        assertThat(asset2).exists()
        noCompressAsset = inputDir.resolve("noCompressAsset")
        FileUtils.createFile(noCompressAsset.toFile(), "noCompressAsset")
        assertThat(noCompressAsset).exists()

        outputDir = temporaryFolder.newFolder("outputDir").toPath()
    }

    @Test
    fun testBasic() {
        // first test non-incremental run
        val noCompressPredicate = Predicate<String> { it.endsWith("noCompressAsset") }
        val compressionLevel = Deflater.BEST_SPEED
        val nonIncrementalChanges =
            listOf(
                FakeFileChange(asset1.toFile(), ChangeType.ADDED, FileType.FILE, "asset1"),
                FakeFileChange(asset2.toFile(), ChangeType.ADDED, FileType.FILE, "dir/asset2"),
                FakeFileChange(noCompressAsset.toFile(), ChangeType.ADDED, FileType.FILE, "noCompressAsset"),
                FakeFileChange(asset2.parent.toFile(), ChangeType.ADDED, FileType.DIRECTORY, "dir")
            )
        CompressAssetsDelegate(
            workQueue,
            outputDir.toFile(),
            noCompressPredicate,
            compressionLevel,
            nonIncrementalChanges
        ).run()

        // check asset1
        val assetJar1 = outputDir.resolve("assets/asset1.jar")
        assertThat(assetJar1).exists()
        val entryMap1 = ZipArchive.listEntries(assetJar1)
        assertThat(entryMap1.keys).containsExactly("assets/asset1")
        assertThat(entryMap1["assets/asset1"]?.isCompressed).isTrue()

        // check asset2
        val assetJar2 = outputDir.resolve("assets/dir/asset2.jar")
        assertThat(assetJar2).exists()
        val entryMap2 = ZipArchive.listEntries(assetJar2)
        assertThat(entryMap2.keys).containsExactly("assets/dir/asset2")
        assertThat(entryMap2["assets/dir/asset2"]?.isCompressed).isTrue()

        // check noCompressAsset
        val noCompressAssetJar = outputDir.resolve("assets/noCompressAsset.jar")
        assertThat(noCompressAssetJar).exists()
        val noCompressEntryMap = ZipArchive.listEntries(noCompressAssetJar)
        assertThat(noCompressEntryMap.keys).containsExactly("assets/noCompressAsset")
        assertThat(noCompressEntryMap["assets/noCompressAsset"]?.isCompressed).isFalse()

        // then test incremental run
        asset1.toFile().writeText("changed")
        FileUtils.deleteRecursivelyIfExists(asset2.parent.toFile())
        val incrementalChanges =
            listOf(
                FakeFileChange(asset1.toFile(), ChangeType.MODIFIED, FileType.FILE, "asset1"),
                FakeFileChange(asset2.toFile(), ChangeType.REMOVED, FileType.FILE, "dir/asset2"),
                FakeFileChange(asset2.parent.toFile(), ChangeType.REMOVED, FileType.DIRECTORY, "dir")
            )
        CompressAssetsDelegate(
            workQueue,
            outputDir.toFile(),
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
        assertThat(assetJar2.parent).exists()

        // check noCompressAsset
        assertThat(noCompressAssetJar).exists()
        val incrementalNoCompressEntryMap = ZipArchive.listEntries(noCompressAssetJar)
        assertThat(incrementalNoCompressEntryMap.keys).containsExactly("assets/noCompressAsset")
        assertThat(incrementalNoCompressEntryMap["assets/noCompressAsset"]?.isCompressed).isFalse()
    }
}
