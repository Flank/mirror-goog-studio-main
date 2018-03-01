/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.builder.model.AndroidProject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class BundleTest {

    private val app = MinimalSubProject.app("com.example.app")
        .appendToBuild(
            """dependencies { feature project(':feature1') }""")

    private val feature1 = MinimalSubProject.dynamicFeature("com.example.opt")
        .appendToBuild(
            """dependencies { implementation project(':app') }""")

    private val testApp = MultiModuleTestProject.builder()
            .subproject(":app", app)
            .subproject(":feature1", feature1)
            .build()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    @Throws(IOException::class)
    fun testModel() {
        val rootBuildModelMap = project.model().fetchAndroidProjects().rootBuildModelMap

        val appModel = rootBuildModelMap[":app"]
        Truth.assertThat(appModel).named("app model").isNotNull()
        Truth.assertThat(appModel!!.dynamicFeatures)
            .named("feature list in app model")
            .containsExactly(":feature1")

        val featureModel = rootBuildModelMap[":feature1"]
        Truth.assertThat(featureModel).named("feature model").isNotNull()
        Truth.assertThat(featureModel!!.projectType)
            .named("feature model type")
            .isEqualTo(AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE)
    }
}
