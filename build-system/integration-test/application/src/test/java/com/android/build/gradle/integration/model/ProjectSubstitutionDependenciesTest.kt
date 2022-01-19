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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.MavenRepoGenerator
import org.junit.Rule
import org.junit.Test

class ProjectSubstitutionDependenciesTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                runtimeOnly(MavenRepoGenerator.Library("com.example:lib:1.0"))
                implementation(MavenRepoGenerator.Library("com.example:lib2:1.0"))
            }
            appendToBuildFile {
                """
                    configurations.all {
                      if (name.contains("RuntimeClasspath")) {
                        resolutionStrategy.dependencySubstitution {
                          substitute module("com.example:lib:1.0") using project(":lib")
                          substitute module("com.example:lib2:1.0") using project(":lib2")
                        }
                      }
                    }
                """.trimIndent()
            }
        }
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
        }
        subProject(":lib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
        }
    }


    @Test
    fun checkAllDependencies() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":app") },
            goldenFile = "VariantDependencies"
        )
    }
}
