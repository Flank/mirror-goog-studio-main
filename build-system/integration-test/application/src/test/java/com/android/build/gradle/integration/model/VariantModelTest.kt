/*
 * Copyright (C) 2021 The Android Open Source Project
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

class FlavoredAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("basic") { dimension = "model" }
                    named("pro") { dimension = "model" }
                }
            }

            // we don't need all the variants. We just want to see the content of one variant in
            // the model, so we disable the rest
            androidComponents {
                disableVariant("basicDebug")
                disableVariant("basicRelease")
                disableVariant("proRelease")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareBasicAndroidProject(goldenFile = "BasicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "AndroidProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
    }
}

class MultiFlavoredAppModelTest: ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("basic") { dimension = "model" }
                    named("pro") { dimension = "model" }
                    named("play") { dimension = "market" }
                    named("other") { dimension = "market" }
                }
            }

            // we don't need all the variants. We just want to see the content of one variant in
            // the model, so we disable the rest
            androidComponents {
                disableVariant("basicPlayDebug")
                disableVariant("proOtherDebug")
                disableVariant("basicOtherDebug")
                disableVariant("proPlayRelease")
                disableVariant("basicPlayRelease")
                disableVariant("proOtherRelease")
                disableVariant("basicOtherRelease")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareBasicAndroidProject(goldenFile = "BasicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "AndroidProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
    }
}

/**
 * This test disables the debug variant.
 */
class DisabledVariantInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("debug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables the only AndroidTest component of the project (debug)
 */
class DisabledAndroidTestInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableAndroidTest("debug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables the debug UnitTest component of the project (There is still the release
 * one)
 */
class DisabledUnitTestInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableUnitTest("debug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables a single variant in a 4 variant setup (2 build types, 2 flavors)
 */
class DisabledSingleVariantInFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("one") {
                        dimension = "foo"
                    }
                    named("two") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("oneDebug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables  variants in a 4 variant setup (2 build types, 2 flavors) by the build type
 * (ie all variant of build type debug)
 */
class DisabledVariantByBuildTypeInFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("one") {
                        dimension = "foo"
                    }
                    named("two") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("oneDebug")
                disableVariant("twoDebug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables  variants in a 4 variant setup (2 build types, 2 flavors) by the flavor
 * (ie all variant of flavor "one")
 */
class DisabledVariantByFlavorInFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("one") {
                        dimension = "foo"
                    }
                    named("two") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("oneDebug")
                disableVariant("oneRelease")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}


class NoVariantModelTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
            }
            androidComponents {
                disableVariant("debug")
                disableVariant("release")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels()

        with(result).compareBasicAndroidProject(goldenFile = "BasicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "AndroidProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
    }
}
