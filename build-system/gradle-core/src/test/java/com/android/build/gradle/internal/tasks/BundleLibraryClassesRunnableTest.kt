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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_DIR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.build.gradle.tasks.toSerializable
import com.android.builder.files.SerializableChange
import com.android.builder.files.SerializableFileChanges
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.testutils.TestUtils
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.work.FileChange
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(Parameterized::class)
class BundleLibraryClassesRunnableTest(private val outputType: AndroidArtifacts.ArtifactType) {

    companion object {

        @Parameterized.Parameters(name = "outputType_{0}")
        @JvmStatic
        fun parameters() = listOf(
            CLASSES_JAR,
            CLASSES_DIR
        )
    }

    @JvmField
    @Rule
    val tmp = TemporaryFolder()
    lateinit var workers: WorkerExecutorFacade

    private lateinit var output: File

    @Before
    fun setUp() {
        workers = ExecutorServiceAdapter("test", ":test", MoreExecutors.newDirectExecutorService())
        output = if (outputType == CLASSES_JAR) {
            tmp.newFile("output.jar")
        } else {
            tmp.newFolder("outputDir")
        }
    }

    private fun assertContains(output: File, relativePath: String) {
        if (output.extension == "jar") {
            Zip(output).use {
                ZipFileSubject.assertThat(it).contains(relativePath)
            }
        } else {
            assertThat(output.resolve(relativePath)).exists()
        }
    }

    private fun assertDoesNotContain(output: File, relativePath: String) {
        if (output.extension == "jar") {
            Zip(output).use {
                ZipFileSubject.assertThat(it).doesNotContain(relativePath)
            }
        } else {
            assertThat(output.resolve(relativePath)).doesNotExist()
        }
    }

