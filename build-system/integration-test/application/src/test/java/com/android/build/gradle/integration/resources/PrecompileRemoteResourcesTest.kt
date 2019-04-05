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

package com.android.build.gradle.integration.resources

import com.android.SdkConstants.FD_MERGED
import com.android.SdkConstants.FD_RES
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.testutils.apk.Apk
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class PrecompileRemoteResourcesTest {

    private val publishedLib = MinimalSubProject.lib("com.example.publishedLib")
        .withFile(
            "src/main/res/values-v28/strings.xml",
            """<resources>
                        <string name="foo">publishedLib</string>
                        <string name="my_version_name">1.0</string>
                    </resources>""".trimIndent()
        )
        .withFile(
            "src/main/res/layout/layout_random_name.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                </LinearLayout>
                        """.trimIndent()
        )
        .withFile(
            "src/main/res/drawable-v26/ic_launcher_background.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                </vector>
            """.trimIndent()
        )
        .withFile(
            "src/main/AndroidManifest.xml",
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                         xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="com.example.publishedLib"
                    android:versionName="@com.example.publishedLib:string/my_version_name">
                </manifest>""".trimIndent()
        )

    private val localLib = MinimalSubProject.lib("com.example.localLib")
        .appendToBuild(
            """repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                dependencies { implementation name: 'publishedLib-release', ext:'aar' }
            """.trimIndent()
        )
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="from_published_lib">@string/foo</string>
                    </resources>"""
        )
        .withFile(
            "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/ic_launcher_background" />
                </adaptive-icon>
            """.trimIndent()
        )

    private val app = MinimalSubProject.app("com.example.app")
        .appendToBuild(
            """repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                dependencies { implementation name: 'publishedLib-release', ext:'aar' }
            """.trimIndent()
        )
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="mystring">@string/foo</string>
                    </resources>"""
        )
        .withFile(
            "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/ic_launcher_background" />
                </adaptive-icon>
            """.trimIndent()
        )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":publishedLib", publishedLib)
            .subproject(":localLib", localLib)
            .subproject(":app", app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkAppBuild() {
        project.executor().with(BooleanOption.PRECOMPILE_REMOTE_RESOURCES, true)
            .run(":publishedLib:assembleRelease")
        project.executor().with(BooleanOption.PRECOMPILE_REMOTE_RESOURCES, true)
            .run(":app:assembleDebug")

        checkAarResourcesCompilerTransformOutput()
        checkValuesAndLayoutResourcedAreMerged(
            FileUtils.join(
                project.getSubproject(":app").intermediatesDir,
                FD_RES,
                FD_MERGED,
                "debug"
            )
        )

        checkAarResourcesAddedToApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
    }

    @Test
    fun checkLocalLibBuild() {
        project.executor().with(BooleanOption.PRECOMPILE_REMOTE_RESOURCES, true)
            .run(":publishedLib:assembleRelease")

        val result = project.executor().with(BooleanOption.PRECOMPILE_REMOTE_RESOURCES, true)
            .run(":localLib:assembleRelease")

        assertThat(result.getTask(":localLib:verifyReleaseResources").didWork()).isTrue()
    }

    private fun checkAarResourcesCompilerTransformOutput() {
        val transformCacheDir = FileUtils.join(
            GradleTestProject.getGradleUserHome(GradleTestProject.BUILD_DIR).toFile(),
            "caches",
            "transforms-2",
            "files-2.1"
        )
        assertThat(transformCacheDir.exists()).isTrue()
        assertThat(transformCacheDir.isDirectory).isTrue()

        var outputDir: File? = null
        for (subdirectory in transformCacheDir.listFiles()) {
            if (subdirectory.isDirectory) {
                val outputDirCandidate = File(subdirectory, "com.example.publishedLib")
                if (outputDirCandidate.exists() && outputDirCandidate.isDirectory) {
                    outputDir = outputDirCandidate
                    break
                }
            }
        }
        // values and layout resources shouldn't be here
        assertThat(outputDir).isNotNull()
        assertThat(outputDir!!.listFiles()).hasLength(1)
        assertThat(outputDir.listFiles()[0].name).isEqualTo(
            Aapt2RenamingConventions.compilationRename(
                File(File("drawable-v26"), "ic_launcher_background.xml")
            )
        )
    }

    private fun checkValuesAndLayoutResourcedAreMerged(mergedResDir: File) {
        assertThat(mergedResDir.listFiles()).hasLength(4)
        assertThat(mergedResDir.listFiles().map { file -> file.name }.toSortedSet()).containsExactlyElementsIn(
            listOf(
                Aapt2RenamingConventions.compilationRename(
                    File(File("layout"), "layout_random_name.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("mipmap-anydpi-v26"), "ic_launcher.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("values-v28"), "values-v28.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("values"), "values.xml")
                )
            )
        )
    }

    private fun checkAarResourcesAddedToApk(apk: Apk) {
        assertThatApk(apk).containsResource("drawable-v26/ic_launcher_background.xml")
    }
}