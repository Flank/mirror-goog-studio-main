/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.builder.internal.packaging

import com.android.testutils.truth.ZipFileSubject.assertThatZip
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode.COMPRESSED
import com.google.common.base.Predicate
import com.google.common.truth.Truth.assertThat

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import zipflinger.JarFlinger
import java.io.File

class ApkFlingerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Mock
    lateinit var creationData: ApkCreatorFactory.CreationData

    private lateinit var apkFile: File

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        apkFile = tmp.root.resolve("out.apk")
        Mockito.`when`(creationData.apkPath).thenReturn(apkFile)
        Mockito.`when`(creationData.nativeLibrariesPackagingMode).thenReturn(COMPRESSED)
        Mockito.`when`(creationData.noCompressPredicate).thenReturn(Predicate<String> { false })
    }

    @Test
    fun writeZip() {
        val fileInputMap = mapOf ("foo.txt" to "foo", "bar.txt" to "bar", "foo/bar.txt" to "foobar")
        val jarInput = tmp.root.resolve("foo.jar")
        JarFlinger(jarInput.toPath()).use { jarFlinger ->
            fileInputMap.forEach { (path, content) ->
                val fileInput = tmp.root.resolve(path)
                fileInput.parentFile.mkdirs()
                fileInput.writeText(content)
                jarFlinger.addFile(path, fileInput.toPath())
            }
        }
        ApkFlinger(creationData).use { it.writeZip(jarInput, null, null) }
        assertThatZip(apkFile).exists()
        fileInputMap.forEach { (path, content) ->
            assertThatZip(apkFile).containsFileWithContent(path, content)
        }
    }

    @Test
    fun writeFile() {
        val fileInputMap = mapOf ("foo.txt" to "foo", "bar.txt" to "bar", "foo/bar.txt" to "foobar")
        ApkFlinger(creationData).use { apkFlinger ->
            fileInputMap.forEach { (path, content) ->
                val fileInput = tmp.root.resolve(path)
                fileInput.parentFile.mkdirs()
                fileInput.writeText(content)
                apkFlinger.writeFile(fileInput, path)
            }
        }
        assertThatZip(apkFile).exists()
        fileInputMap.forEach { (path, content) ->
            assertThatZip(apkFile).containsFileWithContent(path, content)
        }
    }

    @Test
    fun deleteFile() {
        val fileInputMap = mapOf ("foo.txt" to "foo", "bar.txt" to "bar", "foo/bar.txt" to "foobar")
        ApkFlinger(creationData).use { apkFlinger ->
            fileInputMap.forEach { (path, content) ->
                val fileInput = tmp.root.resolve(path)
                fileInput.parentFile.mkdirs()
                fileInput.writeText(content)
                apkFlinger.writeFile(fileInput, path)
            }
        }
        assertThatZip(apkFile).exists()
        fileInputMap.forEach { (path, content) ->
            assertThatZip(apkFile).containsFileWithContent(path, content)
        }
        ApkFlinger(creationData).use { it.deleteFile("bar.txt") }
        assertThatZip(apkFile).exists()
        assertThatZip(apkFile).doesNotContain("bar.txt")
        assertThatZip(apkFile).containsFileWithContent("foo.txt", "foo")
        assertThatZip(apkFile).containsFileWithContent("foo/bar.txt", "foobar")
    }

    @Test
    fun writeUncompressedFile() {
        // first write compressed file
        val bigFile = tmp.root.resolve("bigFile.txt")
        bigFile.writeText("foo".repeat(1000))
        ApkFlinger(creationData).use { it.writeFile(bigFile, "bigFile.txt") }
        assertThatZip(apkFile).exists()
        val compressedSize = apkFile.length()

        // then write uncompressed file
        Mockito.`when`(creationData.noCompressPredicate)
            .thenReturn(Predicate<String> { it == "bigFile.txt" })
        ApkFlinger(creationData).use {
            it.deleteFile("bigFile.txt")
            it.writeFile(bigFile, "bigFile.txt")
        }
        assertThatZip(apkFile).exists()
        val uncompressedSize = apkFile.length()

        // check that the compressed file is smaller than the uncompressed file
        assertThat(compressedSize).isLessThan(uncompressedSize)
    }

    // TODO test alignment
}