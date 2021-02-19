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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test behavior of PackagingOptions.jniLibs.keepDebugSymbols */
class KeepDebugSymbolsTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        packagingOptions.jniLibs.keepDebugSymbols.add('**/dslDoNotStrip1.so')
                        packagingOptions {
                            jniLibs {
                                keepDebugSymbols += '**/dslDoNotStrip2.so'
                            }
                        }
                    }
                    androidComponents {
                        onVariants(selector().withName('debug')) {
                            packaging.jniLibs.keepDebugSymbols.add('**/debugDoNotStrip.so')
                        }
                        onVariants(selector().withName('release')) {
                            packaging.jniLibs.keepDebugSymbols.add('**/releaseDoNotStrip.so')
                        }
                    }
                    """.trimIndent()
            )

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(MultiModuleTestProject.builder().subproject("app", app).build())
            .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    @Test
    fun testKeepDebugSymbols() {
        // add native libs to app
        createAbiFile("x86", "strip.so")
        createAbiFile("x86", "dslDoNotStrip1.so")
        createAbiFile("x86", "dslDoNotStrip2.so")
        createAbiFile("x86", "debugDoNotStrip.so")
        createAbiFile("x86", "releaseDoNotStrip.so")

        project.executor()
            .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
            .run("app:assembleDebug", "app:assembleRelease")

        val debugApk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        TruthHelper.assertThatApk(debugApk).contains("lib/x86/strip.so")
        TruthHelper.assertThatApk(debugApk).contains("lib/x86/dslDoNotStrip1.so")
        TruthHelper.assertThatApk(debugApk).contains("lib/x86/dslDoNotStrip2.so")
        TruthHelper.assertThatApk(debugApk).contains("lib/x86/debugDoNotStrip.so")
        TruthHelper.assertThatApk(debugApk).contains("lib/x86/releaseDoNotStrip.so")
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/strip.so")
        ).isStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/dslDoNotStrip1.so")
        ).isNotStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/dslDoNotStrip2.so")
        ).isNotStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/debugDoNotStrip.so")
        ).isNotStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(debugApk, "lib/x86/releaseDoNotStrip.so")
        ).isStripped()

        val releaseApk = project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE)
        TruthHelper.assertThatApk(releaseApk).contains("lib/x86/strip.so")
        TruthHelper.assertThatApk(releaseApk).contains("lib/x86/dslDoNotStrip1.so")
        TruthHelper.assertThatApk(releaseApk).contains("lib/x86/dslDoNotStrip2.so")
        TruthHelper.assertThatApk(releaseApk).contains("lib/x86/debugDoNotStrip.so")
        TruthHelper.assertThatApk(releaseApk).contains("lib/x86/releaseDoNotStrip.so")
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/strip.so")
        ).isStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/dslDoNotStrip1.so")
        ).isNotStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/dslDoNotStrip2.so")
        ).isNotStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/debugDoNotStrip.so")
        ).isStripped()
        TruthHelper.assertThatNativeLib(
            ZipHelper.extractFile(releaseApk, "lib/x86/releaseDoNotStrip.so")
        ).isNotStripped()
    }

    private fun createAbiFile(abiName: String, libName: String) {
        val abiFolder = File(project.getSubproject("app").getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)
        KeepDebugSymbolsTest::class.java.getResourceAsStream(
            "/nativeLibs/unstripped.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
