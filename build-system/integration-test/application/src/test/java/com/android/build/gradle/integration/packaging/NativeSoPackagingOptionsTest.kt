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

package com.android.build.gradle.integration.packaging

import com.android.build.api.variant.JniLibsPackaging
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.ANDROIDTEST_DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.RELEASE
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Integration tests for [JniLibsPackaging]
 */
class NativeSoPackagingOptionsTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        packagingOptions {
                            jniLibs {
                                // test various supported pattern formats
                                excludes += '**/dslExclude1.so'
                                excludes += 'lib/*/dslExclude2.so'
                                excludes += '/lib/*/dslExclude3.so'
                                pickFirsts += '**/dslPickFirst.so'
                                useLegacyPackaging = false
                            }
                        }
                    }
                    androidComponents {
                        onVariants(selector().withName('debug'), {
                            packaging.jniLibs.excludes.add('**/debugExclude.so')
                        })
                        onVariants(selector().withName('release'), {
                            packaging.jniLibs.excludes.add('**/releaseExclude.so')
                        })
                        onVariants(selector().all(), {
                            packaging.jniLibs.pickFirsts.add('**/variantPickFirst.so')
                            androidTest?.packaging?.jniLibs?.excludes?.add('**/testExclude.so')
                        })
                    }
                    """.trimIndent()
            ).withFile("src/main/jniLibs/x86/appKeep.so", "foo")
            .withFile("src/main/jniLibs/x86/dslExclude1.so", "foo")
            .withFile("src/main/jniLibs/x86/dslExclude2.so", "foo")
            .withFile("src/main/jniLibs/x86/dslExclude3.so", "foo")
            .withFile("src/main/jniLibs/x86/dslPickFirst.so", "foo")
            .withFile("src/main/jniLibs/x86_64/dslPickFirst.so", "foo")
            .withFile("src/main/jniLibs/x86/debugExclude.so", "foo")
            .withFile("src/main/jniLibs/x86/releaseExclude.so", "foo")
            .withFile("src/main/jniLibs/x86/variantPickFirst.so", "foo")
            .withFile("src/main/jniLibs/x86_64/variantPickFirst.so", "foo")
            .withFile("src/androidTest/jniLibs/x86/testKeep.so", "foo")
            .withFile("src/androidTest/jniLibs/x86/testExclude.so", "foo")

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                """
                    android {
                        packagingOptions {
                            jniLibs {
                                // test various supported pattern formats
                                excludes += '**/dslExclude1.so'
                                excludes += 'lib/*/dslExclude2.so'
                                excludes += '/lib/*/dslExclude3.so'
                            }
                        }
                    }
                    androidComponents {
                        onVariants(selector().all(), {
                            packaging.jniLibs.excludes.add('**/libExclude.so')
                            androidTest?.packaging?.jniLibs?.excludes?.add('**/testExclude.so')
                        })
                    }
                    """.trimIndent()
            ).withFile("src/main/jniLibs/x86/libKeep.so", "bar")
            .withFile("src/main/jniLibs/x86/dslExclude1.so", "bar")
            .withFile("src/main/jniLibs/x86/dslExclude2.so", "bar")
            .withFile("src/main/jniLibs/x86/dslExclude3.so", "bar")
            .withFile("src/main/jniLibs/x86/dslPickFirst.so", "bar")
            .withFile("src/main/jniLibs/x86_64/dslPickFirst.so", "bar")
            .withFile("src/main/jniLibs/x86/libExclude.so", "bar")
            .withFile("src/main/jniLibs/x86/variantPickFirst.so", "bar")
            .withFile("src/main/jniLibs/x86_64/variantPickFirst.so", "bar")
            .withFile("src/androidTest/jniLibs/x86/testKeep.so", "bar")
            .withFile("src/androidTest/jniLibs/x86/testExclude.so", "bar")

    private val multiModuleTestProject =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":app", app)
            .dependency(app, lib)
            .build()

    @get:Rule
    val project =
        GradleTestProject
            .builder()
            .fromTestApp(multiModuleTestProject)
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
            // b/149978740, b/146208910
            .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=5")
            .create()

    @Test
    fun testPackagingOptions() {
        val appSubProject = project.getSubproject(":app")
        val libSubProject = project.getSubproject(":lib")
        appSubProject.execute("assemble", "assembleDebugAndroidTest")
        libSubProject.execute("assembleDebug", "assembleDebugAndroidTest")

        val debugApkFile = appSubProject.getApk(DEBUG).file.toFile()
        assertThat(debugApkFile).exists()
        val debugApk = TruthHelper.assertThat(appSubProject.getApk(DEBUG))
        debugApk.doesNotContainJavaResource("lib/x86/dslExclude1.so")
        debugApk.doesNotContainJavaResource("lib/x86/dslExclude2.so")
        debugApk.doesNotContainJavaResource("lib/x86/dslExclude3.so")
        debugApk.doesNotContainJavaResource("lib/x86/debugExclude.so")
        debugApk.containsJavaResourceWithContent("lib/x86/appKeep.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86/dslPickFirst.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86_64/dslPickFirst.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86/releaseExclude.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86/variantPickFirst.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86_64/variantPickFirst.so", "foo")
        // check correct compression and manifest from useLegacyPackaging
        ZipFile(debugApkFile).use {
            val nativeLibEntry = it.getEntry("lib/x86/appKeep.so")
            assertThat(nativeLibEntry).isNotNull()
            assertThat(nativeLibEntry.method).isEqualTo(ZipEntry.STORED)
        }
        assertThat(
                ApkSubject.getManifestContent(debugApkFile.toPath()).any {
                    // check strings separately because there are extra characters between them
                    // in this manifest.
                    it.contains("android:extractNativeLibs") && it.contains("=false")
                }
        ).isTrue()

        val releaseApkFile = appSubProject.getApk(RELEASE).file.toFile()
        assertThat(releaseApkFile).exists()
        val releaseApk = TruthHelper.assertThat(appSubProject.getApk(RELEASE))
        releaseApk.doesNotContainJavaResource("lib/x86/dslExclude1.so")
        releaseApk.doesNotContainJavaResource("lib/x86/dslExclude2.so")
        releaseApk.doesNotContainJavaResource("lib/x86/dslExclude3.so")
        releaseApk.doesNotContainJavaResource("lib/x86/releaseExclude.so")
        releaseApk.containsJavaResourceWithContent("lib/x86/appKeep.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86/dslPickFirst.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86_64/dslPickFirst.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86/debugExclude.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86/variantPickFirst.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86_64/variantPickFirst.so", "foo")
        // check correct compression and manifest from useLegacyPackaging
        ZipFile(releaseApkFile).use {
            val nativeLibEntry = it.getEntry("lib/x86/appKeep.so")
            assertThat(nativeLibEntry).isNotNull()
            assertThat(nativeLibEntry.method).isEqualTo(ZipEntry.STORED)
        }
        assertThat(
                ApkSubject.getManifestContent(releaseApkFile.toPath()).any {
                    // check strings separately because there are extra characters between them
                    // in this manifest.
                    it.contains("android:extractNativeLibs") && it.contains("=false")
                }
        ).isTrue()

        assertThat(appSubProject.getApk(ANDROIDTEST_DEBUG).file).exists()
        val androidTestApk = TruthHelper.assertThat(appSubProject.getApk(ANDROIDTEST_DEBUG))
        androidTestApk.doesNotContainJavaResource("lib/x86/testExclude.so")
        androidTestApk.containsJavaResourceWithContent("lib/x86/testKeep.so", "foo")

        libSubProject.assertThatAar("debug") {
            this.doesNotContain("jni/x86/dslExclude1.so")
            this.doesNotContain("jni/x86/dslExclude2.so")
            this.doesNotContain("jni/x86/dslExclude3.so")
            this.doesNotContain("jni/x86/libExclude.so")
            this.contains("jni/x86/libKeep.so")
        }

        assertThat(libSubProject.getApk(ANDROIDTEST_DEBUG).file).exists()
        val libAndroidTestApk = TruthHelper.assertThat(libSubProject.getApk(ANDROIDTEST_DEBUG))
        libAndroidTestApk.doesNotContainJavaResource("lib/x86/testExclude.so")
        libAndroidTestApk.containsJavaResourceWithContent("lib/x86/testKeep.so", "bar")
    }
}
