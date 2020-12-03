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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
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
 *
 * useLegacyPackaging refers to the value of PackagingOptions.jniLibs.useLegacyPackaging specified
 * via the DSL. If null, no such value is specified.
 */
@RunWith(FilterableParameterized::class)
class ExtractNativeLibsPackagingTest(
    private val sourceManifestValue: Boolean?,
    private val minSdk: Int,
    private val useLegacyPackaging: Boolean?,
    private val expectedMergedManifestValue: Boolean?,
    private val expectedCompression: Int
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "extractNativeLibs_{0}_minSdk_{1}_useLegacyPackaging_{2}")
        fun parameters() = listOf(
            arrayOf(true, 22, true, true, ZipEntry.DEFLATED),
            arrayOf(true, 22, false, true, ZipEntry.DEFLATED),
            arrayOf(true, 22, null, true, ZipEntry.DEFLATED),
            arrayOf(true, 23, true, true, ZipEntry.DEFLATED),
            arrayOf(true, 23, false, true, ZipEntry.DEFLATED),
            arrayOf(true, 23, null, true, ZipEntry.DEFLATED),
            arrayOf(false, 22, true, false, ZipEntry.STORED),
            arrayOf(false, 22, false, false, ZipEntry.STORED),
            arrayOf(false, 22, null, false, ZipEntry.STORED),
            arrayOf(false, 23, true, false, ZipEntry.STORED),
            arrayOf(false, 23, false, false, ZipEntry.STORED),
            arrayOf(false, 23, null, false, ZipEntry.STORED),
            arrayOf(null, 22, true, null, ZipEntry.DEFLATED),
            arrayOf(null, 22, false, false, ZipEntry.STORED),
            arrayOf(null, 22, null, null, ZipEntry.DEFLATED),
            arrayOf(null, 23, true, null, ZipEntry.DEFLATED),
            arrayOf(null, 23, false, false, ZipEntry.STORED),
            arrayOf(null, 23, null, false, ZipEntry.STORED)
        )
    }

    private val extractNativeLibsAttribute = when (sourceManifestValue) {
        true -> "android:extractNativeLibs=\"true\""
        false -> "android:extractNativeLibs=\"false\""
        null -> ""
    }

    private val useLegacyPackagingString = when (useLegacyPackaging) {
        true -> "android.packagingOptions.jniLibs.useLegacyPackaging = true"
        false -> "android.packagingOptions.jniLibs.useLegacyPackaging = false"
        null -> ""
    }

    @get:Rule
    val app = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.test")
                .withFile(
                    "build.gradle",
                    """
                        apply plugin: 'com.android.application'
                        android {
                            compileSdk = 30
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
                            package="com.example">
                            <application $extractNativeLibsAttribute/>
                        </manifest>
                        """.trimIndent()
                )
                .withFile(
                    "src/androidTest/AndroidManifest.xml",
                    """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            xmlns:dist="http://schemas.android.com/apk/distribution"
                            package="com.example">
                            <application $extractNativeLibsAttribute/>
                        </manifest>
                        """.trimIndent()
                )
                .withFile("src/main/jniLibs/x86/fake.so", "foo".repeat(100))
                .withFile("src/androidTest/jniLibs/x86/fake.so", "foo".repeat(100))
        )
        .create()

    @get:Rule
    val testModule = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.test("com.test")
                .withFile(
                    "build.gradle",
                    """
                        apply plugin: 'com.android.test'
                        android {
                            compileSdk = 30
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
                            package="com.example">
                            <application $extractNativeLibsAttribute/>
                        </manifest>
                        """.trimIndent()
                )
                .withFile("src/main/jniLibs/x86/fake.so", "foo".repeat(100))
        )
        .create()

    @Test
    fun testNativeLibPackagedCorrectly_app() {
        checkNativeLibPackagedCorrectly(app, "assembleDebug", ApkType.DEBUG)
    }

    @Test
    fun testNativeLibPackagedCorrectly_androidTest() {
        checkNativeLibPackagedCorrectly(app, "assembleAndroidTest", ApkType.ANDROIDTEST_DEBUG)
    }

    @Test
    fun testNativeLibPackagedCorrectly_testModule() {
        checkNativeLibPackagedCorrectly(testModule, "assembleDebug", ApkType.DEBUG)
    }

    private fun checkNativeLibPackagedCorrectly(
        project: GradleTestProject,
        task: String,
        apkType: ApkType
    ) {
        project.executor().run(task).stdout.use {
            val resolvedUseLegacyPackaging: Boolean = useLegacyPackaging ?: (minSdk < 23)
            when {
                resolvedUseLegacyPackaging && expectedCompression == ZipEntry.STORED -> {
                    assertThat(it).contains(
                        "PackagingOptions.jniLibs.useLegacyPackaging should be set to false"
                    )
                }
                !resolvedUseLegacyPackaging && expectedCompression == ZipEntry.DEFLATED -> {
                    assertThat(it).contains(
                        "PackagingOptions.jniLibs.useLegacyPackaging should be set to true"
                    )
                }
                else -> assertThat(it).doesNotContain("PackagingOptions.jniLibs.useLegacyPackaging")
            }
        }
        val apk = project.getApk(apkType)

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
            val nativeLibEntry = it.getEntry("lib/x86/fake.so")
            assertThat(nativeLibEntry).isNotNull()
            assertThat(nativeLibEntry.method).isEqualTo(expectedCompression)
        }
    }
}
