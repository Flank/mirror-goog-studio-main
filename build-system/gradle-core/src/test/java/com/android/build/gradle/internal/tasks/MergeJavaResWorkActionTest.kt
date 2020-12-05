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

import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.packaging.defaultMerges
import com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.builder.merge.DuplicateRelativeFileException
import com.android.builder.packaging.JarFlinger
import com.android.ide.common.resources.FileStatus
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.android.zipflinger.BytesSource
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Paths
import java.util.zip.Deflater
import java.util.zip.ZipFile
import kotlin.test.assertFailsWith

/** Test cases for [MergeJavaResWorkAction].  */
class MergeJavaResWorkActionTest {
    @get:Rule
    var tmpDir = TemporaryFolder()

    @Test
    fun testMergeResources() {
        // Create first jar file containing resources to be merged
        val jarFile1 = File(tmpDir.root, "jarFile1.jar")
        ZFile(jarFile1).use {
            it.add("fileEndingWithDot.", ByteArrayInputStream(ByteArray(0)))
            it.add("fileNotEndingWithDot", ByteArrayInputStream(ByteArray(0)))
        }
        // Create second jar file containing some resources to merge, and some to exclude
        val jarFile2 = File(tmpDir.root, "jarFile2.jar")
        ZFile(jarFile2).use {
            it.add("javaResFromJarFile2", ByteArrayInputStream(ByteArray(0)))
            it.add("LICENSE", ByteArrayInputStream(ByteArray(0)))
        }

        val outputFile = File(tmpDir.root, "out.jar")
        val incrementalStateFile = File(tmpDir.root, "merge-state")
        val cacheDir = File(tmpDir.root, "cacheDir")
        object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(jarFile1)
                    override val subProjectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(jarFile2)
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val outputFile =
                        FakeObjectFactory.factory.fileProperty().also { it.set(outputFile) }
                    override val outputDirectory = FakeObjectFactory.factory.directoryProperty()
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultExcludes)
                        }
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultMerges)
                        }
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(false)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        )
                    override val contentType = FakeGradleProperty(RESOURCES as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()

        // Make sure the output is a jar file with expected contents
        assertThat(outputFile).isFile()
        assertThat(outputFile) {
            it.contains("fileEndingWithDot.")
            it.contains("fileNotEndingWithDot")
            it.contains("javaResFromJarFile2")
            it.doesNotContain("LICENSE")
        }

        // Check that the zip entries are not extracted (regression test for bug 65337573)
        // TODO(b/174890604): Fix the invalid assertions.
        //assertThat(File(outputFile, "fileEndingWithDot.")).doesNotExist()
        //assertThat(File(outputFile, "fileNotEndingWithDot")).doesNotExist()

        // Check that the zip entries' timestamps are erased (regression test for bug 142890134)
        ZipFile(outputFile).use {
            val entry1Timestamp =
                it.getEntry("fileEndingWithDot.").lastModifiedTime.toInstant().toString()
            val entry2Timestamp =
                it.getEntry("fileNotEndingWithDot").lastModifiedTime.toInstant().toString()

            // Different OSes/timezones may interpret the zero timestamp differently (see bug
            // 150817339), but looks like they agree on the same date.
            assertThat(entry1Timestamp).isEqualTo(entry2Timestamp)
            assertThat(entry2Timestamp).startsWith("1979-11-30")
        }
    }

    @Test
    fun testMergeResourcesWithNoCompress() {
        // Create jar file containing java resources to be merged
        val jarFile = File(tmpDir.root, "jarFile.jar")
        JarFlinger(jarFile.toPath()).use {
            // compress all entries in input jar file
            it.setCompressionLevel(Deflater.BEST_SPEED)
            it.addEntry("from_jar.compress", ByteArrayInputStream(ByteArray(100)))
            it.addEntry("from_jar.no_compress", ByteArrayInputStream(ByteArray(100)))
        }

        // Create directory containing java resources to be merged
        val dir = File(tmpDir.root, "dir")
        FileUtils.createFile(File(dir, "from_dir.compress"), "foo".repeat(100))
        FileUtils.createFile(File(dir, "from_dir.no_compress"), "foo".repeat(100))

        val outputFile = File(tmpDir.root, "out.jar")
        val incrementalStateFile = File(tmpDir.root, "merge-state")
        val cacheDir = File(tmpDir.root, "cacheDir")
        object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(dir)
                    override val subProjectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(jarFile)
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val outputFile =
                        FakeObjectFactory.factory.fileProperty().also { it.set(outputFile) }
                    override val outputDirectory = FakeObjectFactory.factory.directoryProperty()
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultExcludes)
                        }
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultMerges)
                        }
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(false)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        )
                    override val contentType = FakeGradleProperty(RESOURCES as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java).also {
                            it.add(".no_compress")
                        }
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        // Make sure the output is a jar with entries having the expected compression
        assertThat(outputFile).isFile()
        val entries = ZipArchive.listEntries(outputFile.toPath())
        assertThat(entries["from_jar.compress"]?.isCompressed).isTrue()
        assertThat(entries["from_dir.compress"]?.isCompressed).isTrue()
        assertThat(entries["from_jar.no_compress"]?.isCompressed).isFalse()
        assertThat(entries["from_dir.no_compress"]?.isCompressed).isFalse()
    }


    @Test
    fun testMergeNativeLibs() {
        // Create first dir containing native libs to be merged
        val dir1 = File(tmpDir.root, "dir1")
        FileUtils.createFile(File(dir1, "x86/foo.so"), "foo")
        FileUtils.createFile(File(dir1, "x86/bar.so"), "bar")
        FileUtils.createFile(File(dir1, "x86/notAnSoFile"), "ignore me")

        // Create second dir containing native libs to be merged
        val dir2 = File(tmpDir.root, "dir2")
        FileUtils.createFile(File(dir2, "x86/baz.so"), "baz")
        FileUtils.createFile(File(dir2, "x86/exclude.so"), "exclude me")

        val outputDir = File(tmpDir.root, "out")
        // exclude "**/exclude.so"
        val excludes: Set<String> = setOf("**/exclude.so")
        val incrementalStateFile = File(tmpDir.root, "merge-state")
        val cacheDir = File(tmpDir.root, "cacheDir")
        object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(dir1)
                    override val subProjectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(dir2)
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val outputFile = FakeObjectFactory.factory.fileProperty()
                    override val outputDirectory =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(outputDir) }
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(excludes)
                        }
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges = FakeObjectFactory.factory.setProperty(String::class.java)
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(false)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        )
                    override val contentType = FakeGradleProperty(NATIVE_LIBS as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        // Make sure the output is a dir with expected contents
        assertThat(outputDir).isDirectory()
        assertThat(File(outputDir, "lib/x86/foo.so")).isFile()
        assertThat(File(outputDir, "lib/x86/bar.so")).isFile()
        assertThat(File(outputDir, "lib/x86/baz.so")).isFile()
        assertThat(File(outputDir, "lib/x86/notAnSoFile")).doesNotExist()
        assertThat(File(outputDir, "lib/x86/exclude.so")).doesNotExist()
    }

    @Test
    fun testIncrementalMergeResources() {
        // Create jar file containing a resource to be merged
        val jarFile = File(tmpDir.root, "jarFile.jar")
        ZFile(jarFile).use {
            it.add("javaRes1", ByteArrayInputStream(ByteArray(0)))
        }

        val outputFile = File(tmpDir.root, "out.jar")
        val incrementalStateFile = File(tmpDir.root, "merge-state")
        val cacheDir = File(tmpDir.root, "cacheDir")

        // check that no incremental info saved before first merge
        assertThat(incrementalStateFile).doesNotExist()

        // The first time we execute, incremental is false and changedInputs is empty.
        object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(jarFile)
                    override val subProjectJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val outputFile =
                        FakeObjectFactory.factory.fileProperty().also { it.set(outputFile) }
                    override val outputDirectory = FakeObjectFactory.factory.directoryProperty()
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultExcludes)
                        }
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultMerges)
                        }
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(false)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        )
                    override val contentType = FakeGradleProperty(RESOURCES as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        // check that incremental info saved
        assertThat(incrementalStateFile).isFile()
        // Make sure the output is a jar file with expected contents
        assertThat(outputFile).isFile()
        Zip(outputFile).use {
            assertThat(it).contains("javaRes1")
            assertThat(it).doesNotContain("javaRes2")
        }

        // Now add a resource to the jar file and merge incrementally
        ZipArchive(jarFile.toPath()).use {
            it.add(BytesSource(ByteArray(0), "javaRes2", 0))
        }

        // The second time we execute, incremental is true and changedInputs is not empty.
        object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(jarFile)
                    override val subProjectJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val outputFile =
                        FakeObjectFactory.factory.fileProperty().also { it.set(outputFile) }
                    override val outputDirectory = FakeObjectFactory.factory.directoryProperty()
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultExcludes)
                        }
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultMerges)
                        }
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(true)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        ).also { it.put(jarFile, FileStatus.CHANGED) }
                    override val contentType = FakeGradleProperty(RESOURCES as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        // Make sure the output is a jar file with expected contents
        assertThat(outputFile).isFile()
        Zip(outputFile).use {
            assertThat(it).contains("javaRes1")
            assertThat(it).contains("javaRes2")
        }
    }

    @Test
    fun testIncrementalMergeNativeLibs() {
        // Create first dir containing native libs to be merged
        val dir = File(tmpDir.root, "dir")
        FileUtils.createFile(File(dir, "x86/foo.so"), "foo")

        val outputDir = File(tmpDir.root, "out")
        val incrementalStateFile = File(tmpDir.root, "merge-state")
        val cacheDir = File(tmpDir.root, "cacheDir")

        // check that no incremental info saved before first merge
        assertThat(incrementalStateFile).doesNotExist()

        // The first time we execute, incremental is false and changedInputs is empty.
        object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(dir)
                    override val subProjectJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val outputFile = FakeObjectFactory.factory.fileProperty()
                    override val outputDirectory =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(outputDir) }
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges = FakeObjectFactory.factory.setProperty(String::class.java)
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(false)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        )
                    override val contentType = FakeGradleProperty(NATIVE_LIBS as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        // check that incremental info saved
        assertThat(incrementalStateFile).isFile()
        // Make sure the output is a dir with expected contents
        assertThat(outputDir).isDirectory()
        assertThat(File(outputDir, "lib/x86/foo.so")).isFile()
        assertThat(File(outputDir, "lib/x86/bar.so")).doesNotExist()

        // Now add a .so file to the dir and merge incrementally
        val addedFile = File(dir, "x86/bar.so")
        FileUtils.createFile(addedFile, "bar")


        // The second time we execute, incremental is true and changedInputs is not empty.
        object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(dir)
                    override val subProjectJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val outputFile = FakeObjectFactory.factory.fileProperty()
                    override val outputDirectory =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(outputDir) }
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges = FakeObjectFactory.factory.setProperty(String::class.java)
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(true)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        ).also { it.put(addedFile, FileStatus.NEW) }
                    override val contentType = FakeGradleProperty(NATIVE_LIBS as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        // Make sure the output is a dir with expected contents
        assertThat(outputDir).isDirectory()
        assertThat(File(outputDir, "lib/x86/foo.so")).isFile()
        assertThat(File(outputDir, "lib/x86/bar.so")).isFile()
    }

    @Test
    fun testErrorWhenDuplicateJavaResInFeature() {
        // Create jar files from base module and feature with duplicate resources
        val jarFile1 = File(tmpDir.root, "jarFile1.jar")
        ZFile(jarFile1).use { it.add("duplicate", ByteArrayInputStream(ByteArray(0))) }

        val jarFile2 = File(tmpDir.root, "jarFile2.jar")
        ZFile(jarFile2).use { it.add("duplicate", ByteArrayInputStream(ByteArray(0))) }

        val outputFile = File(tmpDir.root, "out.jar")
        val incrementalStateFile = File(tmpDir.root, "merge-state")
        val cacheDir = File(tmpDir.root, "cacheDir")
        val workAction = object : MergeJavaResWorkAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val projectJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(jarFile1)
                    override val subProjectJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val externalLibJavaRes = FakeObjectFactory.factory.fileCollection()
                    override val featureJavaRes =
                        FakeObjectFactory.factory.fileCollection().from(jarFile2)
                    override val outputFile =
                        FakeObjectFactory.factory.fileProperty().also { it.set(outputFile) }
                    override val outputDirectory = FakeObjectFactory.factory.directoryProperty()
                    override val excludes =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultExcludes)
                        }
                    override val pickFirsts =
                        FakeObjectFactory.factory.setProperty(String::class.java)
                    override val merges =
                        FakeObjectFactory.factory.setProperty(String::class.java).also {
                            it.set(defaultMerges)
                        }
                    override val incrementalStateFile =
                        FakeObjectFactory.factory.fileProperty().also {
                            it.set(incrementalStateFile)
                        }
                    override val incremental = FakeGradleProperty(false)
                    override val cacheDir =
                        FakeObjectFactory.factory.directoryProperty().also { it.set(cacheDir) }
                    override val changedInputs =
                        FakeObjectFactory.factory.mapProperty(
                            File::class.java,
                            FileStatus::class.java
                        )
                    override val contentType = FakeGradleProperty(RESOURCES as ContentType)
                    override val noCompress =
                        FakeObjectFactory.factory.listProperty(String::class.java)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }

        val e = assertFailsWith<DuplicateRelativeFileException> { workAction.execute() }
        assertThat(e.message).isEqualTo(
            """
                2 files found with path 'duplicate' from inputs:
                 - ${jarFile1.absolutePath}
                 - ${jarFile2.absolutePath}
                Adding a packagingOptions block may help, please refer to
                https://google.github.io/android-gradle-dsl/current/com.android.build.gradle.internal.dsl.PackagingOptions.html
                for more information
            """.trimIndent()
        )
    }
}
