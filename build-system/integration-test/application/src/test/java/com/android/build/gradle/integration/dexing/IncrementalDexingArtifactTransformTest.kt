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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.File

class IncrementalDexingArtifactTransformTest {

    // create app module, and force multidex
    val app = MinimalSubProject.app("com.example.test")
        .appendToBuild("""
            android.defaultConfig.minSdkVersion  29
        """.trimIndent())
    val lib = MinimalSubProject.lib("com.example.lib")

    val testApp = MultiModuleTestProject.builder()
        .subproject(":app", app)
        .subproject(":lib", lib)
        .dependency(app, lib)
        .build()


    @Rule
    @JvmField
    val project =
        GradleTestProject.builder().fromTestApp(
            testApp
        ).create()

    @Test
    fun testIncrementalBuildWithFileDeletion() {
        val result = project.executor().run("assembleDebug")
        TestFileUtils.searchAndReplace(
            project.getSubproject("lib").buildFile,
            "com.example.lib",
            "com.example.lib.a")
        project.executor().run("assembleDebug")
        val transformedBinaries =
            project.getSubproject(":lib").buildDir.resolve(".transforms")

        val dexCount = transformedBinaries.walk(FileWalkDirection.TOP_DOWN).count {
            it.name == "BuildConfig.dex"
        }
        // there should only be 1 BuildConfig.dex file
        assertThat(dexCount == 1).isTrue()
    }
}
