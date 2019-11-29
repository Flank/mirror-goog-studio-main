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
import org.gradle.api.Action

/**
 * Contains a list of registered [Action] on an instance of [VariantObjectT] plus services like
 * executing these actions.
 *
 * @param VariantObjectT is either a [com.android.build.api.variant.Variant] or
 * [com.android.build.api.variant.VariantProperties]´
 * @param transformer the function to transform a [VariantScope] into an instance of [VariantObjectT]
 */
class VariantOperations<VariantObjectT: ActionableVariantObject>(
    val transformer: VariantScopeTransformers
) {
    val actions= mutableListOf<Action<VariantObjectT>>()
    val filteredActions= mutableListOf<FilteredVariantOperation<out VariantObjectT>>()

    fun addFilteredAction(action: FilteredVariantOperation<out VariantObjectT>) {
        filteredActions.add(action)
    }

    /**
     * Executes all registered actions provided the list of [VariantScope].
     *
     * @param variantScopes instances of [VariantScope] to get instances of [VariantObjectT] from.
     */
    inline fun <reified U : VariantObjectT> executeOperations(variantScopes: List<VariantScope>) {
        variantScopes.forEach { variantScope ->
            val variantObject = transformer.transform(variantScope, U::class.java)
            if (variantObject != null) {
                actions.forEach { action -> action.execute(variantObject) }
            }
        }

        filteredActions.forEach { filteredAction ->
            filteredAction.execute(transformer, variantScopes)
        }
    }
}

