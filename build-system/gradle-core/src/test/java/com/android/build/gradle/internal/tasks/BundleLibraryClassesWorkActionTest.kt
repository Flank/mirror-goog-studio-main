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

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_DIR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.builder.files.SerializableChange
import com.android.builder.files.SerializableFileChanges
import com.android.ide.common.resources.FileStatus
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import org.gradle.api.provider.Property
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
class BundleLibraryClassesWorkActionTest(private val outputType: AndroidArtifacts.ArtifactType) {

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

    private lateinit var outputFile: File

    @Before
    fun setUp() {
        outputFile = if (outputType == CLASSES_JAR) {
            tmp.newFile("output.jar")
        } else {
            tmp.newFolder("outputDir")
        }
    }

    private fun assertContains(output: File, relativePath: String) {
        if (output.extension == "jar") {
            ZipFileSubject.assertThat(output) {
                it.contains(relativePath)
            }
        } else {
            assertThat(output.resolve(relativePath)).exists()
        }
    }

    private fun assertDoesNotContain(output: File, relativePath: String) {
        if (output.extension == "jar") {
            ZipFileSubject.assertThat(output) {
                it.doesNotContain(relativePath)
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
        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("")
                    override val toIgnore =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(input)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(false)
                    override val inputChanges =
                        FakeGradleProperty(SerializableFileChanges(emptyList()))
                    override val packageRClass = FakeGradleProperty(false)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertContains(outputFile, "A.class")
        assertContains(outputFile, "B.class")
        assertContains(outputFile, "sub/C.class")
        assertContains(outputFile, "META-INF/a.modules")
        assertDoesNotContain(outputFile, "res.txt")
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
        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("com.example")
                    override val toIgnore =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(input)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(false)
                    override val inputChanges =
                        FakeGradleProperty(SerializableFileChanges(emptyList()))
                    override val packageRClass = FakeGradleProperty(false)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertContains(outputFile, "A.class")
        assertDoesNotContain(outputFile, "com/example/R.class")
        assertDoesNotContain(outputFile, "com/example/R\$string.class")
        assertDoesNotContain(outputFile, "com/example/Manifest.class")
        assertDoesNotContain(outputFile, "com/example/Manifest\$nested.class")
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
        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("com.example")
                    override val toIgnore =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(input)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(false)
                    override val inputChanges =
                        FakeGradleProperty(SerializableFileChanges(emptyList()))
                    override val packageRClass = FakeGradleProperty(true)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        assertContains(outputFile, "A.class")
        assertContains(outputFile, "com/example/R.class")
        assertContains(outputFile, "com/example/R\$string.class")
        assertDoesNotContain(outputFile, "com/example/Manifest.class")
        assertDoesNotContain(outputFile, "com/example/Manifest\$nested.class")
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

        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("")
                    override val toIgnore =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(inputJar)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(false)
                    override val inputChanges =
                        FakeGradleProperty(SerializableFileChanges(emptyList()))
                    override val packageRClass = FakeGradleProperty(false)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        val outputJar = if (outputType == CLASSES_JAR) {
            outputFile
        } else {
            outputFile.resolve("classes.jar")
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

        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("")
                    override val toIgnore =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                            .value(listOf(".*A\\.class$"))
                    override val input = FakeConfigurableFileCollection(inputJar)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(false)
                    override val inputChanges =
                        FakeGradleProperty(SerializableFileChanges(emptyList()))
                    override val packageRClass = FakeGradleProperty(false)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        val outputJar = if (outputType == CLASSES_JAR) {
            outputFile
        } else {
            outputFile.resolve("classes.jar")
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

        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val namespace = FakeGradleProperty("")
                    override val toIgnore = FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(inputDir)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(false)
                    override val inputChanges = FakeGradleProperty(SerializableFileChanges(emptyList()))
                    override val packageRClass = FakeGradleProperty(false)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        assertContains(outputFile, "dir1/A.class")
        assertContains(outputFile, "dir1/B.class")
        assertContains(outputFile, "dir2/C.class")

        val changedFileTimestampBefore = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(outputFile.resolve("dir1/B.class").toPath())
        } else {
            Files.getLastModifiedTime(outputFile.toPath())
        }
        val unchangedFileTimestampBefore = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(outputFile.resolve("dir2/C.class").toPath())
        } else {
            Files.getLastModifiedTime(outputFile.toPath())
        }

        TestUtils.waitForFileSystemTick()

        FileUtils.delete(inputDir.resolve("dir1/A.class"))
        inputDir.resolve("dir1/B.class").writeText("Changed")
        inputDir.resolve("dir2/D.class").writeText("Added")

        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("")
                    override val toIgnore =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(inputDir)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(true)
                    override val inputChanges = FakeGradleProperty(
                        SerializableFileChanges(
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
                        )
                    )
                    override val packageRClass = FakeGradleProperty(false)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        assertDoesNotContain(outputFile, "dir1/A.class")
        assertContains(outputFile, "dir1/B.class")
        assertContains(outputFile, "dir2/C.class")
        assertContains(outputFile, "dir2/D.class")

        val changedFileTimestampAfter = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(outputFile.resolve("dir1/B.class").toPath())
        } else {
            Files.getLastModifiedTime(outputFile.toPath())
        }
        val unchangedFileTimestampAfter = if (outputType == CLASSES_DIR) {
            Files.getLastModifiedTime(outputFile.resolve("dir2/C.class").toPath())
        } else {
            Files.getLastModifiedTime(outputFile.toPath())
        }
        assertNotEquals(changedFileTimestampBefore, changedFileTimestampAfter)
        if (outputType == CLASSES_DIR) {
            assertEquals(unchangedFileTimestampBefore, unchangedFileTimestampAfter)
        } else {
            // When outputting to a jar, the task is not incremental.
            assertNotEquals(unchangedFileTimestampBefore, unchangedFileTimestampAfter)
        }
    }

    /** Regression test for b/198667126. */
    @Test
    fun testMultipleMetaInfDirsNonIncremental() {
        val input = setOf(
                tmp.newFolder().also { dir ->
                    dir.resolve("META-INF/emptyDir/").mkdirs()
                    dir.resolve("META-INF/dir/").mkdirs()
                    dir.resolve("META-INF/dir/1.txt").createNewFile()
                },
                tmp.newFolder().also { dir ->
                    dir.resolve("META-INF/dir/").mkdirs()
                    dir.resolve("META-INF/dir/2.txt").createNewFile()
                }
        )
        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("com.example")
                    override val toIgnore =
                            FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(input)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(false)
                    override val inputChanges =
                            FakeGradleProperty(SerializableFileChanges(emptyList()))
                    override val packageRClass = FakeGradleProperty(true)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        assertContains(outputFile, "META-INF/dir/1.txt")
        assertContains(outputFile, "META-INF/dir/2.txt")
        assertDoesNotContain(outputFile, "META-INF/emptyDir/")
    }

    /** Regression test for b/198667126. */
    @Test
    fun testMultipleMetaInfDirsIncremental() {
        val changes = mutableListOf<SerializableChange>()
        val input = setOf(
                tmp.newFolder().also { dir ->
                    dir.resolve("META-INF/emptyDir/").mkdirs()
                    dir.resolve("META-INF/dir/").mkdirs()
                    dir.resolve("META-INF/dir/1.txt").also { f ->
                        f.createNewFile()
                        changes.add(SerializableChange(f, FileStatus.NEW, "META-INF/dir/1.txt"))
                        changes.add(SerializableChange(f.parentFile, FileStatus.NEW, "META-INF/dir"))
                        changes.add(SerializableChange(f.parentFile, FileStatus.NEW, "META-INF/emptyDir"))
                    }
                }
        )
        object : BundleLibraryClassesWorkAction() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val namespace = FakeGradleProperty("com.example")
                    override val toIgnore =
                            FakeObjectFactory.factory.listProperty(String::class.java)
                    override val input = FakeConfigurableFileCollection(input)
                    override val output = FakeGradleProperty(outputFile)
                    override val incremental = FakeGradleProperty(true)
                    override val inputChanges =
                            FakeGradleProperty(SerializableFileChanges(changes))
                    override val packageRClass = FakeGradleProperty(true)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        assertContains(outputFile, "META-INF/dir/1.txt")
        assertDoesNotContain(outputFile, "META-INF/emptyDir/")
    }
}
