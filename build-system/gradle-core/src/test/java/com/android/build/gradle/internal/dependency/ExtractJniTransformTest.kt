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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants.FN_GDBSERVER
import com.android.SdkConstants.FN_GDB_SETUP
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractJniTransformTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testNoNativeLibs_UnrelatedFile() {
        val jarFile = createZip("foo.txt" to "foo")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.rootDir)).isEmpty()
    }

    @Test
    fun testNoNativeLibs_UnrelatedFileInNativeLibFolder() {
        val jarFile = createZip("lib/x86/foo.txt" to "foo")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.rootDir)).isEmpty()
    }

    @Test
    fun testSingleNativeLib() {
        val jarFile = createZip("lib/x86/foo.so" to "foo")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.outputDirectory))
            .containsExactly("x86/foo.so")
        assertThat(File(transformOutputs.outputDirectory, "x86/foo.so")).hasContents("foo")
    }

    @Test
    fun testMultipleNativeLibs() {
        val jarFile = createZip(
            "lib/x86/foo.so" to "x86 foo",
            "lib/x86/bar.so" to "x86 bar",
            "lib/x86_64/foo.so" to "x86_64 foo",
            "lib/x86_64/bar.so" to "x86_64 bar",
            "lib/future_abi/foo.so" to "future_abi foo",
            "lib/future_abi/bar.so" to "future_abi bar",
            "lib/x86/$FN_GDBSERVER" to "x86 gdbserver",
            "lib/x86/$FN_GDB_SETUP" to "x86 gdb.setup",
            "lib/x86/foo.txt" to "invalid",
            "incorrect_path/foo.so" to "invalid",
            "foo.so" to "invalid")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.outputDirectory))
            .containsExactly(
                "x86/foo.so",
                "x86/bar.so",
                "x86_64/foo.so",
                "x86_64/bar.so",
                "future_abi/foo.so",
                "future_abi/bar.so",
                "x86/$FN_GDBSERVER",
                "x86/$FN_GDB_SETUP"
            )
        assertThat(File(transformOutputs.outputDirectory, "x86/foo.so")).hasContents("x86 foo")
        assertThat(File(transformOutputs.outputDirectory, "x86/bar.so")).hasContents("x86 bar")
        assertThat(File(transformOutputs.outputDirectory, "x86_64/foo.so"))
            .hasContents("x86_64 foo")
        assertThat(File(transformOutputs.outputDirectory, "x86_64/bar.so"))
            .hasContents("x86_64 bar")
        assertThat(File(transformOutputs.outputDirectory, "future_abi/foo.so"))
            .hasContents("future_abi foo")
        assertThat(File(transformOutputs.outputDirectory, "future_abi/bar.so"))
            .hasContents("future_abi bar")
        assertThat(File(transformOutputs.outputDirectory, "x86/$FN_GDBSERVER"))
            .hasContents("x86 gdbserver")
        assertThat(File(transformOutputs.outputDirectory, "x86/$FN_GDB_SETUP"))
            .hasContents("x86 gdb.setup")
    }

    private fun getProducedFileNames(rootDir: File): List<String> = rootDir
        .walk()
        .filter { !it.isDirectory }
        .map { FileUtils.toSystemIndependentPath(it.relativeTo(rootDir).path) }
        .toList()

    private fun createZip(vararg entries: Pair<String, String?>): File {
        val zipFile = tmp.newFile()
        ZipOutputStream(FileOutputStream(zipFile)).use {
            for (entry in entries) {
                it.putNextEntry(ZipEntry(entry.first))
                if (entry.second != null) {
                    it.write(entry.second!!.toByteArray())
                }
                it.closeEntry()
            }
        }
        return zipFile
    }

    private fun createTransform(primaryInput: File): ExtractJniTransform {
        return object: ExtractJniTransform() {
            override val inputJar: Provider<FileSystemLocation> =
                FakeGradleProvider(FakeGradleRegularFile(primaryInput))

            override fun getParameters(): GenericTransformParameters {
                return object : GenericTransformParameters {
                    override val projectName: Property<String> = FakeGradleProperty("")
                }
            }
        }
    }
}
