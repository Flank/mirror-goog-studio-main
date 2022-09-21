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

import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.MavenRepoGenerator
import org.junit.Rule
import org.junit.Test

class JavaPlatformDependencyTest : ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            appendToBuildFile {
                """
                    dependencies {
                      implementation platform(project(":lib"))
                    }
                """.trimIndent()
            }
        }
        subProject(":lib") {
            plugins.add(PluginType.JAVA_PLATFORM)

            appendToBuildFile {
                """
                    javaPlatform {
                      allowDependencies()
                    }
                """.trimIndent()
            }
            dependencies {
                api(MavenRepoGenerator.Library("com.bar:foo:1.0"))
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        val appModelAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo =
            { getProject(":app") }

        with(result).compareVariantDependencies(
            projectAction = appModelAction,
            goldenFile = "_VariantDependencies"
        )
    }
}
