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

import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.model.ReferenceModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the handling of build type and product flavor attributes of project dependencies
 * as passed to the IDE.
 */
class FlavouredDependencyModelTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {

        subProject(":lib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                productFlavors {
                    named("basic") { dimension = "model" }
                    named("pro") { dimension = "model" }
                    named("play") { dimension = "market" }
                    named("other") { dimension = "market" }
                }
            }
            dependencies {
                implementation(project(":lib2"))
            }
        }
        subProject("lib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                productFlavors {
                    named("basic") { dimension = "model" }
                    named("pro") { dimension = "model" }
                    named("play") { dimension = "market" }
                    named("other") { dimension = "market" }
                }
            }
            dependencies {
                implementation(project(":lib3"))
            }
        }
        subProject("lib3") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                productFlavors {
                    named("play") { dimension = "market" }
                    named("other") { dimension = "market" }
                }
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "basicPlayDebug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":lib1") },
            goldenFile = "VariantDependencies"
        )
    }
}
