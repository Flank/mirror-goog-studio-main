/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the ,License,);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an ,AS IS, BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.options

import org.junit.Test

/** Tests for [BooleanOption]. */
class BooleanOptionTest {

    @Test
    fun `check Boolean options are put in correct order`() {
        // Expected order of Boolean options
        val order = listOf(
                ApiStage.Stable::class.java,
                FeatureStage.Supported::class.java,
                ApiStage.Experimental::class.java,
                FeatureStage.Experimental::class.java,
                FeatureStage.SoftlyEnforced::class.java,
                ApiStage.Deprecated::class.java,
                FeatureStage.Deprecated::class.java,
                FeatureStage.Enforced::class.java,
                ApiStage.Removed::class.java,
                FeatureStage.Removed::class.java
        )

        val options = BooleanOption.values()
        for (index in 1 until options.size) {
            val currentOption = options[index]
            val previousOption = options[index - 1]
            assert(order.indexOf(currentOption.stage.javaClass) >= order.indexOf(previousOption.stage.javaClass)) {
                "Boolean option `${previousOption.name}` with stage `${previousOption.stage.javaClass.name}`" +
                        " should be positioned after Boolean option `${currentOption.name}` with stage `${currentOption.stage.javaClass.name}`." +
                        " Rearrange their positions to put them in the correct groups."
            }
        }
    }

    @Test
    fun `check features are not in SUPPORTED stage`() {
        // Ignore working-as-intended options (or those that we postpone fixing)
        val ignoreList = listOf(
            BooleanOption.ENABLE_SDK_DOWNLOAD,
            BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES,
            BooleanOption.FORCE_JACOCO_OUT_OF_PROCESS,
            BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM,
            BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES,
            BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS,
            BooleanOption.ENABLE_LEGACY_API,
            BooleanOption.FULL_R8,
        )

        val violatingOptions = BooleanOption.values().filter {
            it.stage is FeatureStage.Supported
        }
        checkViolatingProjectOptions(
            violatingOptions = violatingOptions,
            ignoreList = ignoreList,
            requirement = "Features should not be in `FeatureStage.Supported` stage."
        )
    }

    @Test
    fun `check softly-enforced, enforced features have default value 'true'`() {
        val violatingOptions = BooleanOption.values().filter {
            (it.stage is FeatureStage.SoftlyEnforced || it.stage is FeatureStage.Enforced)
                    && !it.defaultValue
        }
        checkViolatingProjectOptions(
                violatingOptions = violatingOptions,
                requirement = "Softly-enforced or enforced features must have default value `true`."
        )
    }

    @Test
    fun `check experimental, deprecated, removed features have default value 'false'`() {
        // Ignore working-as-intended options (or those that we postpone fixing)
        val ignoreList = listOf(
                BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT,
                BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES,
                BooleanOption.ENABLE_EXTRACT_ANNOTATIONS,
                BooleanOption.ENABLE_AAPT2_WORKER_ACTIONS,
                BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES,
                BooleanOption.BUILD_ONLY_TARGET_ABI,
                BooleanOption.ENABLE_SIDE_BY_SIDE_CMAKE,
                BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION,
                BooleanOption.USE_DEPENDENCY_CONSTRAINTS,
                BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK,
                BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM,
                BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS,
                BooleanOption.MINIMAL_KEEP_RULES,
                BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES,
                BooleanOption.ENABLE_DESUGAR,
                BooleanOption.RUN_LINT_IN_PROCESS
        )

        val violatingOptions = BooleanOption.values().filter {
            (it.stage == FeatureStage.Experimental || it.stage is FeatureStage.Deprecated || it.stage is FeatureStage.Removed)
                    && it.defaultValue
        }
        checkViolatingProjectOptions(
                violatingOptions = violatingOptions,
                ignoreList = ignoreList,
                requirement = "Experimental, deprecated, or removed features must have default value `false`."
        )
    }
}
