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

package com.android.build.gradle.internal.packaging

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.builder.model.SigningConfig
import com.android.ide.common.signing.KeystoreHelper
import com.android.testutils.TestResources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import shadow.bundletool.com.android.apksig.ApkVerifier
import java.io.File
import java.security.KeyStore

private const val KEY_ALIAS = "key0"

class IncrementalPackagerBuilderTest {

    lateinit var signingConfig: SigningConfig

    @JvmField
    @Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {

        val keyStoreFile = tmp.newFile()

        KeystoreHelper.createDebugStore(
            KeyStore.getDefaultType(),
            keyStoreFile,
            "",
            "",
            KEY_ALIAS,
            LoggerWrapper(FakeLogger())
        )

        signingConfig = object : SigningConfig {
            override fun getName() = "name"

            override fun getStoreFile() = keyStoreFile

            override fun getStorePassword() = ""

            override fun getKeyAlias() = KEY_ALIAS

            override fun getKeyPassword() = ""

            override fun getStoreType() = null

            override fun isV1SigningEnabled() = true

            override fun isV2SigningEnabled() = true

            override fun isSigningReady() = true
        }
    }

    @Test
    fun testNoV1SignatureWhenSdk24() {
        val outputFile = tmp.root.resolve("signed.apk")
        TestResources.getFile("/com/android/build/gradle/internal/packaging/minsdk24.apk").copyTo(outputFile)

        sign(outputFile, 24)

        val result = ApkVerifier.Builder(outputFile).build().verify()

        assertTrue(result.isVerified)
        assertFalse(result.isVerifiedUsingV1Scheme)
        assertTrue(result.isVerifiedUsingV2Scheme)
    }

    @Test
    fun testV1SignatureWhenSdkLessThan24() {
        val outputFile = tmp.root.resolve("signed.apk")
        TestResources.getFile("/com/android/build/gradle/internal/packaging/minsdk23.apk").copyTo(outputFile)

        sign(outputFile, 23)

        val result = ApkVerifier.Builder(outputFile).build().verify()

        assertTrue(result.isVerified)
        assertTrue(result.isVerifiedUsingV1Scheme)
        assertTrue(result.isVerifiedUsingV2Scheme)
    }

    private fun sign(outputFile: File, minSdk: Int) {
        val intermediateDir = tmp.newFolder("intermediateDir")
        // Signs during the .close()
        IncrementalPackagerBuilder(IncrementalPackagerBuilder.ApkFormat.FILE)
            .withSigning(signingConfig, minSdk)
            .withOutputFile(outputFile)
            .withKeepTimestampsInApk(false)
            .withIntermediateDir(intermediateDir)
            .build().use { }
    }
}