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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.RuntimeException
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.*
import com.android.build.gradle.internal.cxx.logging.LoggingLevel
import com.android.build.gradle.internal.cxx.logging.LoggingRecord
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class NdkLocatorKtTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun String.toSlash() : String {
        check(contains("/"))
        return replace("/", File.separator)
    }
    private fun String.toSlashFile() = File(toSlash())

    private fun List<LoggingRecord>.filterByLevel(level : LoggingLevel) : List<String> {
        return filter { it.level == level }.map { it.message }
    }
    private fun List<LoggingRecord>.errors() = filterByLevel(LoggingLevel.ERROR)
    private fun List<LoggingRecord>.warnings() = filterByLevel(LoggingLevel.WARN)
    private fun List<LoggingRecord>.infos() = filterByLevel(LoggingLevel.INFO)

    private fun assertHasInsufficientPrecisionError(record : List<LoggingRecord>) {
        if (!record.any { it.level == LoggingLevel.ERROR }) {
            throw RuntimeException("Expected at least one error")
        }
        if (!record.any { it.message.contains("precision") }) {
            throw RuntimeException("Expected a precision error but got $record")
        }
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
            "17.1.2", "18.1.2")
    }

    @Test
    fun getVersionedFolderNamesNonExistent() {
        val versionRoot = "./getVersionedFolderNamesNonExistent".toSlashFile()
        assertThat(getNdkVersionedFolders(versionRoot).toList()).isEmpty()
    }

    @Test
    fun getNdkVersionInfoNoFolder() {
        val versionRoot = "./non-existent-folder".toSlashFile()
        assertThat(getNdkVersionInfo(versionRoot)).isNull()
    }

    fun `non-existing ndk dir without NDK version in DSL (bug 129789776)`() {
        val (_, record) =
            findNdkPathWithRecord(
                ndkVersionFromDsl = null,
                ndkDirProperty = "/my/ndk/folder".toSlash(),
                androidNdkHomeEnvironmentVariable = "/my/ndk/environment-folder".toSlash(),
                sdkFolder = null,
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties ={ path -> when(path.path) {
                    "/my/ndk/folder".toSlash() -> null
                    "/my/ndk/environment-folder".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
                    else -> throw RuntimeException(path.path)
                } })
         assertHasInsufficientPrecisionError(record)
    }

    fun `non-existing ndk dir without NDK version in DSL and with side-by-side versions available (bug 129789776)`() {
        val (_, record) =
                findNdkPathWithRecord(
                    ndkVersionFromDsl = null,
                    ndkDirProperty = "/my/ndk/folder".toSlash(),
                    androidNdkHomeEnvironmentVariable = null,
                    sdkFolder = "/my/sdk/folder".toSlashFile(),
                    getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456") },
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
                    })
        assertHasInsufficientPrecisionError(record)
    }

    fun `same version in legacy folder and side-by-side folder (bug 129488603)`() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456") },
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
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    @Test
    fun ndkNotConfigured() {
        val (_, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found. Default is $ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION."""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExist() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevision() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevision() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExists() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    fun nonExistingNdkDirWithNdkVersionInDsl() {
        val (_, record) = findNdkPathWithRecord(
                ndkVersionFromDsl = "18.1",
                ndkDirProperty = "/my/ndk/folder".toSlash(),
                androidNdkHomeEnvironmentVariable = "/my/ndk/environment-folder".toSlash(),
                sdkFolder = null,
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties ={ path -> when(path.path) {
                    "/my/ndk/folder".toSlash() -> null
                    "/my/ndk/environment-folder".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456"))
                    else -> throw RuntimeException(path.path)
                } })
        assertHasInsufficientPrecisionError(record)
    }

    @Test
    fun androidHomeLocationExists() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun sdkFolderNdkBundleExists() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun ndkNotConfiguredWithDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'"""
            .trimIndent())
    }

    @Test
    fun `ndk rc configured with space-rc1 version in DSL`() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456 rc1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when(path.path) {
                "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                    SDK_PKG_REVISION.key to "18.1.23456 rc1"))
                else -> null
            } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    @Test
    fun `ndk rc configured with dash-rc1 version in DSL`() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456-rc1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when(path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456 rc1"))
                    else -> null
                } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }



    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'
            """.trimIndent())
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun ndkNotConfiguredWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION)  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found. Default is $ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION."""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION)  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION)  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithVersionedNdk() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf(ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION)  },
            getNdkSourceProperties = { path -> when(path.path) {
                "/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlash()
                -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION))
            else -> null
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION".toSlashFile())
    }

    @Test
    fun ndkNotConfiguredWithDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersionWithVersionedNdk() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
    }

    fun multipleMatchingVersions1() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456", "18.1.99999") },
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
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.99999".toSlashFile())
        assertHasInsufficientPrecisionError(record)
    }

    fun multipleMatchingVersions2() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456") },
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
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithWrongDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithWrongDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())    
    }

    fun ndkDirPropertyLocationExistsWithWrongDslVersion() {
        val (_, record) =
            findNdkPathWithRecord(
                ndkVersionFromDsl = "17.1.23456",
                ndkDirProperty = "/my/ndk/folder".toSlash(),
                androidNdkHomeEnvironmentVariable = null,
                sdkFolder = null,
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties = { path ->
                    when (path.path) {
                        "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                            mapOf(
                                SDK_PKG_REVISION.key to "18.1.23456"
                            )
                        )
                        else -> throw RuntimeException(path.path)
                    }
                })

        assertHasInsufficientPrecisionError(record)
    }

    fun androidHomeLocationExistsWithWrongDslVersion() {
        val (_, record) =
                findNdkPathWithRecord(
                    ndkVersionFromDsl = "17.1.23456",
                    ndkDirProperty = null,
                    androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
                    sdkFolder = null,
                    getNdkVersionedFolderNames = { listOf() },
                    getNdkSourceProperties = { path ->
                        when (path.path) {
                            "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                                mapOf(
                                    SDK_PKG_REVISION.key to "18.1.23456"
                                )
                            )
                            else -> throw RuntimeException(path.path)
                        }
                    })

        assertHasInsufficientPrecisionError(record)
    }

    fun sdkFolderNdkBundleExistsWithWrongDslVersion() {
        val (_, record) =
                findNdkPathWithRecord(
                    ndkVersionFromDsl = "17.1.23456",
                    ndkDirProperty = null,
                    androidNdkHomeEnvironmentVariable = null,
                    sdkFolder = "/my/sdk/folder".toSlashFile(),
                    getNdkVersionedFolderNames = { listOf() },
                    getNdkSourceProperties = { path ->
                        when (path.path) {
                            "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(
                                mapOf(
                                    SDK_PKG_REVISION.key to "18.1.23456"
                                )
                            )
                            else -> throw RuntimeException(path.path)
                        }
                    })
         assertHasInsufficientPrecisionError(record)
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithWrongDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithWrongDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    fun ndkDirPropertyLocationExistsWithWrongDslVersionWithVersionedNdk() {
        val (_, record) =
                findNdkPathWithRecord(
                    ndkVersionFromDsl = "17.1.23456",
                    ndkDirProperty = "/my/ndk/folder".toSlash(),
                    androidNdkHomeEnvironmentVariable = null,
                    sdkFolder = null,
                    getNdkVersionedFolderNames = { listOf("18.1.23456") },
                    getNdkSourceProperties = { path ->
                        when (path.path) {
                            "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                                mapOf(
                                    SDK_PKG_REVISION.key to "18.1.23456"
                                )
                            )
                            else -> throw RuntimeException(path.path)
                        }
                    })
        assertHasInsufficientPrecisionError(record)
    }

    fun androidHomeLocationExistsWithWrongDslVersionWithVersionedNdk() {
        val (_, record) =
                findNdkPathWithRecord(
                    ndkVersionFromDsl = "17.1.23456",
                    ndkDirProperty = null,
                    androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
                    sdkFolder = null,
                    getNdkVersionedFolderNames = { listOf("18.1.23456") },
                    getNdkSourceProperties = { path ->
                        when (path.path) {
                            "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                                mapOf(
                                    SDK_PKG_REVISION.key to "18.1.23456"
                                )
                            )
                            else -> throw RuntimeException(path.path)
                        }
                    })
        assertHasInsufficientPrecisionError(record)
    }

    fun sdkFolderNdkBundleExistsWithWrongDslVersionWithVersionedNdk() {
        val (_, record) =
                findNdkPathWithRecord(
                    ndkVersionFromDsl = "17.1.23456",
                    ndkDirProperty = null,
                    androidNdkHomeEnvironmentVariable = null,
                    sdkFolder = "/my/sdk/folder".toSlashFile(),
                    getNdkVersionedFolderNames = { listOf("18.1.23456") },
                    getNdkSourceProperties = { path ->
                        when (path.path) {
                            "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                                mapOf(
                                    SDK_PKG_REVISION.key to "18.1.23456"
                                )
                            )
                            else -> null
                        }
                    })
         assertHasInsufficientPrecisionError(record)
    }

    fun unparseableNdkVersionFromDsl() {
        val (_, record) =
                findNdkPathWithRecord(
                    ndkVersionFromDsl = "17.1.unparseable",
                    ndkDirProperty = null,
                    androidNdkHomeEnvironmentVariable = null,
                    sdkFolder = "/my/sdk/folder".toSlashFile(),
                    getNdkVersionedFolderNames = { listOf("18.1.23456") },
                    getNdkSourceProperties = { path ->
                        when (path.path) {
                            "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                                mapOf(
                                    SDK_PKG_REVISION.key to "18.1.23456"
                                )
                            )
                            else -> null
                        }
                    })
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkNotConfiguredWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'"""
            .trimIndent())
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(SDK_PKG_REVISION.key to "bob")
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    fun androidHomeLocationExistsWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertHasInsufficientPrecisionError(record)
    }

    fun sdkFolderNdkBundleExistsWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkNotConfiguredWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'"""
            .trimIndent())
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "bob"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    fun androidHomeLocationExistsWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertHasInsufficientPrecisionError(record)
    }

    fun sdkFolderNdkBundleExistsWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> null
                }
            })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkNotConfiguredWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains(
            """
        Compatible side by side NDK version was not found for android.ndkVersion '18'"""
                .trimIndent()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "bob"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
                .trimIndent().toSlash()
        )
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    fun androidHomeLocationExistsWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertHasInsufficientPrecisionError(record)
    }

    fun sdkFolderNdkBundleExistsWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkNotConfiguredWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'"""
            .trimIndent())
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.infos()).contains(
            """
        Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
                .trimIndent().toSlash()
        )
    }

    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "bob"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isNull()
        assertHasInsufficientPrecisionError(record)
    }

    fun ndkDirPropertyLocationExistsWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertHasInsufficientPrecisionError(record)
    }

    fun androidHomeLocationExistsWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456") },
            getNdkSourceProperties = { path ->
                when (path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                        mapOf(
                            SDK_PKG_REVISION.key to "18.1.23456"
                        )
                    )
                    else -> throw RuntimeException(path.path)
                }
            })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings())
            .containsExactly("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
        assertHasInsufficientPrecisionError(record)
    }

    fun sdkFolderNdkBundleExistsWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertHasInsufficientPrecisionError(record)
    }
}