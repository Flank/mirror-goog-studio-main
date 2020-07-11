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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.model.dump
import com.android.build.gradle.integration.common.utils.goldenFile
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class AppModelTest {

    @get:Rule
    val project = builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun `default AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        Truth.assertWithMessage("Dumped AndroidProject (full version in stdout)")
            .that(result.container.singleModel.dump(result.normalizer))
            .isEqualTo(goldenFile("Default_AndroidProject_Model"))
    }

    @Test
    fun `default VariantDependencies model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchVariantDependencies("debug")

        Truth.assertWithMessage("Dumped VariantDependencies(debug) (full version in stdout)")
            .that(result.container.singleModel.dump(result.normalizer, result.container))
            .isEqualTo(goldenFile("Default_VariantDependencies_Model"))
    }
}