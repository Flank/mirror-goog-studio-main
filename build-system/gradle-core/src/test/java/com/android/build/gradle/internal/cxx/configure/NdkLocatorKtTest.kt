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

package com.android.build.gradle.internal.cxx.configure

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.lang.RuntimeException
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.*
import java.io.File

class NdkLocatorKtTest {
    private val logger = RecordingLoggingEnvironment()

    @After
    fun after() {
        logger.close()
    }

    @Test
    fun getVersionedFolderNames() {
        val versionRoot = File("./versionedRoot")
        val v1 = File("./versionedRoot/17.1.2")
        val v2 = File("./versionedRoot/18.1.2")
        val f1 = File("./versionedRoot/my-file")
        v1.mkdirs()
        v2.mkdirs()
        f1.writeText("touch")
        assertThat(getNdkVersionedFolders(versionRoot)).containsExactly(
            "17.1.2", "18.1.2")
    }

    @Test
    fun getVersionedFolderNamesNonExistent() {
        val versionRoot = File("./getVersionedFolderNamesNonExistent")
        assertThat(getNdkVersionedFolders(versionRoot).toList()).isEmpty()
    }

    @Test
    fun getNdkVersionInfoNoFolder() {
        val versionRoot = File("./non-existent-folder")
        assertThat(getNdkVersionInfo(versionRoot)).isNull()
    }

