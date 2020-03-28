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

package com.android.builder.internal.aapt.v2

import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AaptV2CommandBuilderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun checkMergeOnly() {
        // Given
        val manifestFile = temporaryFolder.newFile("AndroidManifest.xml")
        val resourceDir = temporaryFolder.newFolder("compiled_res")

        val compiledResource = resourceDir.resolve("compiled_resource").also { it.writeText("") }

        val intermediateDir = temporaryFolder.newFolder("intermediates")
        val staticLibApk = temporaryFolder.newFolder().resolve("static_lib.apk")

        val request = AaptPackageConfig(
            androidJarPath = null,
            manifestFile = manifestFile,
            options = AaptOptions(),
            resourceDirs = ImmutableList.of(resourceDir),
            staticLibrary = true,
            resourceOutputApk = staticLibApk,
            variantType = VariantTypeImpl.LIBRARY,
            mergeOnly = true,
            intermediateDir = intermediateDir
        )

        // When
        val command = makeLinkCommand(request)

        // Then
        assertThat(command).containsExactly(
            "--merge-only",
            "--manifest",
            manifestFile.absolutePath,
            "-o",
            staticLibApk.absolutePath,
            "-R",
            compiledResource.absolutePath,
            "--auto-add-overlay",
            "--non-final-ids",
            "-0",
            "apk",
            "--no-version-vectors",
            "--static-lib",
            "--no-proguard-location-reference"
        ).inOrder()
    }

    @Test
    fun checkNoCompressRegex() {
        val manifestFile = temporaryFolder.newFile("AndroidManifest.xml")

        val intermediateDir = temporaryFolder.newFolder("intermediates")
        val staticLibApk = temporaryFolder.newFolder().resolve("static_lib.apk")
        val androidJar = temporaryFolder.newFolder().resolve("android.jar")
        val noCompress = listOf(".test?", ".ęńd", "Android/res/raw/hello.world")

        val request = AaptPackageConfig(
            androidJarPath = androidJar.absolutePath,
            manifestFile = manifestFile,
            options = AaptOptions(noCompress, additionalParameters = null),
            staticLibrary = true,
            resourceOutputApk = staticLibApk,
            variantType = VariantTypeImpl.LIBRARY,
            intermediateDir = intermediateDir
        )

        // When
        val command = makeLinkCommand(request)

        val dotTestExtensionRegex = "(\\.(t|T)(e|E)(s|S)(t|T)\\?\$)"
        val dotEndRegex = "(\\.(ę|Ę)(ń|Ń)(d|D)\$)"
        val fullPathRegex = "((a|A)(n|N)(d|D)(r|R)(o|O)(i|I)(d|D)/(r|R)(e|E)(s|S)/" +
                "(r|R)(a|A)(w|W)/(h|H)(e|E)(l|L)(l|L)(o|O)\\.(w|W)(o|O)(r|R)(l|L)(d|D)\$)"

        // Then
        assertThat(command).containsExactly(
            "-I",
            androidJar.absolutePath,
            "--manifest",
            manifestFile.absolutePath,
            "-o",
            staticLibApk.absolutePath,
            "--auto-add-overlay",
            "--non-final-ids",
            "-0",
            "apk",
            "--no-compress-regex",
            "($fullPathRegex|$dotTestExtensionRegex|$dotEndRegex)",
            "--no-version-vectors",
            "--static-lib",
            "--no-proguard-location-reference"
        ).inOrder()
    }

    @Test
    fun checkNoCompressEmpty() {
        val manifestFile = temporaryFolder.newFile("AndroidManifest.xml")

        val intermediateDir = temporaryFolder.newFolder("intermediates")
        val staticLibApk = temporaryFolder.newFolder().resolve("static_lib.apk")
        val androidJar = temporaryFolder.newFolder().resolve("android.jar")
        val noCompress = listOf("")

        val request = AaptPackageConfig(
            androidJarPath = androidJar.absolutePath,
            manifestFile = manifestFile,
            options = AaptOptions(noCompress, additionalParameters = null),
            staticLibrary = true,
            resourceOutputApk = staticLibApk,
            variantType = VariantTypeImpl.LIBRARY,
            intermediateDir = intermediateDir
        )

        // When
        val command = makeLinkCommand(request)

        // Then
        assertThat(command).containsExactly(
            "-I",
            androidJar.absolutePath,
            "--manifest",
            manifestFile.absolutePath,
            "-o",
            staticLibApk.absolutePath,
            "--auto-add-overlay",
            "--non-final-ids",
            "-0",
            "apk",
            "--no-compress",
            "--no-version-vectors",
            "--static-lib",
            "--no-proguard-location-reference"
        ).inOrder()
    }
}