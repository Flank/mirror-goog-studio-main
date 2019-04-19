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
import org.gradle.api.GradleException
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

    @Test(expected = GradleException::class)
    fun `non-existing ndk dir without NDK version in DSL (bug 129789776)`() {
        try {
            findNdkPathWithRecord(
                ndkVersionFromDsl = null,
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
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo("""
            Location specified by ndk.dir (/my/ndk/folder) did not contain a valid NDK and and couldn't be used
            """.trimIndent().toSlash())
            throw e
        }
    }

    @Test(expected = GradleException::class)
    fun `non-existing ndk dir without NDK version in DSL and with side-by-side versions available (bug 129789776)`() {
        try {
            findNdkPathWithRecord(
                ndkVersionFromDsl = null,
                ndkDirProperty = "/my/ndk/folder".toSlash(),
                androidNdkHomeEnvironmentVariable = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456")  },
                getNdkSourceProperties = { path -> when(path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456"))
                    "/my/sdk/folder/ndk/18.1.00000".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.00000"))
                    else -> null
                } })
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo("""
            Location specified by ndk.dir (/my/ndk/folder) did not contain a valid NDK and and couldn't be used
            """.trimIndent().toSlash())
            throw e
        }
    }

    @Test
    fun `same version in legacy folder and side-by-side folder (bug 129488603)`() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
                "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                    SDK_PKG_REVISION.key to "18.1.23456"))
                "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(mapOf(
                    SDK_PKG_REVISION.key to "18.1.23456"))
                else -> null
            } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
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
            Compatible side by side NDK version was not found."""
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
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test(expected = GradleException::class)
    fun nonExistingNdkDirWithNdkVersionInDsl() {
        try {
            findNdkPathWithRecord(
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
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo("""
            Location specified by ndk.dir (/my/ndk/folder) did not contain a valid NDK and so couldn't satisfy the requested NDK version 18.1
            """.trimIndent().toSlash())
            throw e
        }
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
                SDK_PKG_REVISION.key to "18.1.23456"))
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
                SDK_PKG_REVISION.key to "18.1.23456"))
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
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found."""
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
    fun androidHomeLocationExistsWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
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
    fun sdkFolderNdkBundleExistsWithVersionedNdk() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = null,
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

    @Test
    fun multipleMatchingVersions1() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.23456", "18.1.99999")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            "/my/sdk/folder/ndk/18.1.99999".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.99999"))
            else -> null
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.99999".toSlashFile())
    }

    @Test
    fun multipleMatchingVersions2() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = "/my/sdk/folder".toSlashFile(),
            getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            "/my/sdk/folder/ndk/18.1.00000".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.00000"))
            else -> null
        } })
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
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

    @Test(expected = GradleException::class)
    fun ndkDirPropertyLocationExistsWithWrongDslVersion() {
        try {
            findNdkPathWithRecord(
                ndkVersionFromDsl = "17.1.23456",
                ndkDirProperty = "/my/ndk/folder".toSlash(),
                androidNdkHomeEnvironmentVariable = null,
                sdkFolder = null,
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties = { path -> when(path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456"))
                    else -> throw RuntimeException(path.path)
                } })
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo(
                "Requested NDK version 17.1.23456" +
                        " did not match the version 18.1.23456 requested by ndk.dir at ${"/my/ndk/folder".toSlash()}")
            throw e
        }
    }

    @Test(expected = GradleException::class)
    fun androidHomeLocationExistsWithWrongDslVersion() {
        try {
            findNdkPathWithRecord(
                ndkVersionFromDsl = "17.1.23456",
                ndkDirProperty = null,
                androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
                sdkFolder = null,
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties = { path -> when(path.path) {
                    "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456"))
                    else -> throw RuntimeException(path.path)
                } })
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo("""
            No version of NDK matched the requested version 17.1.23456. Versions available locally: 18.1.23456
            """.trimIndent())
            throw e
        }
    }

    @Test(expected = GradleException::class)
    fun sdkFolderNdkBundleExistsWithWrongDslVersion() {
        try {
            findNdkPathWithRecord(
                ndkVersionFromDsl = "17.1.23456",
                ndkDirProperty = null,
                androidNdkHomeEnvironmentVariable = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf() },
                getNdkSourceProperties = { path -> when(path.path) {
                    "/my/sdk/folder/ndk-bundle".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456"))
                    else -> throw RuntimeException(path.path)
                } })
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo("""
            No version of NDK matched the requested version 17.1.23456. Versions available locally: 18.1.23456
            """.trimIndent())
            throw e
        }
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

    @Test(expected = GradleException::class)
    fun ndkDirPropertyLocationExistsWithWrongDslVersionWithVersionedNdk() {
        try {
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
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo(
                "Requested NDK version 17.1.23456 " +
                        "did not match the version 18.1.23456 requested by ndk.dir at ${"/my/ndk/folder".toSlash()}")
            throw e
        }
    }

    @Test(expected = GradleException::class)
    fun androidHomeLocationExistsWithWrongDslVersionWithVersionedNdk() {
       try {
           findNdkPathWithRecord(
               ndkVersionFromDsl = "17.1.23456",
               ndkDirProperty = null,
               androidNdkHomeEnvironmentVariable = "/my/ndk/folder".toSlash(),
               sdkFolder = null,
               getNdkVersionedFolderNames = { listOf("18.1.23456")  },
               getNdkSourceProperties = { path -> when(path.path) {
                   "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                       SDK_PKG_REVISION.key to "18.1.23456"))
                   else -> throw RuntimeException(path.path)
               } })
       } catch (e : GradleException) {
           assertThat(e.message).isEqualTo("""
            No version of NDK matched the requested version 17.1.23456. Versions available locally: 18.1.23456
            """.trimIndent())
           throw e
       }
    }

    @Test(expected = GradleException::class)
    fun sdkFolderNdkBundleExistsWithWrongDslVersionWithVersionedNdk() {
        try {
            findNdkPathWithRecord(
                ndkVersionFromDsl = "17.1.23456",
                ndkDirProperty = null,
                androidNdkHomeEnvironmentVariable = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf("18.1.23456")  },
                getNdkSourceProperties = { path -> when(path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456"))
                    else -> null
                } })
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo("""
            No version of NDK matched the requested version 17.1.23456. Versions available locally: 18.1.23456
            """.trimIndent())
            throw e
        }
    }

    @Test(expected = GradleException::class)
    fun unparseableNdkVersionFromDsl() {
        try {
            findNdkPathWithRecord(
                ndkVersionFromDsl = "17.1.unparseable",
                ndkDirProperty = null,
                androidNdkHomeEnvironmentVariable = null,
                sdkFolder = "/my/sdk/folder".toSlashFile(),
                getNdkVersionedFolderNames = { listOf("18.1.23456")  },
                getNdkSourceProperties = { path -> when(path.path) {
                    "/my/sdk/folder/ndk/18.1.23456".toSlash() -> SdkSourceProperties(mapOf(
                        SDK_PKG_REVISION.key to "18.1.23456"))
                    else -> null
                } })
        } catch (e : GradleException) {
            assertThat(e.message).isEqualTo("""
            Requested NDK version '17.1.unparseable' could not be parsed"""
                .trimIndent())
            throw e
        }
    }

    @Test
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
    }

    @Test
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
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
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
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(
                mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithTwoPartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test
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
        assertThat(record.errors()).isEmpty()
    }

    @Test
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
        assertThat(record.errors()).isEmpty()
    }

    @Test
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
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
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
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
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
        assertThat(record.errors()).isEmpty()
    }

    @Test
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
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersionWithVersionedNdk() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18.1",
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

    @Test
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
        assertThat(record.warnings()).contains("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'"""
            .trimIndent())
    }

    @Test
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
        assertThat(record.infos()).contains("""
            Rejected /my/ndk/folder by ndk.dir because that location has no source.properties"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
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
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
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
    fun ndkDirPropertyLocationExistsWithOnePartDslVersion() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder".toSlash() -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(record.warnings()).isEmpty()
        assertThat(record.errors()).isEmpty()
    }

    @Test
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
        assertThat(record.errors()).isEmpty()
    }

    @Test
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
        assertThat(record.errors()).isEmpty()
    }

    @Test
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
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
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
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersionWithVersionedNdk() {
        val (path, _) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
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
    }

    @Test
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
        assertThat(record.errors()).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithOnePartDslVersionWithVersionedNdk() {
        val (path, record) = findNdkPathWithRecord(
            ndkVersionFromDsl = "18",
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
    fun sdkFolderNdkBundleExistsWithOnePartDslVersionWithVersionedNdk() {
        val (path, _) = findNdkPathWithRecord(
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
    }
}