    @Test
    fun ndkNotConfigured() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExist() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevision() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevision() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExists() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing /my/ndk/folder which is version 18.1.23456")
    }

    @Test
    fun nonExistingNdkDir() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = "/my/ndk/environment-folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder" -> null
            "/my/ndk/environment-folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/environment-folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Location specified by ndk.dir " +
                "(/my/ndk/folder) did not contain a valid NDK and so couldn't " +
                "satisfy the requested NDK version 18.1")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by" +
                " ndk.dir but that location didn't exist",
                "Found requested NDK version 18.1 at /my/ndk/environment-folder"
            )
    }

    @Test
    fun androidHomeLocationExists() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing /my/ndk/folder which is version 18.1.23456")
    }

    @Test
    fun sdkFolderNdkBundleExists() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk-bundle"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing /my/sdk/folder/ndk-bundle which is version 18.1.23456")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing /my/ndk/folder from " +
                "ndk.dir which had the requested version 18.1.23456")
    }

    @Test
    fun androidHomeLocationExistsWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1.23456 at /my/ndk/folder")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk-bundle"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1.23456 at /my/sdk/folder/ndk-bundle")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found.")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing /my/ndk/folder which is version 18.1.23456")
    }

    @Test
    fun androidHomeLocationExistsWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("No user requested version, " +
                "choosing /my/ndk/folder which is version 18.1.23456")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = null,
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.23456"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that " +
                    "location didn't exist",
            "No user requested version, choosing /my/sdk/folder/ndk/18.1.23456 which is " +
                    "version 18.1.23456")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing /my/ndk/folder from " +
                "ndk.dir which had the requested version 18.1.23456")
    }

    @Test
    fun androidHomeLocationExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1.23456 at /my/ndk/folder")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.23456"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location " +
                    "didn't exist",
            "Found requested NDK version " +
                "18.1.23456 at /my/sdk/folder/ndk/18.1.23456")
    }

    @Test
    fun multipleMatchingVersions1() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.23456", "18.1.99999")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            "/my/sdk/folder/ndk/18.1.99999" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.99999"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.99999"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that " +
                    "location didn't exist",
            "Found 2 NDK folders that matched requested version 18.1, " +
                    "choosing /my/sdk/folder/ndk/18.1.99999")
    }

    @Test
    fun multipleMatchingVersions2() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.00000", "18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            "/my/sdk/folder/ndk/18.1.00000" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.00000"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.23456"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that " +
                    "location didn't exist",
            "Found 2 NDK folders that matched requested version 18.1, " +
                    "choosing /my/sdk/folder/ndk/18.1.23456")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 17.1.23456")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Requested NDK version 17.1.23456" +
                " did not match the version 18.1.23456 requested by ndk.dir at /my/ndk/folder")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("No version of NDK matched the " +
                "requested version 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ANDROID_NDK_HOME but that NDK had version 18.1.23456 which didn't match the " +
                "requested version 17.1.23456")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk-bundle"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("No version of NDK matched the " +
                "requested version 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/sdk/folder/ndk-bundle " +
                "in SDK ndk-bundle folder but that NDK had version 18.1.23456 which " +
                "didn't match the requested version 17.1.23456")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 17.1.23456")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version " +
                "was not found for: 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK " +
                "version was not found for: 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Requested NDK version 17.1.23456 " +
                "did not match the version 18.1.23456 requested by ndk.dir at /my/ndk/folder")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun androidHomeLocationExistsWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("No version of NDK matched the " +
                "requested version 17.1.23456")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder " +
                "by ANDROID_NDK_HOME but that NDK had version 18.1.23456 which didn't match " +
                "the requested version 17.1.23456")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithWrongDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.23456",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.23456"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("No version of NDK matched " +
                "the requested version 17.1.23456")
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location " +
                    "didn't exist",
            "Considered /my/sdk/folder/ndk/18.1.23456 in SDK ndk folder but that NDK had " +
                    "version 18.1.23456 which didn't match the requested version 17.1.23456")
    }

    @Test
    fun unparseableNdkVersionFromDsl() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "17.1.unparseable",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.23456"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Requested NDK version " +
                "'17.1.unparseable' could not be parsed")
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location " +
                    "didn't exist",
            "No user requested version, choosing /my/sdk/folder/ndk/18.1.23456 which is " +
                    "version 18.1.23456")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by" +
                " ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by ndk.dir " +
                "but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(
                mapOf(SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing /my/ndk/folder from " +
                "ndk.dir which had the requested version 18.1")
    }

    @Test
    fun androidHomeLocationExistsWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1 at /my/ndk/folder")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk-bundle"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1 at /my/sdk/folder/ndk-bundle")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties ={ path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18.1")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder " +
                "by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing /my/ndk/folder from " +
                "ndk.dir which had the requested version 18.1")
    }

    @Test
    fun androidHomeLocationExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18.1 at /my/ndk/folder")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithTwoPartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18.1",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.23456"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location " +
                    "didn't exist",
            "Found requested NDK version " +
                    "18.1 at /my/sdk/folder/ndk/18.1.23456")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder " +
                "by ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing /my/ndk/folder from " +
                "ndk.dir which had the requested version 18")
    }

    @Test
    fun androidHomeLocationExistsWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18 at /my/ndk/folder")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersion() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf() },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk-bundle" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk-bundle"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18 at /my/sdk/folder/ndk-bundle")
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
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).isEmpty()
    }

    @Test
    fun ndkDirPropertyLocationDoesntExistWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { null }
        )
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location didn't exist")
    }

    @Test
    fun ndkDirPropertyLocationExistsButNoPkgRevisionWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf())
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder by " +
                "ndk.dir but that location had source.properties with no Pkg.Revision")
    }

    @Test
    fun ndkDirPropertyLocationExistsInvalidPkgRevisionWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "bob"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isNull()
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).containsExactly("Compatible side by side NDK version was not found for: 18")
        assertThat(logger.infos).containsExactly("Considered /my/ndk/folder " +
                "by ndk.dir but that location had source.properties with invalid Pkg.Revision=bob")
    }

    @Test
    fun ndkDirPropertyLocationExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = "/my/ndk/folder",
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Choosing /my/ndk/folder from " +
                "ndk.dir which had the requested version 18")
    }

    @Test
    fun androidHomeLocationExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = "/my/ndk/folder",
            sdkFolder = null,
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/ndk/folder" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> throw RuntimeException(path.path)
        } })
        assertThat(path).isEqualTo(File("/my/ndk/folder"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly("Found requested NDK version " +
                "18 at /my/ndk/folder")
    }

    @Test
    fun sdkFolderNdkBundleExistsWithOnePartDslVersionWithVersionedNdk() {
        val path = findNdkPathImpl(
            ndkVersionFromDsl = "18",
            ndkDirProperty = null,
            androidNdkHomeEnvironmentVariable = null,
            sdkFolder = File("/my/sdk/folder"),
            getNdkVersionedFolderNames = { listOf("18.1.23456")  },
            getNdkSourceProperties = { path -> when(path.path) {
            "/my/sdk/folder/ndk/18.1.23456" -> SdkSourceProperties(mapOf(
                SDK_PKG_REVISION.key to "18.1.23456"))
            else -> null
        } })
        assertThat(path).isEqualTo(File("/my/sdk/folder/ndk/18.1.23456"))
        assertThat(logger.warnings).isEmpty()
        assertThat(logger.errors).isEmpty()
        assertThat(logger.infos).containsExactly(
            "Considered /my/sdk/folder/ndk-bundle in SDK ndk-bundle folder but that location " +
                    "didn't exist",
            "Found requested NDK version " +
                    "18 at /my/sdk/folder/ndk/18.1.23456")
    }
}