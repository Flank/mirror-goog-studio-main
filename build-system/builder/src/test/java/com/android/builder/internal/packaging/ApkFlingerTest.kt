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

import com.android.apksig.ApkVerifier
import com.android.builder.packaging.JarFlinger
import com.android.testutils.TestResources
import com.android.testutils.apk.Zip
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.tools.build.apkzlib.sign.SigningOptions
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode.COMPRESSED
import com.android.utils.FileUtils
import com.android.zipflinger.Sources
import com.android.zipflinger.ZipArchive
import com.google.common.base.Optional
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.zip.Deflater

class ApkFlingerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Mock
    lateinit var creationData: ApkCreatorFactory.CreationData

    private lateinit var apkFile: File
    private lateinit var v4SignatureFile: File

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        apkFile = tmp.root.resolve("out.apk")
        v4SignatureFile = tmp.root.resolve("out.apk.idsig")
        Mockito.`when`(creationData.apkPath).thenReturn(apkFile)
        Mockito.`when`(creationData.nativeLibrariesPackagingMode).thenReturn(COMPRESSED)
        Mockito.`when`(creationData.noCompressPredicate).thenReturn(Predicate { false })
        Mockito.`when`(creationData.signingOptions).thenReturn(Optional.absent())
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
        ApkFlinger(creationData, Deflater.BEST_SPEED).use { it.writeZip(jarInput, null, null) }
        Zip(apkFile).use {
            assertThat(it).exists()
            fileInputMap.forEach { (path, content) ->
                assertThat(it).containsFileWithContent(path, content)
            }
        }
    }

    @Test
    fun writeFile() {
        val fileInputMap = mapOf ("foo.txt" to "foo", "bar.txt" to "bar", "foo/bar.txt" to "foobar")
        ApkFlinger(creationData, Deflater.BEST_SPEED).use { apkFlinger ->
            fileInputMap.forEach { (path, content) ->
                val fileInput = tmp.root.resolve(path)
                fileInput.parentFile.mkdirs()
                fileInput.writeText(content)
                apkFlinger.writeFile(fileInput, path)
            }
        }
        Zip(apkFile).use {
            assertThat(it).exists()
            fileInputMap.forEach { (path, content) ->
                assertThat(it).containsFileWithContent(path, content)
            }
        }
    }

    @Test
    fun deleteFile() {
        val fileInputMap = mapOf ("foo.txt" to "foo", "bar.txt" to "bar", "foo/bar.txt" to "foobar")
        ApkFlinger(creationData, Deflater.BEST_SPEED).use { apkFlinger ->
            fileInputMap.forEach { (path, content) ->
                val fileInput = tmp.root.resolve(path)
                fileInput.parentFile.mkdirs()
                fileInput.writeText(content)
                apkFlinger.writeFile(fileInput, path)
            }
        }
        Zip(apkFile).use {
            assertThat(it).exists()
            fileInputMap.forEach { (path, content) ->
                assertThat(it).containsFileWithContent(path, content)
            }
        }

        ApkFlinger(creationData, Deflater.BEST_SPEED).use { it.deleteFile("bar.txt") }
        Zip(apkFile).use {
            assertThat(it).exists()
            assertThat(it).doesNotContain("bar.txt")
            assertThat(it).containsFileWithContent("foo.txt", "foo")
            assertThat(it).containsFileWithContent("foo/bar.txt", "foobar")
        }
    }

    @Test
    fun writeUncompressedFile() {
        // first write compressed file
        val bigFile = tmp.root.resolve("bigFile.txt")
        bigFile.writeText("foo".repeat(1000))
        ApkFlinger(creationData, Deflater.BEST_SPEED).use { it.writeFile(bigFile, "bigFile.txt") }
        Zip(apkFile).use {
            assertThat(it).exists()
        }
        val compressedSize = apkFile.length()

        // then write uncompressed file
        Mockito.`when`(creationData.noCompressPredicate)
            .thenReturn(Predicate<String> { it == "bigFile.txt" })
        ApkFlinger(creationData, Deflater.BEST_SPEED).use {
            it.deleteFile("bigFile.txt")
            it.writeFile(bigFile, "bigFile.txt")
        }
        Zip(apkFile).use {
            assertThat(it).exists()
        }
        val uncompressedSize = apkFile.length()

        // check that the compressed file is smaller than the uncompressed file
        assertThat(compressedSize).isLessThan(uncompressedSize)
    }

    @Test
    fun writeLargeFile() {
        val largeFile = tmp.root.resolve("largeFile.txt")
        largeFile.writeText("a".repeat(Sources.LARGE_LIMIT + 1))
        ApkFlinger(creationData, Deflater.BEST_SPEED).use {
            it.writeFile(largeFile, "largeFile.txt")
        }
        assertThat(ZipArchive.listEntries(apkFile.toPath()).keys).containsExactly("largeFile.txt")
    }

    @Test
    fun testV1Signing() {
        testSigning(
            enableV1 = true,
            enableV2 = false,
            enableV3 = false,
            enableV4 = false,
            minSdk = 1
        )
    }

    @Test
    fun testV2Signing() {
        // API 24 is the first to support V2 signing
        testSigning(
            enableV1 = false,
            enableV2 = true,
            enableV3 = false,
            enableV4 = false,
            minSdk = 24
        )
    }

    @Test
    fun testV3Signing() {
        // API 28 is the first to support V3 signing
        testSigning(
            enableV1 = false,
            enableV2 = false,
            enableV3 = true,
            enableV4 = false,
            minSdk = 28
        )
    }

    @Test
    fun testV4AndV2Signing() {
        // API 28 is the first to support V4 signing
        testSigning(
            enableV1 = false,
            enableV2 = true,
            enableV3 = false,
            enableV4 = true,
            minSdk = 28
        )
    }

    @Test
    fun testV4AndV3Signing() {
        // API 28 is the first to support V4 signing
        testSigning(
            enableV1 = false,
            enableV2 = false,
            enableV3 = true,
            enableV4 = true,
            minSdk = 28
        )
    }

    @Test
    fun testSigningWithAllVersions() {
        testSigning(
            enableV1 = true,
            enableV2 = true,
            enableV3 = true,
            enableV4 = true,
            minSdk = 1
        )
    }

    private fun testSigning(
        enableV1: Boolean,
        enableV2: Boolean,
        enableV3: Boolean,
        enableV4: Boolean,
        minSdk: Int
    ) {
        FileUtils.copyFile(TestResources.getFile("/testData/packaging/test.apk"), apkFile)
        // Delete META-INF files with signing disabled
        Mockito.`when`(creationData.signingOptions).thenReturn(Optional.absent())
        ApkFlinger(creationData, Deflater.BEST_SPEED).use {
            it.deleteFile("META-INF/MANIFEST.MF")
            it.deleteFile("META-INF/CERT.RSA")
            it.deleteFile("META-INF/CERT.SF")
        }
        Zip(apkFile).use {
            assertThat(it).doesNotContain("META-INF/MANIFEST.MF")
            assertThat(it).doesNotContain("META-INF/CERT.RSA")
            assertThat(it).doesNotContain("META-INF/CERT.SF")
        }
        // Then sign and verify apk
        val signingOptions =
            SigningOptions.builder()
                .setV1SigningEnabled(enableV1)
                .setV2SigningEnabled(enableV2)
                .setMinSdkVersion(minSdk)
                .setKey(getPrivateKey())
                .setCertificates(ImmutableList.copyOf(getCertificates()))
                .build()
        Mockito.`when`(creationData.signingOptions).thenReturn(Optional.of(signingOptions))
        ApkFlinger(
            creationData,
            Deflater.BEST_SPEED,
            enableV3Signing = enableV3,
            enableV4Signing = enableV4
        ).use {}
        if (enableV1) {
            Zip(apkFile).use {
                assertThat(it).contains("META-INF/MANIFEST.MF")
                assertThat(it).contains("META-INF/CERT.RSA")
                assertThat(it).contains("META-INF/CERT.SF")
            }
        }
        verifyApk(apkFile, minSdk, enableV4)
    }

    private fun verifyApk(apk: File, minSdk: Int, v4Enabled: Boolean) {
        val apkVerifier =
            ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(minSdk)
                .also {
                    if (v4Enabled) {
                        it.setV4SignatureFile(File("${apk.absolutePath}.idsig"))
                    }
                }
                .build()
        assertThat(apkVerifier.verify().isVerified).isTrue()
    }
}
