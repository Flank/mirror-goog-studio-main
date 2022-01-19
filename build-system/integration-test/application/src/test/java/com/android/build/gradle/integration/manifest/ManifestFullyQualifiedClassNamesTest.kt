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

package com.android.build.gradle.integration.manifest

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

class ManifestFullyQualifiedClassNamesTest {

    private val app =
        MinimalSubProject.app()
            .appendToBuild(
                """
                    android {
                        namespace 'com.example.app'

                        defaultConfig {
                            applicationId 'com.example.id'
                        }
                    }
                """.trimIndent()
            ).withFile(
                "src/main/AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <activity android:name="MainActivity"/>
                        </application>
                    </manifest>
                """.trimIndent()

            ).withFile(
                "src/main/java/com/example/app/MainActivity.java",
                """
                    package com.example.app;

                    import android.app.Activity;
                    import android.os.Bundle;

                    public class MainActivity extends Activity {
                        @Override
                        public void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                        }
                    }
                """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib()
            .appendToBuild(
                """
                    android {
                        namespace 'com.example.lib'
                    }
                """.trimIndent()
            ).withFile(
                "src/main/AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <activity android:name=".LibraryActivity"/>
                        </application>
                    </manifest>
                """.trimIndent()

            ).withFile(
                "src/main/java/com/example/lib/LibraryActivity.java",
                """
                    package com.example.app;

                    import android.app.Activity;
                    import android.os.Bundle;

                    public class LibraryActivity extends Activity {
                        @Override
                        public void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                        }
                    }
                """.trimIndent()
            )

    @get:Rule
    val project : GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .dependency(app, lib)
                    .build()
            ).create()

    /**
     * Regression test for Issue 213474165
     */
    @Test
    fun build() {
        project.executor().run("assembleDebug")

        val libMergedManifest =
            project.getSubproject("lib")
                .file("build/intermediates/merged_manifest/debug/AndroidManifest.xml")

        val appMergedManifest =
            project.getSubproject("app")
                .file("build/intermediates/packaged_manifests/debug/AndroidManifest.xml")

        // library and app merged manifests contain correct package values
        assertThat(libMergedManifest).contains("package=\"com.example.lib\"")
        assertThat(appMergedManifest).contains("package=\"com.example.id\"")

        // app merged manifest contains correct fully qualified class names
        assertThat(appMergedManifest).contains("com.example.app.MainActivity")
        assertThat(appMergedManifest).contains("com.example.lib.LibraryActivity")
    }
}
