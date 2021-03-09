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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class GenerateApkDataTest {

    @Rule
    @JvmField
     val project = builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Before
    fun setup() {
        project.buildFile.appendText("\n" +
                """
            android.defaultConfig.wearAppUnbundled = true
        """.trimIndent()
        )
    }

    @Test
    fun testMicroApkAndroidManifestInCorrectFolder() {
        project.execute("assembleDebug")
        assertTrue(project.buildResult.didWorkTasks.contains(":handleDebugMicroApk"))
        val manifestFile = FileUtils.join(
                project.generatedDir,
                "manifests",
                "microapk",
                "debug",
                "AndroidManifest.xml")
        PathSubject.assertThat(manifestFile).exists()
        assertTrue(manifestFile.isFile)
    }
}
