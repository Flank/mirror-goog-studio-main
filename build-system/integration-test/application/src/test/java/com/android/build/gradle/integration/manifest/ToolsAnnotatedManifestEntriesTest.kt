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

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.MERGED_MANIFEST
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test to ensure the
 * tools:requiredByPrivacySandboxSdk="true" attribute is preserved
 * by the manifest merger.
 *
 * TODO(b/235469089): Currently tests a manual case, once support is implemented
 *     this should be updated to assert about the contents of the generated APKs
 */
class ToolsAnnotatedManifestEntriesTest {

    @get:Rule
    var project = createGradleProject {
        subProject(":privacy-sandbox-sdk") {
            plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
            android {
                defaultCompileSdk()
                namespace = "com.example.sdk"
                minSdk = 13
            }
            dependencies {
                include(project(":sdk-impl"))
            }
        }
        subProject(":sdk-impl") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.sdk.impl"
                minSdk = 13
            }
            addFile("src/main/AndroidManifest.xml", """
                <manifest
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:tools="http://schemas.android.com/tools">

                    <uses-permission
                            android:name="android.permission.WAKE_LOCK"
                            tools:requiredByPrivacySandboxSdk="true" />
                </manifest>
            """.trimIndent())
        }
    }


    @Test
    fun testToolsAnnotatedManifest() {

        project.buildFile.writeText(
            """
${project.computeGradleBuildscript()}

apply plugin: 'com.android.privacy-sandbox-sdk'

android {
    namespace 'com.example.sdk'
    minSdk 13
    compileSdk $DEFAULT_COMPILE_SDK_VERSION
}
            """
        )

        project.execute(":privacy-sandbox-sdk:mergeManifest")
        // check that merged manifest still has the tools: entries.
        // eventually, once the bundletool is ready, we should check there as well.
        val manifestFile = project.getSubproject(":privacy-sandbox-sdk")
                .intermediatesDir.resolve(MERGED_MANIFEST.getFolderName())
                .resolve("single") // single variant for now
                .resolve(FN_ANDROID_MANIFEST_XML)

        assertThat(manifestFile.exists()).isTrue()
        manifestFile.readText().also {
            assertThat(it).contains("xmlns:tools")
            assertThat(it).contains("tools:requiredByPrivacySandboxSdk")
        }

    }
}
