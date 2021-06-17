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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.bouncycastle.util.io.Streams
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

class PerModuleBundleTaskTest {

    @get:Rule
    val testFolder = TemporaryFolder()

    lateinit var task: PerModuleBundleTask

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(testFolder.newFolder()).build()

        task = project.tasks.create("test", PerModuleBundleTask::class.java) {
            task -> task.assetsFilesDirectories.add(
                project.layout.buildDirectory.dir(
                    testFolder.newFolder("assets").absolutePath))
        }

        val resFile = testFolder.newFile("res").also {
            createRes(it)
        }
        task.resFiles.set(resFile)
        task.fileName.set("bar.zip")
        task.jarCreatorType.set(JarCreatorType.JAR_FLINGER)
        task.outputDir.set(testFolder.newFolder("out"))
    }

    @Test
    fun testSingleDexFiles() {
        val dexFolder = testFolder.newFolder("dex_files")
        task.dexFiles.from(createDex(dexFolder, "classes.dex"))
        task.doTaskAction()
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 1)
    }

    @Test
    fun testNoDuplicateDexFiles() {
        val dexFolder = testFolder.newFolder("dex_files")
        task.dexFiles.from(
            setOf(
                createDex(dexFolder, "classes.dex"),
                createDex(dexFolder, "classes2.dex"),
                createDex(dexFolder, "classes3.dex")
            )
        )
        task.doTaskAction()
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 3)
    }

    @Test
    fun testDuplicateDexFiles() {
        val dexFolder0 = testFolder.newFolder("0")
        val dexFolder1 = testFolder.newFolder("1")
        task.dexFiles.from(
            setOf(
                createDex(dexFolder0, "classes.dex"),
                createDex(dexFolder0, "classes2.dex"),
                createDex(dexFolder0, "classes3.dex"),
                createDex(dexFolder1, "classes.dex"),
                createDex(dexFolder1, "classes2.dex")
            )
        )
        task.doTaskAction()

        // verify naming and shuffling of names.
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 5)
    }

    @Test
    fun testMainDexNotRenamedFiles() {
        val dexFolder0 = testFolder.newFolder("0")
        task.dexFiles.from(
            setOf(
                createDex(dexFolder0, "classes2.dex"),
                createDex(dexFolder0, "classes.dex"),
                createDex(dexFolder0, "classes3.dex")
            )
        )
        task.doTaskAction()

        // verify classes.dex has not been renamed.
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 3)
    }

    @Test
    fun testExcludeJarManifest() {
        val metadata = "META-INF/MANIFEST.MF"
        val dexFolder = testFolder.newFolder("0")
        task.dexFiles.from(
            setOf(
                createDex(dexFolder, "classes.dex"),
                createDex(dexFolder,metadata)
            )
        )
        val resFile = testFolder.newFile("res2").also { file ->
            JarOutputStream(BufferedOutputStream(FileOutputStream(file))).use {
                it.putNextEntry(JarEntry(metadata))
                it.closeEntry()

                it.putNextEntry(JarEntry("bar"))
                it.writer(Charsets.UTF_8).append("bar")
                it.closeEntry()

            }
        }
        task.resFiles.set(resFile)
        task.doTaskAction()
        val zipFile = task.outputDir.get().asFileTree.singleFile
        assertThat(zipFile) {
            it.contains("dex/classes.dex")
            it.contains("bar")
            it.entries("MANIFEST.MF$").hasSize(0)
        }

    }

    private fun verifyOutputZip(zipFile: File, expectedNumberOfDexFiles: Int) {
        assertThat(expectedNumberOfDexFiles).isGreaterThan(0)
        assertThat(zipFile.exists())
        assertThat(zipFile) {
            it.contains("dex/classes.dex")
            for (index in 2..expectedNumberOfDexFiles) {
                it.contains("dex/classes$index.dex")
            }
            it.doesNotContain("dex/classes" + (expectedNumberOfDexFiles + 1) + ".dex")
        }
        verifyClassesDexNotRenamed(zipFile)
    }

    private fun verifyClassesDexNotRenamed(zipFile: File) {
        val outputZip = ZipFile(zipFile)
        outputZip.getInputStream(outputZip.getEntry("dex/classes.dex")).use {
            val bytes = ByteArray(128)
            Streams.readFully(it, bytes)
            assertThat(bytes.toString(Charset.defaultCharset())).startsWith("Dex classes.dex")
        }
    }

    private fun createDex(folder: File, id: String): File {
        val dexFile = File(folder, id)
        FileUtils.createFile(dexFile, "Dex $id")
        return dexFile
    }

    private fun createRes(resFile: File) {
        JarOutputStream(BufferedOutputStream(FileOutputStream(resFile))).use {
            it.putNextEntry(JarEntry("foo"))
            it.writer(Charsets.UTF_8).append("foo")
            it.closeEntry()
        }
    }
}
