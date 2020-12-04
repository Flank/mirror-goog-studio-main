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

import com.android.build.api.variant.DexPackagingOptions
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.RELEASE
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.FileSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Integration tests for [DexPackagingOptions]
 */
class DexPackagingOptionsTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        lintOptions {
                            checkReleaseBuilds = false // TODO(b/146208910): Lint is not compatible with instant execution
                        }
                        packagingOptions {
                            dex {
                                useLegacyPackaging = true
                            }
                        }
                    }
                    androidComponents {
                        onVariants(selector().withName("debug"), {
                            packagingOptions.dex.useLegacyPackaging.set(false)
                        })
                    }
                    """.trimIndent()
            )

    private val multiModuleTestProject =
        MultiModuleTestProject.builder().subproject(":app", app).build()

    @get:Rule
    val project =
        GradleTestProject
            .builder()
            .fromTestApp(multiModuleTestProject)
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
            // b/149978740
            .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=1")
            .create()

    @Test
    fun testDexPackagingOptions() {
        val appSubProject = project.getSubproject(":app")
        appSubProject.execute("assemble")

        val debugApkFile = appSubProject.getApk(DEBUG).file.toFile()
        FileSubject.assertThat(debugApkFile).exists()
        ZipFile(debugApkFile).use {
            val classesDotDex = it.getEntry("classes.dex")
            assertThat(classesDotDex).isNotNull()
            assertThat(classesDotDex.method).isEqualTo(ZipEntry.STORED)
        }

        val releaseApkFile = appSubProject.getApk(RELEASE).file.toFile()
        FileSubject.assertThat(releaseApkFile).exists()
        ZipFile(releaseApkFile).use {
            val classesDotDex = it.getEntry("classes.dex")
            assertThat(classesDotDex).isNotNull()
            assertThat(classesDotDex.method).isEqualTo(ZipEntry.DEFLATED)
        }
    }
}