    @Test
    fun testClassesCopied() {
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("A.class").createNewFile()
                dir.resolve("B.class").createNewFile()
                dir.resolve("res.txt").createNewFile()
                dir.resolve("META-INF").also {
                    it.mkdir()
                    it.resolve("a.modules").createNewFile()
                }
                dir.resolve("sub").also {
                    it.mkdir()
                    it.resolve("C.class").createNewFile()
                }
            }
        )
        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(),
                outputType = outputType,
                output = output,
                input = input,
                incremental = false,
                inputChanges = emptyList<FileChange>().toSerializable(),
                packageRClass = false,
                jarCreatorType = JarCreatorType.JAR_FLINGER
            )
        ).run()
        assertContains(output, "A.class")
        assertContains(output, "B.class")
        assertContains(output, "sub/C.class")
        assertContains(output, "META-INF/a.modules")
        assertDoesNotContain(output, "res.txt")
    }

    @Test
    fun testGeneratedSkipped() {
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("A.class").createNewFile()
                dir.resolve("com/example").mkdirs()
                dir.resolve("com/example/R.class").createNewFile()
                dir.resolve("com/example/R\$string.class").createNewFile()
                dir.resolve("com/example/Manifest.class").createNewFile()
                dir.resolve("com/example/Manifest\$nested.class").createNewFile()
            }
        )
        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "com.example",
                toIgnore = listOf(),
                outputType = outputType,
                output = output,
                input = input,
                incremental = false,
                inputChanges = emptyList<FileChange>().toSerializable(),
                packageRClass = false,
                jarCreatorType = JarCreatorType.JAR_FLINGER
            )
        ).run()
        assertContains(output, "A.class")
        assertDoesNotContain(output, "com/example/R.class")
        assertDoesNotContain(output, "com/example/R\$string.class")
        assertDoesNotContain(output, "com/example/Manifest.class")
        assertDoesNotContain(output, "com/example/Manifest\$nested.class")
    }

    @Test
    fun testBundleRClass() {
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("A.class").createNewFile()
                dir.resolve("com/example").mkdirs()
                dir.resolve("com/example/R.class").createNewFile()
                dir.resolve("com/example/R\$string.class").createNewFile()
                dir.resolve("com/example/Manifest.class").createNewFile()
                dir.resolve("com/example/Manifest\$nested.class").createNewFile()
            }
        )
        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "com.example",
                toIgnore = listOf(),
                outputType = outputType,
                output = output,
                input = input,
                incremental = false,
                inputChanges = emptyList<FileChange>().toSerializable(),
                packageRClass = true,
                jarCreatorType = JarCreatorType.JAR_FLINGER
            )
        ).run()
        assertContains(output, "A.class")
        assertContains(output, "com/example/R.class")
        assertContains(output, "com/example/R\$string.class")
        assertDoesNotContain(output, "com/example/Manifest.class")
        assertDoesNotContain(output, "com/example/Manifest\$nested.class")
    }

    @Test
    fun testReadingFromJars() {
        val inputJar = tmp.root.resolve("input.jar")
        ZipOutputStream(inputJar.outputStream()).use {
            it.putNextEntry(ZipEntry("A.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/B.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("a.txt"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/a.txt"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("META-INF/a.modules"))
            it.closeEntry()
        }

        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(),
                outputType = outputType,
                output = output,
                input = setOf(inputJar),
                incremental = false,
                inputChanges = emptyList<FileChange>().toSerializable(),
                packageRClass = false,
                jarCreatorType = JarCreatorType.JAR_FLINGER
            )
        ).run()
        val outputJar = if (outputType == CLASSES_JAR) {
            output
        } else {
            output.resolve("classes.jar")
        }
        assertContains(outputJar, "A.class")
        assertContains(outputJar, "sub/B.class")
        assertContains(outputJar, "META-INF/a.modules")
        assertDoesNotContain(outputJar, "a.txt")
        assertDoesNotContain(outputJar, "sub/a.txt")
    }

    @Test
    fun testIgnoredExplicitly() {
        val inputJar = tmp.root.resolve("input.jar")
        ZipOutputStream(inputJar.outputStream()).use {
            it.putNextEntry(ZipEntry("A.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/B.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("a.txt"))
            it.closeEntry()
        }

        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(".*A\\.class$"),
                outputType = outputType,
                output = output,
                input = setOf(inputJar),
                incremental = false,
                inputChanges = emptyList<FileChange>().toSerializable(),
                packageRClass = false,
                jarCreatorType = JarCreatorType.JAR_FLINGER
            )
        ).run()
        val outputJar = if (outputType == CLASSES_JAR) {
            output
        } else {
            output.resolve("classes.jar")
        }
        assertDoesNotContain(outputJar, "A.class")
        assertContains(outputJar, "sub/B.class")
    }

    @Test
    fun testIncrementalCopy() {
        val inputDir = tmp.newFolder()
        inputDir.resolve("dir1").also {
            it.mkdir()
            it.resolve("A.class").createNewFile()
            it.resolve("B.class").createNewFile()
        }
        inputDir.resolve("dir2").also {
            it.mkdir()
            it.resolve("C.class").createNewFile()
        }

        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(),
                outputType = outputType,
                output = output,
                input = setOf(inputDir),
                incremental = false,
                inputChanges = emptyList<FileChange>().toSerializable(),
                packageRClass = false,
                jarCreatorType = JarCreatorType.JAR_FLINGER
            )
        ).run()
        assertContains(output, "dir1/A.class")
        assertContains(output, "dir1/B.class")
        assertContains(output, "dir2/C.class")

        val changedFileTimestampBefore = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(output.resolve("dir1/B.class").toPath())
        } else {
            Files.getLastModifiedTime(output.toPath())
        }
        val unchangedFileTimestampBefore = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(output.resolve("dir2/C.class").toPath())
        } else {
            Files.getLastModifiedTime(output.toPath())
        }

        TestUtils.waitForFileSystemTick()

        FileUtils.delete(inputDir.resolve("dir1/A.class"))
        inputDir.resolve("dir1/B.class").writeText("Changed")
        inputDir.resolve("dir2/D.class").writeText("Added")
        BundleLibraryClassesRunnable(
            BundleLibraryClassesRunnable.Params(
                packageName = "",
                toIgnore = listOf(),
                outputType = outputType,
                output = output,
                input = setOf(inputDir),
                incremental = true,
                inputChanges = SerializableFileChanges(
                    listOf(
                        SerializableChange(
                            inputDir.resolve("dir1/A.class"),
                            FileStatus.REMOVED,
                            "dir1/A.class"
                        ),
                        SerializableChange(
                            inputDir.resolve("dir1/B.class"),
                            FileStatus.CHANGED,
                            "dir1/B.class"
                        ),
                        SerializableChange(
                            inputDir.resolve("dir2/D.class"),
                            FileStatus.NEW,
                            "dir2/D.class"
                        )
                    )
                ),
                packageRClass = false,
                jarCreatorType = JarCreatorType.JAR_FLINGER
            )
        ).run()
        assertDoesNotContain(output, "dir1/A.class")
        assertContains(output, "dir1/B.class")
        assertContains(output, "dir2/C.class")
        assertContains(output, "dir2/D.class")

        val changedFileTimestampAfter = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(output.resolve("dir1/B.class").toPath())
        } else {
            Files.getLastModifiedTime(output.toPath())
        }
        val unchangedFileTimestampAfter = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(output.resolve("dir2/C.class").toPath())
        } else {
            Files.getLastModifiedTime(output.toPath())
        }
        assertNotEquals(changedFileTimestampBefore, changedFileTimestampAfter)
        if (outputType == CLASSES_DIR) {
            assertEquals(unchangedFileTimestampBefore, unchangedFileTimestampAfter)
        } else {
            // When outputting to a jar, the task is not incremental.
            assertNotEquals(unchangedFileTimestampBefore, unchangedFileTimestampAfter)
        }
    }
}