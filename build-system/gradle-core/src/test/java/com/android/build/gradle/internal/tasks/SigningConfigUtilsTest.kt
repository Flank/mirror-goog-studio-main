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

import com.android.SdkConstants
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.signing.SigningConfigVersions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/** Tests for [SigningConfigUtils]. */
class SigningConfigUtilsTest {
    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    lateinit var outputFile : File
    lateinit var storeFile : File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        outputFile = temporaryFolder.newFile()
        storeFile = temporaryFolder.newFile()
    }

    @Test
    @Throws(IOException::class)
    fun testSigningConfigDataSaveAndLoad() {
        val signingConfigData = SigningConfigData(
            name = "sampleName",
            storeType = "sampleStoreType",
            storeFile = storeFile,
            storePassword = "sampleStorePassword",
            keyAlias = "sampleKeyAlias",
            keyPassword = "sampleKeyPassword"
        )
        SigningConfigUtils.saveSigningConfigData(outputFile, signingConfigData)

        val config = SigningConfigUtils.loadSigningConfigData(outputFile)
        assertThat(config).isEqualTo(signingConfigData)
    }

    @Test
    @Throws(IOException::class)
    fun testSigningConfigVersionsSaveAndLoad() {
        val signingConfigVersions = SigningConfigVersions(
                enableV1Signing = false,
                enableV2Signing = true,
                enableV3Signing = false,
                enableV4Signing = true
        )
        SigningConfigUtils.saveSigningConfigVersions(outputFile, signingConfigVersions)

        val loadedSigningConfigVersions = SigningConfigUtils.loadSigningConfigVersions(outputFile)
        assertThat(loadedSigningConfigVersions).isEqualTo(signingConfigVersions)
    }

    @Test
    @Throws(IOException::class)
    fun testSigningConfigDataFileIsReadWriteByOwnerOnly() {
        val signingConfig = SigningConfigData(
            name = "sampleName",
            storeType = "sampleStoreType",
            storeFile = storeFile,
            storePassword = "sampleStorePassword",
            keyAlias = "sampleKeyAlias",
            keyPassword = "sampleKeyPassword"
        )
        SigningConfigUtils.saveSigningConfigData(outputFile, signingConfig)

        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
            val perms = Files.getPosixFilePermissions(outputFile.toPath())
            assertThat(perms).containsExactly(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE)
        } else {
            // Windows special handling, check that we can read and write.
            val view =
                Files.getFileAttributeView(outputFile.toPath(), AclFileAttributeView::class.java)
            val expectedEntry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(view.owner)
                .setPermissions(
                    AclEntryPermission.READ_ACL,
                    AclEntryPermission.READ_NAMED_ATTRS,
                    AclEntryPermission.READ_DATA,
                    AclEntryPermission.READ_ATTRIBUTES,
                    AclEntryPermission.WRITE_ACL,
                    AclEntryPermission.WRITE_DATA,
                    AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.WRITE_NAMED_ATTRS,
                    AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.WRITE_OWNER,
                    AclEntryPermission.SYNCHRONIZE,
                    AclEntryPermission.DELETE)
                .build()
            assertThat(view.acl).containsExactly(expectedEntry)
        }
    }
}
