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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Basic integration tests for ViewBinding. */
class ViewBindingBasicTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("databindingMultiModule")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            "android.buildFeatures.viewBinding = true"
        )
    }

    // Regression test for http://issuetracker.google.com/132637061
    @Test
    fun `check that layouts from local or remote libraries are not reprocessed`() {
        project.executor().run(":app:compileDebugJavaWithJavac")

        // Check that only the layout from the current app subproject is processed
        val generatedClassDir =
            InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT.getOutputDir(
                project.getSubproject(":app").buildDir
            )
        val layoutBindingFiles = File(
            generatedClassDir,
            "debug/out/android/databinding/multimodule/app/databinding"
        ).listFiles()
        Truth.assertThat(layoutBindingFiles!!).hasLength(1)
        Truth.assertThat(layoutBindingFiles[0].name).isEqualTo("AppLayoutBinding.java")
    }
    @Test
    fun `check non-transitive R classes`() {
        project.executor()
                .with(BooleanOption.NON_TRANSITIVE_R_CLASS, true)
                .run("clean", ":app:assembleDebug")
    }
}
