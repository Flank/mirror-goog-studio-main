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

package com.android.build.gradle.integration.analytics

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

class AnalyticsCompositeBuildTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestProject("multiCompositeBuild")
            .withDependencyChecker(false)
            .enableProfileOutput()
            .create()

    // regression test for b/226095015
    @Test
    fun testFetchNativeModelInCompositeBuild() {
        // This is to mimic what tooling api does during a sync which is to add
        // prepareKotlinBuildScriptModel task to the root project.
        project.projectDir.resolve("TestCompositeApp/app/build.gradle").appendText(
            """
                def tasks = gradle.startParameter.taskNames.toSet()
                tasks.add("prepareKotlinBuildScriptModel")
                gradle.startParameter.setTaskNames(tasks)
            """.trimIndent()
        )

        // This is to make the main build(TestCompositeApp) gets configured before one of its
        // included build(TestCompositeLib1) to trigger the issue of b/226095015
        TestFileUtils.searchAndReplace(
            project.projectDir.resolve("TestCompositeApp/settings.gradle"),
            "includeBuild '../TestCompositeLib1'",
            """
                includeBuild('../TestCompositeLib1') {
                    dependencySubstitution {
                        substitute(module("com.test.composite:composite1")).using(project(":composite1"))
                    }
                }
            """.trimIndent()
        )

        // Only fetching the native model will trigger this issue.
        project.getSubproject("TestCompositeApp")
            .modelV2()
            .ignoreSyncIssues()
            .fetchNativeModules(ModelBuilderV2.NativeModuleParams())
    }
}
