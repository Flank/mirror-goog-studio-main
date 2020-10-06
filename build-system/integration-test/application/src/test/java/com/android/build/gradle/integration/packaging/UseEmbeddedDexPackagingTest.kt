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

package com.android.build.gradle.integration.packaging

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.sdklib.AndroidVersion.VersionCodes.O
import com.android.sdklib.AndroidVersion.VersionCodes.P
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * sourceManifestvalue and expectedMergedManifestValue refer to the value of
 * android:useEmbeddedDex in the source and merged manifests, respectively. If null, no such
 * attribute is written or expected in the manifests.
 *
 * useLegacyPackaging refers to the value of PackagingOptions.dex.useLegacyPackaging specified via
 * the DSL. If null, no such value is specified.
 */
@RunWith(FilterableParameterized::class)
class UseEmbeddedDexPackagingTest(
    private val sourceManifestValue: Boolean?,
    private val minSdk: Int,
    private val useLegacyPackaging: Boolean?,
    private val expectedMergedManifestValue: Boolean?,
    private val expectedCompression: Int
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "useEmbeddedDex_{0}_minSdk_{1}_useLegacyPackaging_{2}")
        fun parameters() = listOf(
            arrayOf(true, O, true, true, ZipEntry.STORED),
            arrayOf(true, O, false, true, ZipEntry.STORED),
            arrayOf(true, O, null, true, ZipEntry.STORED),
            arrayOf(true, P, true, true, ZipEntry.STORED),
            arrayOf(true, P, false, true, ZipEntry.STORED),
            arrayOf(true, P, null, true, ZipEntry.STORED),
            arrayOf(false, O, true, false, ZipEntry.DEFLATED),
            arrayOf(false, O, false, false, ZipEntry.STORED),
            arrayOf(false, O, null, false, ZipEntry.DEFLATED),
            arrayOf(false, P, true, false, ZipEntry.DEFLATED),
            arrayOf(false, P, false, false, ZipEntry.STORED),
            arrayOf(false, P, null, false, ZipEntry.STORED),
            arrayOf(null, O, true, null, ZipEntry.DEFLATED),
            arrayOf(null, O, false, null, ZipEntry.STORED),
            arrayOf(null, O, null, null, ZipEntry.DEFLATED),
            arrayOf(null, P, true, null, ZipEntry.DEFLATED),
            arrayOf(null, P, false, null, ZipEntry.STORED),
            arrayOf(null, P, null, null, ZipEntry.STORED)
        )
    }

    private val useEmbeddedAttribute = when (sourceManifestValue) {
        null -> ""
        false -> "android:useEmbeddedDex=\"false\""
        true -> "android:useEmbeddedDex=\"true\""
    }

    private val useLegacyPackagingString = when (useLegacyPackaging) {
        true -> "android.packagingOptions.dex.useLegacyPackaging = true"
        false -> "android.packagingOptions.dex.useLegacyPackaging = false"
        null -> ""
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.test")
                .withFile(
                    "build.gradle",
                    """
                        apply plugin: 'com.android.application'
                        android {
                            compileSdkVersion 'android-30'
                            defaultConfig {
                                minSdk = $minSdk
                            }
                        }
                        $useLegacyPackagingString
                    """.trimIndent()
                )
                .withFile(
                    "src/main/AndroidManifest.xml",
                    """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                         xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="com.test">
                    <application $useEmbeddedAttribute/>
                </manifest>
                    """.trimIndent()
                )
        )
        .create()

    @Test
    fun testDexIsPackagedCorrectly() {
        project.executor().run("assembleDebug").stdout.use {
            val resolvedUseLegacyPackaging: Boolean = useLegacyPackaging ?: (minSdk < P)
            if (resolvedUseLegacyPackaging && expectedCompression == ZipEntry.STORED) {
                assertThat(it).contains(
                    "PackagingOptions.dex.useLegacyPackaging should be set to false"
                )
            } else {
                assertThat(it).doesNotContain("PackagingOptions.dex.useLegacyPackaging")
            }
        }
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)

        // check merged manifest
        val mergedManifestContents = ApkSubject.getManifestContent(apk.file)
        when (expectedMergedManifestValue) {
            null -> {
                assertThat(
                    mergedManifestContents.none {
                        it.contains("android:useEmbeddedDex")
                    }
                ).isTrue()
            }
            else -> {
                assertThat(
                    mergedManifestContents.any {
                        // check strings separately because there are extra characters between them
                        // in this manifest.
                        it.contains("android:useEmbeddedDex")
                                && it.contains("=${expectedMergedManifestValue}")
                    }
                ).isTrue()
            }
        }

        // check compression
        ZipFile(apk.file.toFile()).use {
            val nativeLibEntry = it.getEntry("classes.dex")
            assertThat(nativeLibEntry).isNotNull()
            assertThat(nativeLibEntry.method).isEqualTo(expectedCompression)
        }
    }
}
