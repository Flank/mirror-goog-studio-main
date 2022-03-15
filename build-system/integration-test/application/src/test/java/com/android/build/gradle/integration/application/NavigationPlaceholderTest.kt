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

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test

class NavigationPlaceholderTest {

    private val fooPlaceholder = "\${foo}"
    private val hostPlaceholder = "\${host}"
    private val schemePlaceholder = "\${scheme}"
    private val appIdPlaceholder = "\${applicationId}"

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        defaultConfig {
                            manifestPlaceholders =
                                [
                                    foo: "appFoo",
                                    scheme: "appScheme",
                                    host: "app.example.com",
                                ]
                        }
                    }
                """.trimIndent()
            )
            .withFile(
                "src/main/AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <activity android:name="MyActivity">
                                <nav-graph android:value="@navigation/nav_app"/>
                                <nav-graph android:value="@navigation/nav_lib"/>
                            </activity>
                            <meta-data
                                android:name="app"
                                android:value="$fooPlaceholder"/>
                        </application>
                    </manifest>
                """.trimMargin()
            )
            .withFile(
                "src/main/res/navigation/nav_app.xml",
                """
                    <navigation xmlns:app="http://schemas.android.com/apk/res-auto">
                        <deepLink
                            app:uri="$schemePlaceholder://$hostPlaceholder/$appIdPlaceholder"/>
                    </navigation>
                """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                """
                    android {
                        defaultConfig {
                            manifestPlaceholders =
                                [
                                    foo: "libFoo",
                                    host: "lib.example.com",
                                    scheme: "libScheme",
                                ]
                        }
                    }
                """.trimIndent()
            )
            .withFile(
                "src/main/AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <meta-data
                                android:name="lib"
                                android:value="$fooPlaceholder"/>
                        </application>
                    </manifest>
                """.trimMargin()
            )
            .withFile(
                "src/main/res/navigation/nav_lib.xml",
                """
                    <navigation xmlns:app="http://schemas.android.com/apk/res-auto">
                        <deepLink
                            app:uri="$schemePlaceholder://$hostPlaceholder/$appIdPlaceholder"/>
                    </navigation>
                """.trimIndent()
            )

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .dependency(app, lib)
                    .build()
            )
            .create()

    @Test
    fun testNavigationPlaceholders() {
        project.executor().run(":app:processDebugMainManifest")
        val mergedManifest =
            project.file(
                "app/build/${SdkConstants.FD_INTERMEDIATES}/${SingleArtifact.MERGED_MANIFEST.getFolderName()}/debug/AndroidManifest.xml"
            )


        PathSubject.assertThat(mergedManifest).contentWithUnixLineSeparatorsIsExactly(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app"
                    android:versionCode="1" >

                    <uses-sdk
                        android:minSdkVersion="14"
                        android:targetSdkVersion="14" />

                    <application android:debuggable="true" >
                        <activity android:name="com.example.app.MyActivity" >
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />

                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="appScheme" />
                                <data android:host="app.example.com" />
                                <data android:path="/com.example.app" />
                            </intent-filter>
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />

                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="libScheme" />
                                <data android:host="lib.example.com" />
                                <data android:path="/com.example.app" />
                            </intent-filter>
                        </activity>

                        <meta-data
                            android:name="app"
                            android:value="appFoo" />
                        <meta-data
                            android:name="lib"
                            android:value="libFoo" />
                    </application>

                </manifest>
            """.trimIndent()
        )
    }

    @Test
    fun testNavigationPlaceholders_withoutLibManifestPlaceholders() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("lib").buildFile,
            """
                |        manifestPlaceholders =
                |            [
                |                foo: "libFoo",
                |                host: "lib.example.com",
                |                scheme: "libScheme",
                |            ]
            """.trimMargin(),
            ""
        )
        project.executor().run(":app:processDebugMainManifest")
        val mergedManifest =
            project.file(
                "app/build/${SdkConstants.FD_INTERMEDIATES}/${SingleArtifact.MERGED_MANIFEST.getFolderName()}/debug/AndroidManifest.xml"
            )

        PathSubject.assertThat(mergedManifest).contentWithUnixLineSeparatorsIsExactly(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app"
                    android:versionCode="1" >

                    <uses-sdk
                        android:minSdkVersion="14"
                        android:targetSdkVersion="14" />

                    <application android:debuggable="true" >
                        <activity android:name="com.example.app.MyActivity" >
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />

                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="appScheme" />
                                <data android:host="app.example.com" />
                                <data android:path="/com.example.app" />
                            </intent-filter>
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />

                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="appScheme" />
                                <data android:host="app.example.com" />
                                <data android:path="/com.example.app" />
                            </intent-filter>
                        </activity>

                        <meta-data
                            android:name="app"
                            android:value="appFoo" />
                        <meta-data
                            android:name="lib"
                            android:value="appFoo" />
                    </application>

                </manifest>
            """.trimIndent()
        )
    }
}
