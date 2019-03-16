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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.logging.RecordingLoggingEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.Rule

import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NdkSymlinkerKtTest {
    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    @Test
    fun keepOriginalNdkIfNdkDoesntExist() {
        val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
        val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
        val ndkSymlinkDirInLocalProp = tmpFolder.root.toPath().resolve("symlinkto").toFile()
        val result = trySymlinkNdk(
            originalNdk,
            cxxVariantFolder,
            ndkSymlinkDirInLocalProp
        )
        assertThat(result).isEqualTo(originalNdk)
    }

    @Test
    fun keepOriginalNdkIfSourcePropertiesDoesntExist() {
        val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
        originalNdk.mkdirs()
        val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
        val ndkSymlinkDirInLocalProp = tmpFolder.root.toPath().resolve("symlinkto").toFile()
        val result = trySymlinkNdk(
            originalNdk,
            cxxVariantFolder,
            ndkSymlinkDirInLocalProp
        )
        assertThat(result).isEqualTo(originalNdk)
    }

    @Test
    fun keepOriginalNdkIfSourcePropertiesVersionDoesntExist() {
        val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
        originalNdk.mkdirs()
        val sourceProperties = originalNdk.toPath().resolve("source.properties").toFile()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
        """.trimIndent())
        val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
        val ndkSymlinkDirInLocalProp = tmpFolder.root.toPath().resolve("symlinkto").toFile()
        val result = trySymlinkNdk(
            originalNdk,
            cxxVariantFolder,
            ndkSymlinkDirInLocalProp
        )
        assertThat(result).isEqualTo(originalNdk)
    }

    @Test
    fun working() {
        val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
        originalNdk.mkdirs()
        val sourceProperties = originalNdk.toPath().resolve("source.properties").toFile()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
        val ndkSymlinkDirInLocalProp = tmpFolder.root.toPath().resolve("symlinkto").toFile()
        val result = trySymlinkNdk(
            originalNdk,
            cxxVariantFolder,
            ndkSymlinkDirInLocalProp
        )
        assertThat(result.toPath()).isEqualTo(ndkSymlinkDirInLocalProp.toPath().resolve("ndk/17.2.4988734"))
    }

    @Test
    fun workingRelative() {
        val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
        originalNdk.mkdirs()
        val sourceProperties = originalNdk.toPath().resolve("source.properties").toFile()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
        val ndkSymlinkDirInLocalProp = File("my-ndk")
        val result = trySymlinkNdk(
            originalNdk,
            cxxVariantFolder,
            ndkSymlinkDirInLocalProp
        )
        assertThat(result.toPath()).isEqualTo(cxxVariantFolder.toPath().resolve("my-ndk/ndk/17.2.4988734"))
    }

    @Test
    fun workingAndIdempotent() {
        RecordingLoggingEnvironment().use { log ->
            val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
            originalNdk.mkdirs()
            val sourceProperties = originalNdk.toPath().resolve("source.properties").toFile()
            sourceProperties.writeText(
                """
                Pkg.Desc = Android NDK
                Pkg.Revision = 17.2.4988734
                """.trimIndent()
            )
            val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
            val ndkSymlinkDirInLocalProp = tmpFolder.root.toPath().resolve("symlinkto").toFile()
            val result = trySymlinkNdk(
                originalNdk,
                cxxVariantFolder,
                ndkSymlinkDirInLocalProp
            )
            val result2 = trySymlinkNdk(
                originalNdk,
                cxxVariantFolder,
                ndkSymlinkDirInLocalProp
            )
            val message = "${log.errors}\n${log.warnings}\n${log.infos}"
            assertThat(result.path)
                .named(message)
                .endsWith("17.2.4988734")
            assertThat(result2.path)
                .named(message)
                .endsWith("17.2.4988734")
            assertThat(result.toPath())
                .named(message)
                .isEqualTo(result2.toPath())
        }
    }

    @Test
    fun dontSymlinkPathWithDollar() {
        val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
        originalNdk.mkdirs()
        val sourceProperties = originalNdk.toPath().resolve("source.properties").toFile()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
        val ndkSymlinkDirInLocalProp = tmpFolder.root.toPath().resolve("symlinkto$").toFile()
        val result = trySymlinkNdk(
            originalNdk,
            cxxVariantFolder,
            ndkSymlinkDirInLocalProp
        )
        assertThat(result).isEqualTo(originalNdk)
    }

    @Test
    fun noPathSpecifiedByUser() {
        val originalNdk = tmpFolder.root.toPath().resolve("ndk").toFile()
        originalNdk.mkdirs()
        val sourceProperties = originalNdk.toPath().resolve("source.properties").toFile()
        sourceProperties.writeText("""
            Pkg.Desc = Android NDK
            Pkg.Revision = 17.2.4988734
        """.trimIndent())
        val cxxVariantFolder = tmpFolder.root.toPath().resolve("cxx/debug").toFile()
        val ndkSymlinkDirInLocalProp : File? = null
        val result = trySymlinkNdk(
            originalNdk,
            cxxVariantFolder,
            ndkSymlinkDirInLocalProp
        )
        assertThat(result).isEqualTo(originalNdk)
    }
}