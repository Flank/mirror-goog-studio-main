/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:>>www.apache.org>licenses>LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.SDK_PKG_REVISION
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.text
import com.android.builder.sdk.InstallFailedException
import com.android.builder.sdk.LicenceNotAcceptedException
import com.android.repository.Revision
import com.android.repository.api.RemotePackage
import com.google.common.truth.Truth.assertThat
import org.gradle.api.InvalidUserDataException
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock as mock

class NdkLocatorKtTest {
    class TestLoggingEnvironment : ThreadLoggingEnvironment() {
        private val messages = mutableListOf<LoggingMessage>()
        override fun log(message: LoggingMessage) {
            messages.add(message)
        }
        fun errors() = messages.filter { it.level == LoggingLevel.ERROR }
        fun warnings() = messages.filter { it.level == LoggingLevel.WARN }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    val log = TestLoggingEnvironment()

    private fun String.toSlash(): String {
        return replace("/", File.separator)
    }

    private fun String?.toSlashFile() = if (this == null) null else File(toSlash())

    private fun findNdkPath(
        ndkVersionFromDsl: String?,
        ndkPathFromDsl: String?,
        ndkDirProperty: String?,
        sdkFolder: File?,
        ndkVersionedFolderNames: List<String>,
        getNdkSourceProperties: (File) -> SdkSourceProperties?,
        sdkHandler: SdkHandler?
    ) =
        CachingEnvironment(temporaryFolder.newFolder()).use {
            findNdkPathImpl(
                ndkVersionFromDsl,
                ndkPathFromDsl,
                ndkDirProperty,
                sdkFolder,
                ndkVersionedFolderNames,
                getNdkSourceProperties,
                sdkHandler
            )
        }

    @Test
    fun getVersionedFolderNames() {
        val versionRoot = temporaryFolder.newFolder("versionedRoot")
        val v1 = versionRoot.resolve("17.1.2")
        val v2 = versionRoot.resolve("18.1.2")
        val f1 = versionRoot.resolve("my-file")
        v1.mkdirs()
        v2.mkdirs()
        f1.writeText("touch")
        assertThat(getNdkVersionedFolders(versionRoot)).containsExactly(
            "17.1.2", "18.1.2"
        )
    }

    @Test
    fun getVersionedFolderNamesNonExistent() {
        val versionRoot = "./getVersionedFolderNamesNonExistent".toSlashFile()!!
        assertThat(getNdkVersionedFolders(versionRoot).toList()).isEmpty()
    }

    @Test
    fun getNdkVersionInfoNoFolder() {
        val versionRoot = "./non-existent-folder".toSlashFile()!!
        assertThat(getNdkVersionInfo(versionRoot)).isNull()
    }

