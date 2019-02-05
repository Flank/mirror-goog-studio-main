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

import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThatZip

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope.FEATURES
import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS
import com.android.builder.files.IncrementalRelativeFileSets
import com.android.builder.files.RelativeFile
import com.android.builder.files.RelativeFiles
import com.android.builder.merge.DuplicateRelativeFileException
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.builder.merge.LazyIncrementalFileMergerInput
import com.android.ide.common.resources.FileStatus
import com.android.tools.build.apkzlib.utils.CachedSupplier
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableMap
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Test cases for [MergeJavaResourcesDelegate].  */
class MergeJavaResourcesDelegateTest {

    @get:Rule
    var tmpDir = TemporaryFolder()

    private lateinit var outputFile: File
    private lateinit var outputDir: File
    private lateinit var packagingOptions: PackagingOptions
    private lateinit var incrementalStateFile: File

    @Before
    fun setUp() {
        outputFile = File(tmpDir.root, "out.jar")
        outputDir = File(tmpDir.root, "out")
        packagingOptions = PackagingOptions()
        incrementalStateFile = File(tmpDir.root, "merge-state")
    }

    @Test
    fun testMergeResources() {
        // Create first jar file containing resources to be merged
        val jarFile1 = File(tmpDir.root, "jarFile1.jar")
        ZFile(jarFile1).use {
            it.add("fileEndingWithDot.", ByteArrayInputStream(ByteArray(0)))
            it.add("fileNotEndingWithDot", ByteArrayInputStream(ByteArray(0)))
        }
        val input1 = createIncrementalFilerMergerInputFromJar(jarFile1)

        // Create second jar file containing some resources to merge, and some to exclude
        val jarFile2 = File(tmpDir.root, "jarFile2.jar")
        ZFile(jarFile2).use {
            it.add("javaResFromJarFile2", ByteArrayInputStream(ByteArray(0)))
            it.add("LICENSE", ByteArrayInputStream(ByteArray(0)))
        }
        val input2 = createIncrementalFilerMergerInputFromJar(jarFile2)

        val contentMap = mutableMapOf(
            Pair(input1, createQualifiedContent(jarFile1, PROJECT, RESOURCES)),
            Pair(input2, createQualifiedContent(jarFile2, SUB_PROJECTS, RESOURCES))
        )

        val delegate = MergeJavaResourcesDelegate(
            listOf(input1, input2),
            outputFile,
            contentMap,
            ParsedPackagingOptions(packagingOptions),
            RESOURCES,
            incrementalStateFile,
            isIncremental = true
        )

        delegate.run()

        // Make sure the output is a jar file with expected contents
        assertThat(outputFile).isFile()
        assertThatZip(outputFile).contains("fileEndingWithDot.")
        assertThatZip(outputFile).contains("fileNotEndingWithDot")
        assertThatZip(outputFile).contains("javaResFromJarFile2")
        assertThatZip(outputFile).doesNotContain("LICENSE")
        // regression test for b/65337573
        assertThat(File(outputFile, "fileEndingWithDot.")).doesNotExist()
        assertThat(File(outputFile, "fileNotEndingWithDot")).doesNotExist()
    }


    @Test
    fun testMergeNativeLibs() {
        // Create first dir containing native libs to be merged
        val dir1 = File(tmpDir.root, "dir1")
        FileUtils.createFile(File(dir1, "x86/foo.so"), "foo")
        FileUtils.createFile(File(dir1, "x86/bar.so"), "bar")
        FileUtils.createFile(File(dir1, "x86/notAnSoFile"), "ignore me")
        val input1 = createIncrementalFilerMergerInputFromDir(dir1)

        // Create second dir containing native libs to be merged
        val dir2 = File(tmpDir.root, "dir2")
        FileUtils.createFile(File(dir2, "x86/baz.so"), "baz")
        FileUtils.createFile(File(dir2, "x86/exclude.so"), "exclude me")
        val input2 = createIncrementalFilerMergerInputFromDir(dir2)

        val contentMap = mutableMapOf(
            Pair(input1, createQualifiedContent(dir1, PROJECT, NATIVE_LIBS)),
            Pair(input2, createQualifiedContent(dir2, SUB_PROJECTS, NATIVE_LIBS))
        )

        // edit packagingOptions to exclude exclude.so
        packagingOptions.exclude("**/exclude.so")

        val delegate = MergeJavaResourcesDelegate(
            listOf(input1, input2),
            outputDir,
            contentMap,
            ParsedPackagingOptions(packagingOptions),
            NATIVE_LIBS,
            incrementalStateFile,
            isIncremental = true
        )

        delegate.run()

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
        val input = createIncrementalFilerMergerInputFromJar(jarFile)

        val contentMap =
            mutableMapOf(Pair(input, createQualifiedContent(jarFile, PROJECT, RESOURCES)))

        val delegate = MergeJavaResourcesDelegate(
            listOf(input),
            outputFile,
            contentMap,
            ParsedPackagingOptions(packagingOptions),
            RESOURCES,
            incrementalStateFile,
            isIncremental = true
        )

        // check that no incremental info saved before first merge
        assertThat(incrementalStateFile).doesNotExist()

        delegate.run()

        // check that incremental info saved
        assertThat(incrementalStateFile).isFile()
        // Make sure the output is a jar file with expected contents
        assertThat(outputFile).isFile()
        assertThatZip(outputFile).contains("javaRes1")
        assertThatZip(outputFile).doesNotContain("javaRes2")

        // Now add a resource to the jar file and merge incrementally
        ZFile(jarFile).use {
            it.add("javaRes2", ByteArrayInputStream(ByteArray(0)))
        }

        val incrementalInput = LazyIncrementalFileMergerInput(
            jarFile.absolutePath,
            CachedSupplier {
                ImmutableMap.of(RelativeFile(jarFile, "javaRes2"), FileStatus.NEW)
            },
            CachedSupplier { RelativeFiles.fromZip(jarFile) }
        )

        val incrementalContentMap = mutableMapOf(
            Pair(
                incrementalInput as IncrementalFileMergerInput,
                createQualifiedContent(jarFile, PROJECT, RESOURCES)
            )
        )

        val incrementalDelegate = MergeJavaResourcesDelegate(
            listOf(incrementalInput),
            outputFile,
            incrementalContentMap,
            ParsedPackagingOptions(packagingOptions),
            RESOURCES,
            incrementalStateFile,
            isIncremental = true
        )

        incrementalDelegate.run()

        // Make sure the output is a jar file with expected contents
        assertThat(outputFile).isFile()
        assertThatZip(outputFile).contains("javaRes1")
        assertThatZip(outputFile).contains("javaRes2")
    }

