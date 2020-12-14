/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests support for RROs (Resource Runtime Overlays) tags handling at compile time.
 * These tests make sure we allow the "overlay" tag in the manifest, and the "overlayable" tag in
 * values resources (it makes sure they are not removed by the resource merger, that they are parsed
 * correctly and do not create new resources in the resource table).
 */
class OverlayableResourcesTest {

    private val lib = MinimalSubProject.lib("com.example.lib")
        .withFile(
            "src/main/res/values/strings.xml",
            """
                <resources>
                    <string name="foo_lib">Foo</string>
                    <integer name="bar_lib">42</integer>
                    <overlayable name="LibraryResources">
                        <policy type="public">
                            <item type="string" name="foo_lib" />
                            <item type="integer" name="bar_lib" />
                        </policy>
                    </overlayable>
                </resources>""".trimIndent())

    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/res/values/strings.xml",
            """
                <resources>
                    <string name="foo_app">FOO</string>
                    <integer name="bar_app">42</integer>
                    <overlayable name="AppResources">
                        <policy type="public">
                            <item type="string" name="foo_app" />
                            <item type="integer" name="bar_app" />
                        </policy>
                    </overlayable>
                </resources>""".trimIndent())

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":app", app)
            .dependency(app, lib)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testOverlayableTagIsAllowedAndPassedCorrectly() {
        val appManifest =
            File(project.getSubproject("app").mainSrcDir.parentFile, "AndroidManifest.xml")
        assertThat(appManifest).exists()
        // Append an "overlay" tag to test that the Manifest Merger allows it.
        TestFileUtils.searchAndReplace(
            appManifest,
            "</manifest>",
            """
                <overlay android:targetPackage="android" android:targetName="AppResources"/>
            </manifest>""".trimIndent()
        )
        project.executor().run(":app:assembleDebug")

        // Check library resources
        val libFiles = project.getSubproject("lib")

        val mergedLibValues = libFiles.getIntermediateFile(
            "packaged_res",
            "debug",
            "values",
            "values.xml")

        // Make sure the merged values in lib kept both the overlayable tag and its content.
        assertThat(mergedLibValues).containsAllOf(
            "overlayable name=\"LibraryResources\"",
            "item name=\"foo_lib\" type=\"string\""
        )

        val libRdef = libFiles.getIntermediateFile(
            "local_only_symbol_list",
            "debug",
            "R-def.txt"
        )
        assertThat(libRdef).exists()

        // Make sure our library resource parsers did not create any overlayable resources.
        assertThat(libRdef).contains("string foo_lib")
        assertThat(libRdef).doesNotContain("overlayable")

        // Application resources
        val appFiles = project.getSubproject("app")

        val mergedAppValues = appFiles.getIntermediateFile(
            "incremental",
            "mergeDebugResources",
            "merged.dir",
            "values",
            "values.xml"
        )

        // Make sure the merged values in app (big merge) contain both overlayable from app and lib,
        // as well as their content.
        assertThat(mergedAppValues).containsAllOf(
            "overlayable name=\"AppResources\"",
            "item name=\"foo_app\" type=\"string\"",
            "overlayable name=\"LibraryResources\"",
            "item name=\"foo_lib\" type=\"string\""
        )

        val appRTxt = appFiles.getIntermediateFile(
            "runtime_symbol_list",
            "debug",
            "R.txt"
        )
        assertThat(appRTxt).exists()

        // Make sure the resources are present, but overlayable did not create a new resource type.
        assertThat(appRTxt).containsAllOf("string foo_lib", "string foo_app")
        assertThat(appRTxt).doesNotContain("overlayable")
    }
}
