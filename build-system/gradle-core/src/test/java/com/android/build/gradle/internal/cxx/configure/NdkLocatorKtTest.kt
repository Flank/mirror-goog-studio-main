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
import org.junit.After
import org.junit.Test
import java.lang.RuntimeException
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class NdkLocatorKtTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val logger = RecordingLoggingEnvironment()

    @After
    fun after() {
        logger.close()
    }
    
    private fun String.toSlash() : String {
        check(contains("/"))
        return replace("/", File.separator)
    }
    private fun String.toSlashFile() = File(toSlash())

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

    @Test
    fun ndkNotConfigured() {
        findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found."""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExist() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found.
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevision() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found.
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevision() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found.
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExists() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing ${"/my/ndk/folder".toSlash()} which is version 18.1.23456")
    }

    @Test
    fun nonExistingNdkDir() {
        val path = findNdkPathImpl(
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
        assertThat(path).isEqualTo("/my/ndk/environment-folder".toSlashFile())
        assertThat(logger.errors).containsExactly("""
            Location specified by ndk.dir (/my/ndk/folder) did not contain a valid NDK and so couldn't satisfy the requested NDK version 18.1
            Considered /my/ndk/folder by ndk.dir but that location didn't exist
            Found requested NDK version 18.1 at /my/ndk/environment-folder"""
            .trimIndent().toSlash())
    }

    @Test
    fun androidHomeLocationExists() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing ${"/my/ndk/folder".toSlash()} which is version 18.1.23456")
    }

    @Test
    fun sdkFolderNdkBundleExists() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing ${"/my/sdk/folder/ndk-bundle".toSlash()} which is version 18.1.23456")
    }

    @Test
    fun ndkNotConfiguredWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing ${"/my/ndk/folder".toSlash()} from " +
                "ndk.dir which had the requested version 18.1.23456")
    }

    @Test
    fun androidHomeLocationExistsWithDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1.23456 at ${"/my/ndk/folder".toSlash()}")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1.23456 at ${"/my/sdk/folder/ndk-bundle".toSlash()}" )
    }

    @Test
    fun ndkNotConfiguredWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found."""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found.
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found.
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found.
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing ${"/my/ndk/folder".toSlash()} which is version 18.1.23456")
    }

    @Test
    fun androidHomeLocationExistsWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing ${"/my/ndk/folder".toSlash()} which is version 18.1.23456")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.infos).containsExactly("""
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            No user requested version, choosing /my/sdk/folder/ndk/18.1.23456 which is version 18.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkNotConfiguredWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing ${"/my/ndk/folder".toSlash()} from " +
                "ndk.dir which had the requested version 18.1.23456")
    }

    @Test
    fun androidHomeLocationExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1.23456 at ${"/my/ndk/folder".toSlash()}")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.infos).containsExactly("""
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            Found requested NDK version 18.1.23456 at /my/sdk/folder/ndk/18.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun multipleMatchingVersions1() {
        val path = findNdkPathImpl(
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
        assertThat(logger.infos).containsExactly("""
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            Found 2 NDK folders that matched requested version 18.1, choosing /my/sdk/folder/ndk/18.1.99999"""
            .trimIndent().toSlash())
    }

    @Test
    fun multipleMatchingVersions2() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("""
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            Found 2 NDK folders that matched requested version 18.1, choosing /my/sdk/folder/ndk/18.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithWrongDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithWrongDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())    }

    @Test
    fun ndkDirPropertyLocationExistsWithWrongDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Requested NDK version 17.1.23456" +
                " did not match the version 18.1.23456 requested by ndk.dir at ${"/my/ndk/folder".toSlash()}")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithWrongDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(logger.errors).containsExactly("""
            No version of NDK matched the requested version 17.1.23456
            Considered /my/ndk/folder by ANDROID_NDK_HOME but that NDK had version 18.1.23456 which didn't match the requested version 17.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(path).isEqualTo("/my/sdk/folder/ndk-bundle".toSlashFile())
        assertThat(logger.errors).containsExactly("""
            No version of NDK matched the requested version 17.1.23456
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that NDK had version 18.1.23456 which didn't match the requested version 17.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkNotConfiguredWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '17.1.23456'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Requested NDK version 17.1.23456 " +
                "did not match the version 18.1.23456 requested by ndk.dir at ${"/my/ndk/folder".toSlash()}")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(path).isEqualTo("/my/ndk/folder".toSlashFile())
        assertThat(logger.errors).containsExactly("""
            No version of NDK matched the requested version 17.1.23456
            Considered /my/ndk/folder by ANDROID_NDK_HOME but that NDK had version 18.1.23456 which didn't match the requested version 17.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertThat(logger.errors).containsExactly("""
            No version of NDK matched the requested version 17.1.23456
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            Considered /my/sdk/folder/ndk/18.1.23456 in SDK ndk folder but that NDK had version 18.1.23456 which didn't match the requested version 17.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun unparseableNdkVersionFromDsl() {
        val path = findNdkPathImpl(
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
        assertThat(path).isEqualTo("/my/sdk/folder/ndk/18.1.23456".toSlashFile())
        assertThat(logger.errors).containsExactly("""
            Requested NDK version '17.1.unparseable' could not be parsed
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            No user requested version, choosing /my/sdk/folder/ndk/18.1.23456 which is version 18.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkNotConfiguredWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing ${"/my/ndk/folder".toSlash()} from " +
                "ndk.dir which had the requested version 18.1")
    }

    @Test
    fun androidHomeLocationExistsWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1 at ${"/my/ndk/folder".toSlash()}")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1 at ${"/my/sdk/folder/ndk-bundle".toSlash()}" )
    }

    @Test
    fun ndkNotConfiguredWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18.1'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing ${"/my/ndk/folder".toSlash()} from " +
                "ndk.dir which had the requested version 18.1")
    }

    @Test
    fun androidHomeLocationExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1 at ${"/my/ndk/folder".toSlash()}")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.infos).containsExactly("""
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            Found requested NDK version 18.1 at /my/sdk/folder/ndk/18.1.23456"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkNotConfiguredWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithOnePartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithOnePartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing ${"/my/ndk/folder".toSlash()} from " +
                "ndk.dir which had the requested version 18")
    }

    @Test
    fun androidHomeLocationExistsWithOnePartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18 at ${"/my/ndk/folder".toSlash()}")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersion() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18 at ${"/my/sdk/folder/ndk-bundle".toSlash()}" )
    }

    @Test
    fun ndkNotConfiguredWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'"""
            .trimIndent())
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder".toSlash(),
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'
            Considered /my/ndk/folder by ndk.dir but that location didn't exist"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).containsExactly("""
            Compatible side by side NDK version was not found for android.ndkVersion '18'
            Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob"""
            .trimIndent().toSlash())
    }

    @Test
    fun ndkDirPropertyLocationExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing ${"/my/ndk/folder".toSlash()} from " +
                "ndk.dir which had the requested version 18")
    }

    @Test
    fun androidHomeLocationExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18 at ${"/my/ndk/folder".toSlash()}")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
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
        assertThat(logger.infos).containsExactly("""
            Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location didn't exist
            Found requested NDK version 18 at /my/sdk/folder/ndk/18.1.23456"""
            .trimIndent().toSlash())
    }
}