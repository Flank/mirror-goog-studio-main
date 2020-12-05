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

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.android.testutils.truth.PathSubject.assertThat
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

class ExtractProGuardRulesTransformTest {

    private val slash = File.separator

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testNoRules_UnrelatedFile() {
        val jarFile = createZip("bar.txt" to "hello")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.rootDir)).isEmpty()
    }

    @Test
    fun testNoRules_FolderExists() {
        val jarFile = createZip("META-INF/proguard" to null)
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.rootDir)).isEmpty()
    }

    @Test
    fun testSingleRuleFile() {
        val jarFile = createZip("META-INF/proguard/foo.txt" to "bar")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.outputDirectory)).containsExactly("lib${slash}META-INF${slash}proguard${slash}foo.txt")
        assertThat(transformOutputs.outputDirectory.resolve("lib${slash}META-INF${slash}proguard${slash}foo.txt")).hasContents("bar")
    }

    @Test
    fun testSingleRuleFile_startingWithSlash() {
        val jarFile = createZip("/META-INF/proguard/foo.txt" to "bar")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.outputDirectory)).containsExactly("lib${slash}META-INF${slash}proguard${slash}foo.txt")
        assertThat(transformOutputs.outputDirectory.resolve("lib${slash}META-INF${slash}proguard${slash}foo.txt")).hasContents("bar")
    }

    @Test
    fun testMultipleRuleFiles() {
        val jarFile = createZip(
            "META-INF/proguard/bar.txt" to "hello",
            "META-INF/proguard/foo.pro" to "goodbye")
        val transformOutputs = FakeTransformOutputs(tmp)
        createTransform(jarFile).transform(transformOutputs)

        assertThat(getProducedFileNames(transformOutputs.outputDirectory)).containsExactly("lib${slash}META-INF${slash}proguard${slash}foo.pro", "lib${slash}META-INF${slash}proguard${slash}bar.txt")
        assertThat(transformOutputs.outputDirectory.resolve("lib${slash}META-INF${slash}proguard${slash}foo.pro")).hasContents("goodbye")
        assertThat(transformOutputs.outputDirectory.resolve("lib${slash}META-INF${slash}proguard${slash}bar.txt")).hasContents("hello")
    }

    private fun getProducedFileNames(rootDir: File): List<String> = rootDir
        .walk()
        .filter { !it.isDirectory }
        .map { it.relativeTo(rootDir).path }
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

    private fun createTransform(primaryInput: File): ExtractProGuardRulesTransform {
        return object: ExtractProGuardRulesTransform() {
            override val inputArtifact: Provider<FileSystemLocation> = FakeGradleProvider(FakeGradleRegularFile(primaryInput))

            override fun getParameters(): GenericTransformParameters {
                return object : GenericTransformParameters {
                    override val projectName: Property<String> = FakeGradleProperty("")
                }
            }
        }
    }
}
