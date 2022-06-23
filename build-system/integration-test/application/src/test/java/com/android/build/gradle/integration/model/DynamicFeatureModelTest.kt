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

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class HelloWorldDynamicFeatureModelTest : ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                dynamicFeatures += listOf(":feature")
            }
        }
        subProject(":feature") {
            plugins.add(PluginType.ANDROID_DYNAMIC_FEATURE)
            android {
                setUpHelloWorld()
            }
            dependencies {
                implementation(project(":app"))
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

        with(result).compareAndroidProject(
            projectAction = appModelAction,
            goldenFile = "_app_AndroidProject"
        )
        with(result).compareVariantDependencies(
            projectAction = appModelAction,
            goldenFile = "_app_VariantDependencies"
        )

        val featureModelAction:  ModelContainerV2.() -> ModelContainerV2.ModelInfo =
            { getProject(":feature") }

        with(result).compareAndroidProject(
            projectAction = featureModelAction,
            goldenFile = "_feature_AndroidProject"
        )
        with(result).compareVariantDependencies(
            projectAction = featureModelAction,
            goldenFile = "_feature_" +
                    "VariantDependencies"
        )
    }
}

class CompileSdkViaSettingsInDynamicFeatureModelTest {
    @get:Rule
    val project = createGradleProject {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                compileSdk = DEFAULT_COMPILE_SDK_VERSION
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_DYNAMIC_FEATURE)
            android {
                setUpHelloWorld(setupDefaultCompileSdk = false)
            }
        }
    }

    @Test
    fun `test compileTarget`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        val androidDsl = result.container.getProject().androidDsl
            ?: throw RuntimeException("Failed to get AndroidDsl Model")
        Truth
            .assertWithMessage("compile target hash")
            .that(androidDsl.compileTarget)
            .isEqualTo("android-$DEFAULT_COMPILE_SDK_VERSION")
    }
}

class MinSdkViaSettingsInDynamicFeatureModelTest {
    @get:Rule
    val project = createGradleProject {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                minSdk = 23
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_DYNAMIC_FEATURE)
            android {
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun `test minSdkVersion`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        val androidDsl = result.container.getProject().androidDsl
            ?: throw RuntimeException("Failed to get AndroidDsl Model")

        Truth
            .assertWithMessage("minSdkVersion")
            .that(androidDsl.defaultConfig.minSdkVersion)
            .isNotNull()

        Truth
            .assertWithMessage("minSdkVersion.apiLevel")
            .that(androidDsl.defaultConfig.minSdkVersion?.apiLevel)
            .isEqualTo(23)

        Truth
            .assertWithMessage("minSdkVersion.codename")
            .that(androidDsl.defaultConfig.minSdkVersion?.codename)
            .isNull()
    }
}
