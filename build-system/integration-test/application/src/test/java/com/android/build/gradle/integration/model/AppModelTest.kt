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
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Rule
import org.junit.Test

class HelloWorldAppModelTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }

    @Test
    fun `test VariantDependencies model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchVariantDependencies("debug")

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "VariantDependencies"
        )
    }
}

class ApplicationIdInAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                applicationId = "customized.application.id"
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}

class DisabledAidlInAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                buildFeatures {
                    aidl = false
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}
class DisabledRenderScriptInAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                buildFeatures {
                    renderScript = false
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}

class DisabledResValuesInAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                buildFeatures {
                    resValues = false
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}

class DisabledShadersInAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                buildFeatures {
                    shaders = false
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}

class DisabledBuildConfigInAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                buildFeatures {
                    buildConfig = false
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}

class EnabledMlModelBindingInAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                buildFeatures {
                    mlModelBinding = true
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}

class DefaultBuildTypeAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                buildTypes {
                    named("debug") {
                        isDefault = true
                    }
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}

class DefaultFlavorAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        configureRoot {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()

                productFlavors {
                    named("flavorA") {
                        dimension = "foo"
                        isDefault = true
                    }
                    named("flavorB") {
                        dimension = "foo"
                    }
                }
            }
        }
    }

    @Test
    fun `test AndroidProject model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()

        with(result).compare(
            model = result.container.singleModel,
            goldenFile = "AndroidProject"
        )
    }
}
