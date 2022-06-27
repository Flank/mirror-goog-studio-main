/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.utils.IgnoredTests
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.zip.ZipFile

class FusedLibraryMergeResourcesTaskTest {

    private val testAar = generateAarWithContent("com.remotedep.remoteaar",
            resources = mapOf("values/strings.xml" to
                    // language=XML
                    """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="string_from_remote_lib">Remote String</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            ),
    )

    @JvmField
    @Rule
    val project = createGradleProject {
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
            }
            addFile(
                "src/main/res/values/strings.xml",
                """<resources>
                <string name="string_from_androidLib1">androidLib1</string>
                <string name="string_overridden">androidLib1</string>
              </resources>"""
            )
        }
        subProject(":androidLib3") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib3"
            }
            addFile(
                    "src/main/res/values/strings.xml",
            """<resources>
                <string name="string_from_android_lib_3">androidLib3</string>
                <string name="string_overridden">androidLib3</string>
              </resources>"""
            )
            addFile(
                    "src/main/res/layout/layout.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                            "    android:layout_width=\"match_parent\"\n" +
                            "    android:layout_height=\"match_parent\">\n" +
                            "\n" +
                            "    <TextView\n" +
                            "        android:id=\"@+id/androidlib3_textview\"\n" +
                            "        android:layout_width=\"match_parent\"\n" +
                            "        android:layout_height=\"wrap_content\"\n" +
                            "        android:layout_weight=\"1\"\n" +
                            "        android:text=\"TextView\" />\n" +
                            "</LinearLayout>"
            )
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        subProject(":androidLib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib2"
            }
            addFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_android_lib_2">androidLib2</string>
                <string name="string_overridden">androidLib2</string>
              </resources>"""
            )
        }
        subProject(":fusedLib1") {
            plugins.add(PluginType.FUSED_LIBRARY)
            android {
                namespace = "com.example.fusedLib1"
                minSdk = 1
            }
            dependencies {
                include(project(":androidLib2"))
                include(project(":androidLib3"))
                include(MavenRepoGenerator.Library("com.remotedep:remoteaar:1", "aar", testAar))
            }
        }
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                namespace = "com.example.app"
                minSdk = 1
            }
            addFile("src/main/res/values/strings.xml",
                    """<resources>
                <string name="string_from_app">app</string>
                <string name="string_overridden">app</string>
              </resources>"""
                    )
            // Add a dependency on the fused library aar in the test if needed.
        }
    }

    @Test
    @Ignore(IgnoredTests.BUG_23682893)
    fun testMerge() {
        val fusedLibraryAar = getFusedLibraryAar()
        ZipFile(fusedLibraryAar).use { aar ->
            val mergedValues = aar.getEntry("res/values/values.xml")
            val mergedLayout = aar.getEntry("res/layout/layout.xml")
            val mergedValuesContents = String(aar.getInputStream(mergedValues).readBytes())
            val mergedLayoutContents = String(aar.getInputStream(mergedLayout).readBytes())
            assertThat(mergedValuesContents).isEqualTo(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<resources>\n" +
                            "    <string name=\"string_from_androidLib1\">androidLib1</string>\n" +
                            "    <string name=\"string_from_android_lib_2\">androidLib2</string>\n" +
                            "    <string name=\"string_from_android_lib_3\">androidLib3</string>\n" +
                            "    <string name=\"string_from_remote_lib\">Remote String</string>\n" +
                            "    <string name=\"string_overridden\">androidLib2</string>\n" +
                            "</resources>"
            )
            assertThat(mergedLayoutContents).isEqualTo(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                            "    android:layout_width=\"match_parent\"\n" +
                            "    android:layout_height=\"match_parent\">\n" +
                            "\n" +
                            "    <TextView\n" +
                            "        android:id=\"@+id/androidlib3_textview\"\n" +
                            "        android:layout_width=\"match_parent\"\n" +
                            "        android:layout_height=\"wrap_content\"\n" +
                            "        android:layout_weight=\"1\"\n" +
                            "        android:text=\"TextView\" />\n" +
                            "</LinearLayout>"
            )
        }
    }

    @Test
    @Ignore(IgnoredTests.BUG_23682893)
    fun testAppResourceMergingWithFusedLib() {
        val publishedFusedLibrary = getFusedLibraryAar()
        val appSubproject = project.getSubproject("app")
        appSubproject.buildFile.appendText(
                "dependencies {" +
                        "implementation(files(\'${publishedFusedLibrary.invariantSeparatorsPath}\'))" +
                        "}"
        )
        project.execute(":app:assembleDebug")
        val apk = appSubproject.getApk(GradleTestProject.ApkType.DEBUG)
        val incrementalMergedResDir = FileUtils.join(
                appSubproject.getIntermediateFile(
                        InternalArtifactType.MERGED_RES_INCREMENTAL_FOLDER.getFolderName()),
                "debug",
                "mergeDebugResources",
                "merged.dir"
        )
        assertThat(FileUtils.join(incrementalMergedResDir, "values", "values.xml")
                .readLines().map(String::trim))
                .containsExactly(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                        "<resources>",
                        "<string name=\"string_from_androidLib1\">androidLib1</string>",
                        "<string name=\"string_from_android_lib_2\">androidLib2</string>",
                        "<string name=\"string_from_android_lib_3\">androidLib3</string>",
                        "<string name=\"string_from_app\">app</string>",
                        "<string name=\"string_from_remote_lib\">Remote String</string>",
                        "<string name=\"string_overridden\">app</string>",
                        "</resources>"
                )
        assertThat(apk.entries.map(Path::toString)).contains("/res/layout/layout.xml")
    }

    private fun getFusedLibraryAar(): File {
        project.getSubproject("fusedLib1").execute(":fusedLib1:bundle")
        val fusedLibAar =
                FileUtils.join(project.getSubproject("fusedLib1").buildDir, "bundle", "bundle.aar")
        assertThat(fusedLibAar.exists()).isTrue()
        return fusedLibAar
    }
}
