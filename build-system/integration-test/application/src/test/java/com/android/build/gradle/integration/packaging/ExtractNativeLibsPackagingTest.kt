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
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * sourceManifestvalue and expectedMergedManifestValue refer to the value of
 * android:extractNativeLibs in the source and merged manifests, respectively. If null, no such
 * attribute is written or expected in the manifests.
 */
@RunWith(FilterableParameterized::class)
class ExtractNativeLibsPackagingTest(
    sourceManifestValue: Boolean?,
    minSdk: Int,
    private val expectedMergedManifestValue: Boolean?,
    private val expectedCompression: Int
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "extractNativeLibs_{0}_minSdk_{1}")
        fun parameters() = listOf(
            arrayOf(true, 22, true, ZipEntry.DEFLATED),
            arrayOf(true, 23, true, ZipEntry.DEFLATED),
            arrayOf(false, 22, false, ZipEntry.STORED),
            arrayOf(false, 23, false, ZipEntry.STORED),
            arrayOf(null, 22, null, ZipEntry.DEFLATED),
            arrayOf(null, 23, false, ZipEntry.STORED)
        )
    }

    private val extractNativeLibsAttribute = when (sourceManifestValue) {
        true -> "android:extractNativeLibs=\"true\""
        false -> "android:extractNativeLibs=\"false\""
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
                            compileSdk = 29
                            defaultConfig {
                                minSdk = $minSdk
                            }
                        }
                        """.trimIndent()
                )
                .withFile(
                    "src/main/AndroidManifest.xml",
                    """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            xmlns:dist="http://schemas.android.com/apk/distribution"
                            package="com.test">
                            <application $extractNativeLibsAttribute/>
                        </manifest>
                        """.trimIndent()
                )
                .withFile("src/main/jniLibs/x86/dummy.so", "foo".repeat(100))
        )
        .create()

    @Test
    fun testNativeLibPackagedCorrectly() {
        project.executor().run("assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)

        // check merged manifest
        val mergedManifestContents = ApkSubject.getManifestContent(apk.file)
        when (expectedMergedManifestValue) {
            null -> {
                assertThat(
                    mergedManifestContents.none {
                        it.contains("android:extractNativeLibs")
                    }
                ).isTrue()
            }
            else -> {
                assertThat(
                    mergedManifestContents.any {
                        // check strings separately because there are extra characters between them
                        // in this manifest.
                        it.contains("android:extractNativeLibs")
                                && it.contains("=${expectedMergedManifestValue}")
                    }
                ).isTrue()
            }
        }

        // check compression
        ZipFile(apk.file.toFile()).use {
            val nativeLibEntry = it.getEntry("lib/x86/dummy.so")
            assertThat(nativeLibEntry).isNotNull()
            assertThat(nativeLibEntry.method).isEqualTo(expectedCompression)
        }
    }
}