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
import kotlin.test.fail

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
            if (order.indexOf(currentOption.stage.javaClass) < order.indexOf(previousOption.stage.javaClass)) {
                fail(
                    "Boolean option `${previousOption.name}` with stage `${previousOption.stage.javaClass.name}`" +
                            " should be positioned after Boolean option `${currentOption.name}` with stage `${currentOption.stage.javaClass.name}`." +
                            " Rearrange their positions to put them in the correct groups."
                )
            }
        }
    }

    @Test
    fun `check experimental features do not have default value 'true'`() {
        // Ignore known violating or working-as-intended features
        val ignoreList = setOf(
            BooleanOption.ENABLE_GRADLE_WORKERS,
            BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING,
            BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT,
            BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES,
            BooleanOption.ENABLE_EXTRACT_ANNOTATIONS,
            BooleanOption.ENABLE_AAPT2_WORKER_ACTIONS,
            BooleanOption.ENABLE_D8_DESUGARING,
            BooleanOption.ENABLE_R8_DESUGARING,
            BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES,
            BooleanOption.BUILD_ONLY_TARGET_ABI,
            BooleanOption.ENABLE_PARALLEL_NATIVE_JSON_GEN,
            BooleanOption.ENABLE_SIDE_BY_SIDE_CMAKE,
            BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION,
            BooleanOption.USE_DEPENDENCY_CONSTRAINTS,
            BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK,
            BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM,
            BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS,
            BooleanOption.MINIMAL_KEEP_RULES,
            BooleanOption.USE_NEW_JAR_CREATOR,
            BooleanOption.USE_NEW_APK_CREATOR,
            BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES
        )
        val violatingOptions = BooleanOption.values().filter {
            it.stage == FeatureStage.Experimental && it.defaultValue && it !in ignoreList
        }
        if (violatingOptions.isNotEmpty()) {
            fail(
                "The following experimental features have default value `true`: " + violatingOptions.joinToString(", ") + "\n" +
                        "When we change the default value of an EXPERIMENTAL feature from `false` to `true`, we should also change its stage to SUPPORTED or higher.\n" +
                        "Otherwise, the AGP would warn the users about using an experimental feature when they set the option to `false`, which is usually not intended.\n" +
                        "If it is actually intended, add the feature to the ignore list in this test."
            )
        }
    }
}