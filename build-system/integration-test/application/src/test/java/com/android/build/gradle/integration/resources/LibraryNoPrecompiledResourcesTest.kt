/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class LibraryNoPrecompiledResourcesTest {

    private val localLib = MinimalSubProject.lib("com.test.localLib")
        .appendToBuild("group = \"com.test\"\nversion = \"99.0\"\n")
        .withFile(
            "src/main/res/layout/lib_activity.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                            android:orientation="vertical"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent">

                        </LinearLayout>"""
        )
        .withFile("src/main/java/test/Data.java",
            "package test; public class Data {}")

    private val app = MinimalSubProject.app("com.example.app")
        .appendToBuild(
            """
                android {
                    buildTypes {
                        release {
                            shrinkResources true
                            minifyEnabled true
                        }
                    }
                }
                dependencies {
                    implementation "com.test:localLib:1.0"
                    implementation project(":localLib")
                    androidTestImplementation project(":localLib")
                }
            """.trimIndent()
        )
        .withFile("src/androidTest/java/test/test.java",
        "package test; public class DataTest extends Data {}")
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
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
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
            .subproject(":localLib", localLib)
            .subproject(":app", app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testDependencyOnLibrariesWithNoProcompiledDependencies() {
            project.executor()
                .with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, true)
                .run("clean", "app:processDebugAndroidTestResources")
    }
}
