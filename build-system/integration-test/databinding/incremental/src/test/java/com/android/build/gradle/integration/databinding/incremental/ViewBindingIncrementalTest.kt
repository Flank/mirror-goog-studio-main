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

package com.android.build.gradle.integration.databinding.incremental

import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.utils.IncrementalTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

/** Integration test to ensure correctness of incremental builds when view binding is used. */
class ViewBindingIncrementalTest {

    @get:Rule
    val project = EmptyActivityProjectBuilder().build()

    /** Regression test for bug 140955511. */
    @Test
    fun `test view binding disabled then enabled, expect merge-resources task to be out-of-date`() {
        IncrementalTestHelper(project, buildTask = ":app:compileDebugJavaWithJavac")
            .runFullBuild()
            .applyChange {
                // Enable view binding
                project.getSubproject("app").buildFile.appendText(
                    "\nandroid { buildFeatures { viewBinding = true } }")
            }
            .runIncrementalBuild()
            .assertTaskStates(
                expectedTaskStates = mapOf(
                    ":app:dataBindingMergeDependencyArtifactsDebug" to DID_WORK,
                    ":app:dataBindingMergeGenClassesDebug" to DID_WORK,
                    ":app:mergeDebugResources" to DID_WORK, // Regression test for bug 140955511
                    ":app:dataBindingGenBaseClassesDebug" to DID_WORK,
                    ":app:processDebugMainManifest" to DID_WORK,
                    ":app:processDebugResources" to DID_WORK,
                    ":app:compileDebugJavaWithJavac" to DID_WORK
                )
            )
    }
}