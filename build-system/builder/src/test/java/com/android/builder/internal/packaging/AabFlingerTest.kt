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

package com.android.builder.internal.packaging

import com.android.builder.packaging.JarFlinger
import com.android.testutils.apk.Zip
import com.android.testutils.truth.ZipFileSubject
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.Deflater

class AabFlingerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var outputFile: File

    @Before
    fun setUp() {
        outputFile = tmp.root.resolve("out.aab")
    }

    private fun getAabFlinger(outputFile: File): AabFlinger = AabFlinger(
            outputFile = outputFile,
            signerName = "test",
            privateKey = getPrivateKey(),
            certificates = getCertificates(),
            minSdkVersion = 18
    )

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
        getAabFlinger(outputFile).use {
            it.writeZip(jarInput, Deflater.DEFAULT_COMPRESSION)
        }
        Zip(outputFile).use {
            ZipFileSubject.assertThat(it).exists()
            fileInputMap.forEach { (path, content) ->
                ZipFileSubject.assertThat(it).containsFileWithContent(path, content)
            }
        }
    }

    @Test
    fun testCompression() {
        val jarInput = tmp.root.resolve("foo.jar")
        JarFlinger(jarInput.toPath()).use { jarFlinger ->
            jarFlinger.setCompressionLevel(Deflater.NO_COMPRESSION)
            val bigFile = tmp.root.resolve("bigFile.txt")
            bigFile.writeText("foo".repeat(1000))
            jarFlinger.addFile("bigFile.txt", bigFile.toPath())
        }

        val uncompressedFile = tmp.root.resolve("uncompressed.aab")

        getAabFlinger(uncompressedFile).use {
            it.writeZip(jarInput, Deflater.NO_COMPRESSION)
        }

        Zip(uncompressedFile).use {
            ZipFileSubject.assertThat(it).exists()
        }

        getAabFlinger(outputFile).use {
            it.writeZip(jarInput, Deflater.DEFAULT_COMPRESSION)
        }

        Zip(outputFile).use {
            ZipFileSubject.assertThat(it).exists()
        }

        // check that the compressed file is smaller than the uncompressed file
        Truth.assertThat(outputFile.length()).isLessThan(uncompressedFile.length())
    }

    @Test
    fun testSigning() {
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
        getAabFlinger(outputFile).use {
            it.writeZip(jarInput, Deflater.DEFAULT_COMPRESSION)
        }
        Zip(outputFile).use {
            ZipFileSubject.assertThat(it).contains("META-INF/MANIFEST.MF")
            ZipFileSubject.assertThat(it).contains("META-INF/TEST.RSA")
            ZipFileSubject.assertThat(it).contains("META-INF/TEST.SF")
        }
    }
}
