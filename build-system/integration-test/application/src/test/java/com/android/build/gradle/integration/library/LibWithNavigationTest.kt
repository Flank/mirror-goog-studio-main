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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Tests for library module with navigation. */
class LibWithNavigationTest {

    private val library =
        MinimalSubProject.lib("com.example.library")
            .withFile(
                "src/main/AndroidManifest.xml",
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.library">
                    <application android:name="library">
                        <activity android:name=".MainActivity">
                            <nav-graph android:value="@navigation/nav1" />
                        </activity>
                     </application>
                </manifest>""".trimIndent()
            )

    private val testApp = MultiModuleTestProject.builder().subproject(":library", library).build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    /**
     * Test that we can build a release AAR when there are <nav-graph> tags in the library manifest.
     * Regression test for Issue 140856013.
     */
    @Test
    fun testAssembleReleaseWithNavGraphTagInManifest() {
        project.execute("clean", ":library:assembleRelease")
        project.getSubproject("library").withAar("release") {
            assertThat(androidManifestContentsAsString).contains(
                "<nav-graph android:value=\"@navigation/nav1\" />")
        }
    }
}
