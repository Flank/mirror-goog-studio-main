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

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.testutils.TestInputsGenerator
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.RuntimeException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.fail

class ExtractProfilerNativeDependenciesTaskTest {
    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private lateinit var jarWithoutDeps: File
    private lateinit var jarWithoutNativeDeps: File
    private lateinit var jarWithNativeDep: File
    private lateinit var jarWithDuplicateNativeDep: File
    private lateinit var jarWithMultipleNativeDeps: File

    @Before
    fun setUp() {
        val tempFolder = temp.newFolder()

        val fooJar = tempFolder.resolve("foo.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(it.toPath(), listOf("foo"))
        }

        val nativeLibJar1 = tempFolder.resolve("nativeLibJar1.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(it.toPath(), listOf("lib/x86/foo.so"))
        }

        val nativeLibJar2 = tempFolder.resolve("nativeLibJar2.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(it.toPath(), listOf("lib/x86/bar.so"))
        }

        val nativeLibJar3 = tempFolder.resolve("nativeLibJar3.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(it.toPath(), listOf("lib/x86/baz.so"))
        }

        jarWithoutDeps = tempFolder.resolve("jarWithoutDeps.jar").also { jarJar ->
            ZipOutputStream(FileOutputStream(jarJar)).use { zip ->
                zip.putNextEntry(ZipEntry("foo.jar"))
                BufferedInputStream(FileInputStream(fooJar)).use {
                    ByteStreams.copy(it, zip)
                }
            }
        }

        jarWithoutNativeDeps = tempFolder.resolve("jarWithoutNativeDeps.jar").also { jarJar ->
            ZipOutputStream(FileOutputStream(jarJar)).use { zip ->
                zip.putNextEntry(ZipEntry("foo.jar"))
                BufferedInputStream(FileInputStream(fooJar)).use {
                    ByteStreams.copy(it, zip)
                }
                zip.putNextEntry(ZipEntry("dependencies/foo.jar"))
                BufferedInputStream(FileInputStream(fooJar)).use {
                    ByteStreams.copy(it, zip)
                }
                zip.putNextEntry(ZipEntry("dependencies/foo2.jar"))
                BufferedInputStream(FileInputStream(fooJar)).use {
                    ByteStreams.copy(it, zip)
                }
            }
        }

        jarWithNativeDep = tempFolder.resolve("jarWithNativeDep.jar").also { jarJar ->
            ZipOutputStream(FileOutputStream(jarJar)).use { zip ->
                zip.putNextEntry(ZipEntry("dependencies/nativeLibJar1.jar"))
                BufferedInputStream(FileInputStream(nativeLibJar1)).use {
                    ByteStreams.copy(it, zip)
                }
            }
        }

        jarWithDuplicateNativeDep =
            tempFolder.resolve("jarWithDuplicateNativeDep.jar").also { jarJar ->
                ZipOutputStream(FileOutputStream(jarJar)).use { zip ->
                    zip.putNextEntry(ZipEntry("dependencies/duplicateNativeLibJar1.jar"))
                    BufferedInputStream(FileInputStream(nativeLibJar1)).use {
                        ByteStreams.copy(it, zip)
                    }
                }
            }

        jarWithMultipleNativeDeps =
            tempFolder.resolve("jarWithMultipleNativeDeps.jar").also { jarJar ->
                ZipOutputStream(FileOutputStream(jarJar)).use { zip ->
                    zip.putNextEntry(ZipEntry("dependencies/nativeLibJar2.jar"))
                    BufferedInputStream(FileInputStream(nativeLibJar2)).use {
                        ByteStreams.copy(it, zip)
                    }
                    zip.putNextEntry(ZipEntry("dependencies/nativeLibJar3.jar"))
                    BufferedInputStream(FileInputStream(nativeLibJar3)).use {
                        ByteStreams.copy(it, zip)
                    }
                }
            }
    }

    @Test
    fun testNoDependencyJars() {
        val outputDir = temp.newFolder()

        executeWorkerAction(listOf(jarWithoutDeps), outputDir)

        assertThat(outputDir.listFiles()!!).isEmpty()
    }

    @Test
    fun testNoNativeLibsInDependencyJars() {
        val outputDir = temp.newFolder()

        executeWorkerAction(listOf(jarWithoutNativeDeps), outputDir)

        assertThat(outputDir.listFiles()!!).isEmpty()
    }

    @Test
    fun testNativeLibExtraction() {
        val outputDir = temp.newFolder()

        executeWorkerAction(listOf(jarWithNativeDep), outputDir)

        assertThat(outputDir.list()!!.asList()).containsExactly("x86")
        assertThat(outputDir.resolve("x86").list()!!.asList()).containsExactly("foo.so")
    }

    @Test
    fun testNativeLibExtractionFromMultiple() {
        val outputDir = temp.newFolder()

        executeWorkerAction(
            listOf(
                jarWithoutDeps,
                jarWithoutNativeDeps,
                jarWithNativeDep,
                jarWithMultipleNativeDeps
            ),
            outputDir
        )

        assertThat(outputDir.list()!!.asList()).containsExactly("x86")
        assertThat(outputDir.resolve("x86").list()!!.asList())
            .containsExactly("foo.so", "bar.so", "baz.so")
    }

    @Test
    fun testErrorFromDuplicateNativeLibs() {
        val outputDir = temp.newFolder()

        try {
            executeWorkerAction(listOf(jarWithNativeDep, jarWithDuplicateNativeDep), outputDir)
            fail("Expected worker action to fail because of duplicate native libs")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains(
                "Unexpected duplicate profiler native dependency: x86/foo.so"
            )
        }
    }

    @Test
    fun testOldNativeLibsDeleted() {
        val outputDir = temp.newFolder()

        executeWorkerAction(listOf(jarWithNativeDep), outputDir)

        assertThat(outputDir.list()!!.asList()).containsExactly("x86")
        assertThat(outputDir.resolve("x86").list()!!.asList()).containsExactly("foo.so")

        executeWorkerAction(listOf(jarWithMultipleNativeDeps), outputDir)

        assertThat(outputDir.list()!!.asList()).containsExactly("x86")
        assertThat(outputDir.resolve("x86").list()!!.asList()).containsExactly("bar.so", "baz.so")
    }


    private fun executeWorkerAction(inputJars: List<File>, outputDir: File) {
        object : ExtractProfilerNativeDependenciesTask.ExtractProfilerNativeDepsWorkerAction() {
            override fun getParameters() = object : Parameters() {
                override val outputDir = FakeObjectFactory.factory.directoryProperty().fileValue(outputDir)
                override val inputJars = FakeObjectFactory.factory.fileCollection().from(inputJars)
                override val projectPath = FakeGradleProperty("projectName")
                override val taskOwner = FakeGradleProperty("taskOwner")
                override val workerKey = FakeGradleProperty("workerKey")
                override val analyticsService: Property<AnalyticsService>
                        = FakeGradleProperty(FakeNoOpAnalyticsService())
            }
        }.execute()
    }
}
