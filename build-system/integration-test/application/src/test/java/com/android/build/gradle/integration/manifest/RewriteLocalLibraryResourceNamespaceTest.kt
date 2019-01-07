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

package com.android.build.gradle.integration.manifest

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RewriteLocalLibraryResourceNamespaceTest {
    @JvmField @Rule
    var project = GradleTestProject.builder().fromTestProject("projectWithModules").create()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val appProject = project.getSubproject("app")
        val libProject = project.getSubproject("library")

        Files.asCharSink(project.settingsFile, Charsets.UTF_8)
            .write("include 'app', 'library'")

        // setup dependencies.
        appendToFile(
            appProject.buildFile,
            """
             dependencies {
                compile project(':library')
            }
            android.aaptOptions.namespaced = true
            """.trimIndent()
        )

        appendToFile(
            libProject.buildFile,
            """
            android.aaptOptions.namespaced = true
            """.trimIndent()
        )

        libProject.file("src/main/AndroidManifest.xml").delete()
        FileUtils.createFile(
            libProject.file("src/main/AndroidManifest.xml"),
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.android.multiproject.library">
                <application>
                    <activity android:name=".LibraryActivity"
                                  android:label="@string/app_name"/>
                </application>
            </manifest>
            """.trimIndent()
        )

        FileUtils.createFile(
            libProject.file("src/main/java/com/example/android/multiproject/library/LibraryActivity.java"),
            """
            package com.example.android.multiproject.library;

            import android.app.Activity;
            import android.content.Intent;
            import android.os.Bundle;
            import android.view.View;

            public class LibraryActivity extends Activity {
                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.liblayout);
                }
            }
            """.trimIndent()
        )
        FileUtils.createFile(
            libProject.file("src/main/res/values/strings.xml"),
            """
            <resources>
                <string name="app_name">Library App</string>
            </resources>
            """.trimIndent()
        )
        FileUtils.createFile(
            libProject.file("src/main/res/values/public.xml"),
            """
            <resources>
                <public type="string" name="app_name"/>
            </resources>
            """.trimIndent()
        )
    }

    @Test
    fun build() {
        project.executor().run("assembleDebug")

        val libraryManifest =
            project.getSubproject("library")
                .file("build/intermediates/library_manifest/debug/AndroidManifest.xml")

        // namespaces in library are resolved
        assertThat(libraryManifest).contains("@com.example.android.multiproject.library:string/app_name")

        val mergedManifest =
            project.getSubproject("app")
                .file("build/intermediates/merged_manifests/debug/AndroidManifest.xml")

        // namespaces in main app are resolved only for library
        assertThat(mergedManifest).contains("@com.example.android.multiproject.library:string/app_name")
        assertThat(mergedManifest).contains("@string/app_name")
        assertThat(mergedManifest).doesNotContain("com.example.android.multiproject:string/app_name")
    }
}