    @Test
    fun `non-existing ndkPath without NDK version in DSL (bug 129789776)`() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkPathFromDsl = "/my/ndk/folder".toSlash(),
                ndkDirProperty = null,
                sdkFolder = null,
                ndkVersionedFolderNames = listOf(),
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/ndk/folder".toSlash() -> null
                        "/my/ndk/environment-folder".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                            )
                        )
                        else -> throw RuntimeException(path.path)
                    }
                },
                sdkHandler = null
            )
        assertThat(path).isNull()
    }

    @Test
    fun `non-existing ndkPath without NDK version in DSL and with side-by-side versions available (bug 129789776)`() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkPathFromDsl = "/my/ndk/folder".toSlash(),
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                ndkVersionedFolderNames = listOf("18.1.00000", "18.1.23456"),
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        "/my/sdk/folder/ndk/18.1.00000".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.00000"
                            )
                        )
                        else -> null
                    }
                },
                sdkHandler = null
            )
        assertThat(path).isNull()
    }

    @Test
    fun `same version in legacy folder and side-by-side folder (bug 129488603)`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.00000", "18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfigured() {
        findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkPathPropertyLocationDoesntExist() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationExists() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun `ndkPath properties has -rc2 in version and ndkVersion exists`() {
        val ndk = findNdkPath(
            ndkVersionFromDsl = "21.0.6011959-rc2",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "21.0.6011959-rc2"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )
        assertThat(ndk?.ndk?.path).isEqualTo("/my/ndk/folder".toSlash())
    }

    @Test
    fun `ndk dir properties has -rc2 in version`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "21.0.6011959-rc2",
            ndkPathFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "21.0.6011959-rc2"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun nonExistingNdkPathWithNdkVersionInDsl() {
        findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> null
                    "/my/ndk/environment-folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )
    }

    @Test
    fun sdkFolderNdkBundleExists() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
    }

    @Test
    fun `no version specified by user and only old ndk available (bug 148189425) download fails`() {
        try {
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                ndkVersionedFolderNames = listOf("18.1.23456"),
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                            mapOf(SDK_PKG_REVISION.key to "18.1.23456")
                        )
                        else -> null
                    }
                },
                sdkHandler = null
            )
        } catch (e: InvalidUserDataException) {
            assertThat(log.errors()).hasSize(0)
            assertThat(log.warnings()).hasSize(0)
            assertThat(e.message).contains("NDK not configured.")
            return
        }
        assertThat(false).named("Expected an InvalidUserDataException")
    }

    @Test
    fun `download fails with LicenceNotAcceptedException thrown`() {
        val existingNdk = "18.1.23456"
        val sdkFolder = "/my/sdk/folder"
        val sourceProperties = mapOf(
            "/my/sdk/folder/ndk/$existingNdk".toSlash() to
                    SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to existingNdk))
        )
        val sdkHandler = mock(SdkHandler::class.java)
        val pkg = mock(RemotePackage::class.java)
        doThrow(RuntimeException(
            LicenceNotAcceptedException(sdkFolder.toSlashFile()!!.toPath(), listOf(pkg))))
            .`when`(sdkHandler)
            .installNdk(Revision.parseRevision(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))

        try {
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = File(sdkFolder),
                ndkVersionedFolderNames = listOf(existingNdk),
                getNdkSourceProperties = { path -> sourceProperties[path.path] },
                sdkHandler = sdkHandler
            )!!
        } catch(e: Exception) {
            // Expect that SdkHandler#installNdk() converted LicenceNotAcceptedException to
            // RuntimeException
            assertThat(e.cause is LicenceNotAcceptedException)
                .named("${e.cause} is not of the expected type")
                .isTrue()
            return
        }
        fail("Expected exception")
    }

    @Test
    fun `download fails with InstallFailedException thrown`() {
        val existingNdk = "18.1.23456"
        val sdkFolder = "/my/sdk/folder"
        val sourceProperties = mapOf(
            "/my/sdk/folder/ndk/$existingNdk".toSlash() to
                    SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to existingNdk))
        )
        val sdkHandler = mock(SdkHandler::class.java)
        val pkg = mock(RemotePackage::class.java)
        doThrow(RuntimeException(
            InstallFailedException(sdkFolder.toSlashFile()!!.toPath(), listOf(pkg))))
            .`when`(sdkHandler)
            .installNdk(Revision.parseRevision(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))

        try {
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = File(sdkFolder),
                ndkVersionedFolderNames = listOf(existingNdk),
                getNdkSourceProperties = { path -> sourceProperties[path.path] },
                sdkHandler = sdkHandler
            )!!
        } catch(e: Exception) {
            // Expect that SdkHandler#installNdk() converted InstallFailedException to
            // RuntimeException
            assertThat(e.cause is InstallFailedException)
                .named("${e.cause} is not of the expected type")
                .isTrue()
            return
        }
        fail("Expected exception")
    }

    @Test
    fun `download fails with null returned`() {
        val existingNdk = "18.1.23456"
        val sdkFolder = "/my/sdk/folder"
        val sourceProperties = mapOf(
            "/my/sdk/folder/ndk/$existingNdk".toSlash() to
                    SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to existingNdk))
        )
        val sdkHandler = mock(SdkHandler::class.java)
        doReturn(null)
            .`when`(sdkHandler)
            .installNdk(Revision.parseRevision(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))

        try {
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = File(sdkFolder),
                ndkVersionedFolderNames = listOf(existingNdk),
                getNdkSourceProperties = { path -> sourceProperties[path.path] },
                sdkHandler = sdkHandler
            )!!
        } catch(e: InvalidUserDataException) {
            // Expect an NDK not configured exception
            assertThat(e.message).contains("NDK not configured.")
            return
        }
        fail("Expected exception")
    }

    @Test
    fun `no version specified by user and only old ndk available download succeeds`() {
        val existingNdk = "18.1.23456"
        val sourceProperties = mutableMapOf(
            "/my/sdk/folder/ndk/$existingNdk".toSlash() to
                    SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to existingNdk))
        )
        val sdkHandler = mock(SdkHandler::class.java)
        doReturn(
            "/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlashFile())
            .`when`(sdkHandler)
            .installNdk(Revision.parseRevision(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))

        val ndk =
            findNdkPath(
                ndkVersionFromDsl = null,
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = File("/my/sdk/folder"),
                ndkVersionedFolderNames = listOf(existingNdk),
                getNdkSourceProperties = { path -> sourceProperties[path.path] },
                sdkHandler = sdkHandler
            )!!
        assertThat(ndk.ndk)
            .isEqualTo("/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlashFile())
        assertThat(log.errors()).hasSize(0)
        assertThat(log.warnings()).hasSize(0)
    }

    @Test
    fun `version specified by user and only old ndk available (bug 148189425)`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.9.99999",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(SDK_PKG_REVISION.key to "18.1.23456")
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )
        assertThat(path).isEqualTo(null)
        assertThat(log.errors()).hasSize(0)
        assertThat(log.warnings()).hasSize(0)
    }

    @Test
    fun ndkNotConfiguredWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun `ndk rc configured with space-rc1 version in DSL`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456 rc1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456 rc1"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    @Test
    fun `ndk rc configured with dash-rc1 version in DSL`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456-rc1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456 rc1"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationExistsWithDslVersion() {
        val ndk = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )
        assertThat(ndk?.ndk?.path).isEqualTo("/my/ndk/folder".toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )!!.ndk
        if (log.errors().isNotEmpty()) throw Exception(log.errors()[0].text())
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
    }

    @Test
    fun ndkNotConfiguredWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationExistsWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = null,
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlash()
                    -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
                    else -> null
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlashFile())
    }

    @Test
    fun ndkNotConfiguredWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationExistsWithDslVersionWithVersionedNdk() {
        val ndk = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )
        assertThat(ndk?.ndk?.path).isEqualTo("/my/ndk/folder".toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )!!.ndk
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    @Test
    fun multipleMatchingVersions1() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456", "18.1.99999"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    "/my/sdk/folder/ndk/18.1.99999".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.99999"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun multipleMatchingVersions2() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.00000", "18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    "/my/sdk/folder/ndk/18.1.00000".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.00000"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithWrongDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersion() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = "17.1.23456",
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                ndkVersionedFolderNames = listOf(),
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        else -> null
                    }
                },
                sdkHandler = null
            )
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // Only expect a warning
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "17.1.23456",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersionWithVersionedNdk() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = "17.1.23456",
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                ndkVersionedFolderNames = listOf("18.1.23456"),
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        else -> null
                    }
                },
                sdkHandler = null
            )
        assertThat(path).isNull()
    }

    @Test
    fun unparseableNdkVersionFromDsl() {
        val path =
            findNdkPath(
                ndkVersionFromDsl = "17.1.unparseable",
                ndkPathFromDsl = null,
                ndkDirProperty = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                ndkVersionedFolderNames = listOf("18.1.23456"),
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        else -> null
                    }
                },
                sdkHandler = null
            )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(1)
        // The test SingleVariantSyncIntegrationTest#testProjectSyncIssuesAreCorrectlyReported
        // checks for this exact message. If you need to change this here then you'll also
        // have to change it there
        assertThat(log.errors().single().message)
            .isEqualTo("Requested NDK version '17.1.unparseable' could not be parsed")
    }

    @Test
    fun ndkNotConfiguredWithTwoPartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithTwoPartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18.1",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )
        assertThat(path).isEqualTo(null)
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithOnePartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithOnePartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersion() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf(),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkNotConfiguredWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun ndkPathPropertyLocationDoesntExistWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkPathFromDsl = "/my/ndk/folder".toSlash(),
            ndkDirProperty = null,
            sdkFolder = null,
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { null },
            sdkHandler = null
        )
        assertThat(path).isNull()
        assertThat(path).isNull()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPath(
            ndkVersionFromDsl = "18",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )
        assertThat(path).isNull()
    }

    @Test
    fun `from fuzz, blank ndkVersionFromDsl`() {
        val path = findNdkPath(
            ndkVersionFromDsl = "",
            ndkPathFromDsl = null,
            ndkDirProperty = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            ndkVersionedFolderNames = listOf("18.1.23456"),
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            },
            sdkHandler = null
        )
        assertThat(path).isNull()
        assertThat(log.errors()).hasSize(0) // No NDK found is supposed to be warning
    }

    @Test
    fun `fuzz test`() {
        RandomInstanceGenerator().apply {
            PassThroughDeduplicatingLoggingEnvironment().use {
                for (i in 0..10000) {
                    val veryOldVersion = "10.1.2"
                    val properVersion = "18.1.23456"
                    val properSdkPath = "/my/sdk/folder"
                    val properNdkPath = "$properSdkPath/ndk/$properVersion"
                    val properLegacyNdkPath = "$properSdkPath/ndk-bundle"
                    fun interestingString() = oneOf(
                        { nullableString() },
                        { "16" },
                        { veryOldVersion },
                        { "17.1" },
                        { "17.1.2" },
                        { properVersion },
                        { properNdkPath },
                        { "/my/sdk/folder/ndk/17.1.2" },
                        { "/my/sdk/folder" },
                        { SDK_PKG_REVISION.key })

                    fun pathToNdk() = oneOf({ properNdkPath },
                        { properLegacyNdkPath },
                        { null },
                        { interestingString() })

                    fun pathToSdk() = oneOf({ properSdkPath }, { null }, { interestingString() })
                    fun ndkVersion() = oneOf({ properVersion },
                        { veryOldVersion },
                        { null },
                        { interestingString() })

                    fun ndkVersionList() = makeListOf { ndkVersion() }.filterNotNull()
                    fun sourcePropertyVersionKey() = oneOf({ SDK_PKG_REVISION.key },
                        { SDK_PKG_REVISION.key },
                        { null },
                        { interestingString() })

                    findNdkPath(
                        ndkVersionFromDsl = ndkVersion(),
                        ndkPathFromDsl = pathToNdk(),
                        ndkDirProperty = null,
                        sdkFolder = pathToSdk().toSlashFile(),
                        ndkVersionedFolderNames = ndkVersionList(),
                        getNdkSourceProperties = { path ->
                            when (path.path) {
                                pathToNdk() -> SdkSourceProperties(
                                    mapOf(
                                        (sourcePropertyVersionKey() ?: "") to (ndkVersion() ?: "")
                                    )
                                )
                                else -> null
                            }
                        },
                        sdkHandler = null
                    )
                }
            }
        }
    }
}
