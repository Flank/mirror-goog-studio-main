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

        val options =
            BooleanOption::class.java.declaredFields
                .filter { it.type == BooleanOption::class.java }
                .map { it.get(BooleanOption::class.java) as BooleanOption }
        var lastOption: BooleanOption? = null
        options.forEach {
            if (lastOption != null
                && order.indexOf(it.stage.javaClass) < order.indexOf(lastOption!!.stage.javaClass)
            ) {
                fail(
                    "Boolean option `${lastOption!!.name}` with stage `${lastOption!!.stage.javaClass.name}`" +
                            " should be positioned after Boolean option `${it.name}` with stage `${it.stage.javaClass.name}`." +
                            " Rearrange their positions to put them in the correct groups."
                )
            }
            lastOption = it
        }
    }
}