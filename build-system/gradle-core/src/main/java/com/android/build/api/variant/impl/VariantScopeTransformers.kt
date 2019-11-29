/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.variant.ActionableVariantObject
import com.android.build.gradle.internal.scope.VariantScope

/**
 * Functional interface to transform a [VariantScope] into an specific type instance.
 */
interface VariantScopeTransformers {
    /**
     * Transforms input [VariantScope] into an instance of provided type [U]
     *
     * @param variantScope input [VariantScope]
     * @param type the intended type for the returned instance.
     * @return U the returned instance of the provided [type]
     */
    fun <U: ActionableVariantObject> transform(variantScope: VariantScope, type: Class<U>): U?

    companion object {
        /**
         * Transforms from [VariantScope] into a subtype of [com.android.build.api.variant.Variant]
         */
        @JvmStatic
        val toVariant = object : VariantScopeTransformers {
            override fun <U: ActionableVariantObject> transform(variantScope: VariantScope, type: Class<U>): U? {
                return if (type.isInstance(variantScope.variantData.publicVariantApi))
                    type.cast(variantScope.variantData.publicVariantApi) else null
            }
        }
        /**
         * Transforms from [VariantScope] into a subtype of [com.android.build.api.variant.VariantProperties]
         */
        @JvmStatic
        val toVariantProperties = object : VariantScopeTransformers {
            override fun <U: ActionableVariantObject> transform(variantScope: VariantScope, type: Class<U>): U? {
                return if (type.isInstance(variantScope.variantData.publicVariantPropertiesApi))
                    type.cast(variantScope.variantData.publicVariantPropertiesApi) else null
            }
        }
    }
}