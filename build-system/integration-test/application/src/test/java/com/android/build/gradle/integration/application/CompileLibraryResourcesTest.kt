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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.internal.res.shrinker.DummyContent
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_RES
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompileLibraryResourcesTest {

    private val library = MinimalSubProject.lib("com.example.library")
        .withFile(
            "src/main/res/drawable-v26/ic_launcher_background.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                     <item
                      android:drawable="@drawable/button"/>
                </layer-list>
            """.trimIndent()
        )
        .withFile(
            "src/main/res/layout/layout_random_name.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:layout_x="10px"
                        android:layout_y="110px"
                        android:text="test"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                    <EditText
                        android:layout_x="150px"
                        android:layout_y="100px"
                        android:width="150px"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
                        """.trimIndent()
        )
        .withFile(
            "src/main/res/drawable-v26/button.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                </vector>
            """.trimIndent()
        ).withFile(
            "src/main/res/values-v28/strings.xml",
            """<resources>
                    <string name="foo">publishedLib</string>
                    <string name="my_version_name">1.0</string>
                </resources>""".trimIndent()
        ).withFile(
            "src/main/res/raw/me.raw",
            "test"
        )

    private val app = MinimalSubProject.app("com.example.app")
        .appendToBuild(
            """dependencies { api project(':library') }
                android {
                    buildTypes {
                        release {
                            shrinkResources true
                            minifyEnabled true
                        }
                    }
                }
            """
                .trimIndent()
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
        .withFile(
            "src/main/AndroidManifest.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app" >
                    <application android:label="app_name" android:icon="@mipmap/ic_launcher">
                        <activity android:name="MainActivity"
                                  android:label="app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>""".trimIndent()
        )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":library", library)
            .subproject(":app", app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testResourcesAreCompiledAndProcessed() {
        project.executor().with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, true)
            .run(":app:assembleDebug")

        checkCompiledLibraryResourcesDir(
            setOf(
                Aapt2RenamingConventions.compilationRename(
                    File(File("layout"), "layout_random_name.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("drawable-anydpi-v26"), "button.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("drawable-v26"), "ic_launcher_background.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("raw"), "me.raw")
                )
            )
        )

        checkOnlyValuesWasMerged()

        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)

        // check resources are added to the final apk

        assertThatApk(apk).containsResource("raw/me.raw")
        assertThatApk(apk).containsResource("layout/layout_random_name.xml")
        assertThatApk(apk).containsResource("drawable-v26/ic_launcher_background.xml")
        assertThatApk(apk).containsResource("drawable-anydpi-v26/button.xml")
        assertThatApk(apk).containsResource("mipmap-anydpi-v26/ic_launcher.xml")
    }

    @Test
    fun testIncrementalResourceChange() {
        project.executor().with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, true)
            .run(":app:assembleDebug")

        val filesLastModified = FileUtils.join(
            project.getSubproject(":library").intermediatesDir,
            "compiled_local_resources",
            "debug",
            "out"
        ).listFiles()!!.sorted().map { it.lastModified() }

        // change me.raw
        FileUtils.writeToFile(
            FileUtils.join(
                project.getSubproject(":library").projectDir,
                "src",
                "main",
                "res",
                "raw",
                "me.raw"
            ), "test2"
        )

        project.executor().with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, true)
            .run(":app:assembleDebug")

        FileUtils.join(
            project.getSubproject(":library").intermediatesDir,
            "compiled_local_resources",
            "debug",
            "out"
        ).listFiles()!!.sorted().forEachIndexed { index, file ->
            // Check only the raw file was updated
            if (file.name.startsWith("raw_me")) {
                assertThat(file.lastModified()).isNotEqualTo(filesLastModified[index])
            } else {
                assertThat(file.lastModified()).isEqualTo(filesLastModified[index])
            }
        }

        // Remove ic_launcher_background
        FileUtils.deleteIfExists(
            FileUtils.join(
                project.getSubproject(":library").projectDir,
                "src",
                "main",
                "res",
                "drawable-v26",
                "ic_launcher_background.xml"
            )
        )

        FileUtils.writeToFile(
            FileUtils.join(
                project.getSubproject(":app").projectDir,
                "src",
                "main",
                "res",
                "mipmap-anydpi-v26",
                "ic_launcher.xml"
            ), """<?xml version="1.0" encoding="utf-8"?>
                        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        </adaptive-icon>
                    """.trimIndent()
        )

        project.executor().with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, true)
            .run(":app:assembleDebug")

        // check the file doesn't exist in the compiled library resources

        checkCompiledLibraryResourcesDir(
            setOf(
                Aapt2RenamingConventions.compilationRename(
                    File(File("layout"), "layout_random_name.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("drawable-anydpi-v26"), "button.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("raw"), "me.raw")
                )
            )
        )

        // check the resource doesn't exist in the final apk

        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(apk).doesNotContainResource("drawable-v26/ic_launcher_background.xml")
    }

    @Test
    fun testIntegrationWithResourceShrinker() {
        project.executor().with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, true)
            // http://b/149978740
            .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
            .run(":app:assembleRelease")

        val compressed = project.getSubproject(":app").getIntermediateFile(
            "shrunk_processed_res",
            "release",
            "resources-release-stripped.ap_"
        )

        assertThat(compressed.isFile).isTrue()

        ZFile.openReadOnly(compressed).use {
            assertThat(it.get("res/layout/layout_random_name.xml")!!.read()).isEqualTo(
                DummyContent.TINY_BINARY_XML
            )
            assertThat(it.get("res/drawable-anydpi-v26/button.xml")!!.read()).isNotEqualTo(
                DummyContent.TINY_BINARY_XML
            )
            assertThat(it.get("res/drawable-v26/ic_launcher_background.xml")!!.read())
                    .isNotEqualTo(
                DummyContent.TINY_BINARY_XML
            )
        }
    }

    private fun checkCompiledLibraryResourcesDir(expected: Set<String>) {
        val compiledLibraryResourcesDir = FileUtils.join(
            project.getSubproject(":library").intermediatesDir,
            "compiled_local_resources",
            "debug",
            "out"
        )

        assertThat(compiledLibraryResourcesDir.listFiles()!!.map { it.name }.toSet()).containsExactlyElementsIn(
            expected
        )
    }

    private fun checkOnlyValuesWasMerged() {
        val mergedResDir = File(MERGED_RES.getOutputDir( project.getSubproject(":app").buildDir), "debug")

        assertThat(mergedResDir.listFiles()!!.map { it.name }.toSet()).containsExactlyElementsIn(
            setOf(
                Aapt2RenamingConventions.compilationRename(
                    File(File("values-v28"), "values-v28.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("values"), "values.xml")
                ),
                Aapt2RenamingConventions.compilationRename(
                    File(File("mipmap-anydpi-v26"), "ic_launcher.xml")
                )
            )
        )
    }
}
