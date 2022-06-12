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
import com.android.build.api.artifact.Artifact
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class ToolsAnnotatedManifestEntriesTest {

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin("com.android.privacy-sandbox-sdk")).create()

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

        project.execute("mainManifestGenerator")
        // check that merged manifest still has the tools: entries.
        // eventually, once the bundletool is ready, we should check there as well.
        val manifestFile = FileUtils.join(project.buildDir,
            Artifact.Category.INTERMEDIATES.name.lowercase(),
            PrivacySandboxSdkInternalArtifactType.SANDBOX_MANIFEST.getFolderName(),
            "single", // single variant for now
            FN_ANDROID_MANIFEST_XML
        )
        Truth.assertThat(manifestFile.exists()).isTrue()
        manifestFile.readText().also {
            Truth.assertThat(it).contains("xmlns:tools")
            Truth.assertThat(it).contains("tools:requiredByPrivacySandboxSdk")
        }

    }
}
