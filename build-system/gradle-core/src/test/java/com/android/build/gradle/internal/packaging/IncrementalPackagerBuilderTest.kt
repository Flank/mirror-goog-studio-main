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
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.builder.internal.packaging.ApkCreatorType
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

    lateinit var signingConfig: SigningConfigData

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

        signingConfig = SigningConfigData(
            name = "name",
            storeType = null,
            storeFile = keyStoreFile,
            storePassword = "",
            keyAlias = KEY_ALIAS,
            keyPassword = "",
            v1SigningEnabled = true,
            v2SigningEnabled = true
        )
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
            .withIntermediateDir(intermediateDir)
            .withApkCreatorType(ApkCreatorType.APK_FLINGER)
            .build().use { }
    }
}