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
import com.android.testutils.TestResources
import com.android.testutils.truth.ZipFileSubject.assertThatZip
import com.android.tools.build.apkzlib.sign.SigningOptions
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode.COMPRESSED
import com.android.utils.FileUtils
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
import com.android.builder.packaging.JarFlinger
import com.android.sdklib.SdkVersionInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.Deflater

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
        assertThatZip(apkFile).exists()
        fileInputMap.forEach { (path, content) ->
            assertThatZip(apkFile).containsFileWithContent(path, content)
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
        assertThatZip(apkFile).exists()
        fileInputMap.forEach { (path, content) ->
            assertThatZip(apkFile).containsFileWithContent(path, content)
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
        assertThatZip(apkFile).exists()
        fileInputMap.forEach { (path, content) ->
            assertThatZip(apkFile).containsFileWithContent(path, content)
        }
        ApkFlinger(creationData, Deflater.BEST_SPEED).use { it.deleteFile("bar.txt") }
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
        ApkFlinger(creationData, Deflater.BEST_SPEED).use { it.writeFile(bigFile, "bigFile.txt") }
        assertThatZip(apkFile).exists()
        val compressedSize = apkFile.length()

        // then write uncompressed file
        Mockito.`when`(creationData.noCompressPredicate)
            .thenReturn(Predicate<String> { it == "bigFile.txt" })
        ApkFlinger(creationData, Deflater.BEST_SPEED).use {
            it.deleteFile("bigFile.txt")
            it.writeFile(bigFile, "bigFile.txt")
        }
        assertThatZip(apkFile).exists()
        val uncompressedSize = apkFile.length()

        // check that the compressed file is smaller than the uncompressed file
        assertThat(compressedSize).isLessThan(uncompressedSize)
    }

    @Test
    fun signApk() {
        // First copy existing test apk
        FileUtils.copyFile(TestResources.getFile("/testData/packaging/test.apk"), apkFile)
        for (minSdk in 1..SdkVersionInfo.HIGHEST_KNOWN_API) {
            // Delete META-INF files with signing disabled
            Mockito.`when`(creationData.signingOptions).thenReturn(Optional.absent())
            ApkFlinger(creationData, Deflater.BEST_SPEED).use {
                it.deleteFile("META-INF/MANIFEST.MF")
                it.deleteFile("META-INF/CERT.RSA")
                it.deleteFile("META-INF/CERT.SF")
            }
            assertThatZip(apkFile).doesNotContain("META-INF/MANIFEST.MF")
            assertThatZip(apkFile).doesNotContain("META-INF/CERT.RSA")
            assertThatZip(apkFile).doesNotContain("META-INF/CERT.SF")
            // Then sign and verify apk
            val signingOptions =
                SigningOptions.builder()
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setMinSdkVersion(minSdk)
                    .setKey(getPrivateKey())
                    .setCertificates(ImmutableList.copyOf(getCertificates()))
                    .build()
            Mockito.`when`(creationData.signingOptions).thenReturn(Optional.of(signingOptions))
            ApkFlinger(creationData, Deflater.BEST_SPEED).use {}
            assertThatZip(apkFile).contains("META-INF/MANIFEST.MF")
            assertThatZip(apkFile).contains("META-INF/CERT.RSA")
            assertThatZip(apkFile).contains("META-INF/CERT.SF")
            verifyApk(apkFile, minSdk)
        }
    }

    private fun getPrivateKey(): PrivateKey {
        val bytes =
            Files.readAllBytes(TestResources.getFile("/testData/packaging/rsa-2048.pk8").toPath())
        return KeyFactory.getInstance("rsa").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun getCertificates(): List<X509Certificate> {
        val bytes =
            Files.readAllBytes(
                TestResources.getFile("/testData/packaging/rsa-2048.x509.pem").toPath()
            )
        return CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(bytes))
            .map { it as X509Certificate }
    }

    private fun verifyApk(apk: File, minSdk: Int) {
        val apkVerifier = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(minSdk).build()
        assertThat(apkVerifier.verify().isVerified).isTrue()
    }
}