    @Test
    fun testIncrementalMergeNativeLibs() {
        // Create first dir containing native libs to be merged
        val dir = File(tmpDir.root, "dir")
        FileUtils.createFile(File(dir, "x86/foo.so"), "foo")
        val input = createIncrementalFilerMergerInputFromDir(dir)

        val contentMap =
            mutableMapOf(Pair(input, createQualifiedContent(dir, PROJECT, NATIVE_LIBS)))

        val delegate = MergeJavaResourcesDelegate(
            listOf(input),
            outputDir,
            contentMap,
            ParsedPackagingOptions(packagingOptions),
            NATIVE_LIBS,
            incrementalStateFile,
            isIncremental = true
        )

        // check that no incremental info saved before first merge
        assertThat(incrementalStateFile).doesNotExist()

        delegate.run()

        // check that incremental info saved
        assertThat(incrementalStateFile).isFile()
        // Make sure the output is a dir with expected contents
        assertThat(outputDir).isDirectory()
        assertThat(File(outputDir, "lib/x86/foo.so")).isFile()
        assertThat(File(outputDir, "lib/x86/bar.so")).doesNotExist()

        // Now add a .so file to the dir and merge incrementally
        FileUtils.createFile(File(dir, "x86/bar.so"), "bar")

        val incrementalInput = LazyIncrementalFileMergerInput(
            dir.absolutePath,
            CachedSupplier {
                ImmutableMap.of(RelativeFile(dir, "x86/bar.so"), FileStatus.NEW)
            },
            CachedSupplier { RelativeFiles.fromDirectory(dir) }
        )

        val incrementalContentMap = mutableMapOf(
            Pair(
                incrementalInput as IncrementalFileMergerInput,
                createQualifiedContent(dir, PROJECT, NATIVE_LIBS)
            )
        )

        val incrementalDelegate = MergeJavaResourcesDelegate(
            listOf(incrementalInput),
            outputDir,
            incrementalContentMap,
            ParsedPackagingOptions(packagingOptions),
            NATIVE_LIBS,
            incrementalStateFile,
            isIncremental = true
        )

        incrementalDelegate.run()

        // Make sure the output is a dir with expected contents
        assertThat(outputDir).isDirectory()
        assertThat(File(outputDir, "lib/x86/foo.so")).isFile()
        assertThat(File(outputDir, "lib/x86/bar.so")).isFile()
    }

    @Test(expected = DuplicateRelativeFileException::class)
    fun testErrorWhenDuplicateJavaResInFeature() {
        // Create jar files from base module and feature with duplicate resources
        val jarFile1 = File(tmpDir.root, "jarFile1.jar")
        ZFile(jarFile1).use { it.add("duplicate", ByteArrayInputStream(ByteArray(0))) }
        val input1 = createIncrementalFilerMergerInputFromJar(jarFile1)

        val jarFile2 = File(tmpDir.root, "jarFile2.jar")
        ZFile(jarFile2).use { it.add("duplicate", ByteArrayInputStream(ByteArray(0))) }
        val input2 = createIncrementalFilerMergerInputFromJar(jarFile2)

        val contentMap = mutableMapOf(
            Pair(input1, createQualifiedContent(jarFile1, PROJECT, RESOURCES)),
            Pair(input2, createQualifiedContent(jarFile2, FEATURES, RESOURCES))
        )

        val delegate = MergeJavaResourcesDelegate(
            listOf(input1, input2),
            outputFile,
            contentMap,
            ParsedPackagingOptions(packagingOptions),
            RESOURCES,
            incrementalStateFile,
            isIncremental = true
        )

        delegate.run()
    }

    private fun createIncrementalFilerMergerInputFromJar(jar: File): IncrementalFileMergerInput {
        assertThat(jar).isFile()
        return LazyIncrementalFileMergerInput(
            jar.absolutePath,
            CachedSupplier { IncrementalRelativeFileSets.fromZip(jar) },
            CachedSupplier { RelativeFiles.fromZip(jar) }
        )
    }

    private fun createIncrementalFilerMergerInputFromDir(dir: File): IncrementalFileMergerInput {
        assertThat(dir).isDirectory()
        return LazyIncrementalFileMergerInput(
            dir.absolutePath,
            CachedSupplier { IncrementalRelativeFileSets.fromDirectory(dir) },
            CachedSupplier { RelativeFiles.fromDirectory(dir) }
        )
    }

    private fun createQualifiedContent(
        file: File,
        scopeType: ScopeType,
        contentType: ContentType
    ): QualifiedContent {
        return object: QualifiedContent {
            override fun getName() = "foo"
            override fun getFile() = file
            override fun getContentTypes() = mutableSetOf(contentType)
            override fun getScopes() = mutableSetOf(scopeType)
        }
    }
}
