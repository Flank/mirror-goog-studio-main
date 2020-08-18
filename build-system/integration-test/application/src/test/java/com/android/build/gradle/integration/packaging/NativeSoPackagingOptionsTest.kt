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

import com.android.build.api.variant.JniLibsPackagingOptions
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.ANDROIDTEST_DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.RELEASE
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for [JniLibsPackagingOptions]
 */
class NativeSoPackagingOptionsTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        packagingOptions {
                            jniLibs {
                                excludes += '**/dslExclude.so'
                                pickFirsts += '**/dslPickFirst.so'
                            }
                        }
                        onVariantProperties.withName('debug') {
                            packagingOptions.jniLibs.excludes.add('**/debugExclude.so')
                        }
                        onVariantProperties.withName('release') {
                            packagingOptions.jniLibs.excludes.add('**/releaseExclude.so')
                        }
                        onVariantProperties {
                            packagingOptions.jniLibs.pickFirsts.add('**/variantPickFirst.so')
                        }
                        onVariants {
                            androidTestProperties {
                                packagingOptions.jniLibs.excludes.add('**/testExclude.so')
                            }
                        }
                    }
                    """.trimIndent()
            ).withFile("src/main/jniLibs/x86/appKeep.so", "foo")
            .withFile("src/main/jniLibs/x86/dslExclude.so", "foo")
            .withFile("src/main/jniLibs/x86/dslPickFirst.so", "foo")
            .withFile("src/main/jniLibs/x86/debugExclude.so", "foo")
            .withFile("src/main/jniLibs/x86/releaseExclude.so", "foo")
            .withFile("src/main/jniLibs/x86/variantPickFirst.so", "foo")
            .withFile("src/androidTest/jniLibs/x86/testKeep.so", "foo")
            .withFile("src/androidTest/jniLibs/x86/testExclude.so", "foo")

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                """
                    android {
                        packagingOptions {
                            jniLibs {
                                excludes += '**/dslExclude.so'
                            }
                        }
                        onVariantProperties {
                            packagingOptions.jniLibs.excludes.add('**/libExclude.so')
                        }
                        onVariants {
                            androidTestProperties {
                                packagingOptions.jniLibs.excludes.add('**/testExclude.so')
                            }
                        }
                    }
                    """.trimIndent()
            ).withFile("src/main/jniLibs/x86/libKeep.so", "bar")
            .withFile("src/main/jniLibs/x86/dslExclude.so", "bar")
            .withFile("src/main/jniLibs/x86/dslPickFirst.so", "bar")
            .withFile("src/main/jniLibs/x86/libExclude.so", "bar")
            .withFile("src/main/jniLibs/x86/variantPickFirst.so", "bar")
            .withFile("src/androidTest/jniLibs/x86/testKeep.so", "bar")
            .withFile("src/androidTest/jniLibs/x86/testExclude.so", "bar")

    private val multiModuleTestProject =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":app", app)
            .dependency(app, lib)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(multiModuleTestProject).create()

    @Test
    fun testPackagingOptions() {
        val appSubProject = project.getSubproject(":app")
        val libSubProject = project.getSubproject(":lib")
        appSubProject.execute("assemble", "assembleDebugAndroidTest")
        libSubProject.execute("assembleDebug", "assembleDebugAndroidTest")

        FileSubject.assertThat(appSubProject.getApk(DEBUG).file.toFile()).exists()
        val debugApk = TruthHelper.assertThat(appSubProject.getApk(DEBUG))
        debugApk.doesNotContainJavaResource("lib/x86/dslExclude.so")
        debugApk.doesNotContainJavaResource("lib/x86/debugExclude.so")
        debugApk.containsJavaResourceWithContent("lib/x86/appKeep.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86/dslPickFirst.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86/releaseExclude.so", "foo")
        debugApk.containsJavaResourceWithContent("lib/x86/variantPickFirst.so", "foo")

        FileSubject.assertThat(appSubProject.getApk(RELEASE).file.toFile()).exists()
        val releaseApk = TruthHelper.assertThat(appSubProject.getApk(RELEASE))
        releaseApk.doesNotContainJavaResource("lib/x86/dslExclude.so")
        releaseApk.doesNotContainJavaResource("lib/x86/releaseExclude.so")
        releaseApk.containsJavaResourceWithContent("lib/x86/appKeep.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86/dslPickFirst.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86/debugExclude.so", "foo")
        releaseApk.containsJavaResourceWithContent("lib/x86/variantPickFirst.so", "foo")

        FileSubject.assertThat(appSubProject.getApk(ANDROIDTEST_DEBUG).file.toFile()).exists()
        val androidTestApk = TruthHelper.assertThat(appSubProject.getApk(ANDROIDTEST_DEBUG))
        androidTestApk.doesNotContainJavaResource("lib/x86/testExclude.so")
        androidTestApk.containsJavaResourceWithContent("lib/x86/testKeep.so", "foo")

        libSubProject.assertThatAar("debug") {
            this.doesNotContain("jni/x86/dslExclude.so")
            this.doesNotContain("jni/x86/libExclude.so")
            this.contains("jni/x86/libKeep.so")
        }

        FileSubject.assertThat(libSubProject.getApk(ANDROIDTEST_DEBUG).file.toFile()).exists()
        val libAndroidTestApk = TruthHelper.assertThat(libSubProject.getApk(ANDROIDTEST_DEBUG))
        libAndroidTestApk.doesNotContainJavaResource("lib/x86/testExclude.so")
        libAndroidTestApk.containsJavaResourceWithContent("lib/x86/testKeep.so", "bar")
    }
}
