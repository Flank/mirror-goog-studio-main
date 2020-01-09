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
import com.android.build.api.variant.VariantConfiguration
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.Action

/**
 * Contains a list of registered [Action] on an instance of [VariantObjectT] plus services like
 * executing these actions.
 *
 * @param VariantObjectT is either a [com.android.build.api.variant.Variant] or
 * [com.android.build.api.variant.VariantProperties]
 */
class VariantOperations<VariantObjectT> where VariantObjectT: ActionableVariantObject, VariantObjectT: VariantConfiguration {
    val actions= mutableListOf<Action<VariantObjectT>>()
    private val filteredActions= mutableListOf<FilteredVariantOperation<VariantObjectT>>()

    fun addFilteredAction(action: FilteredVariantOperation<out VariantObjectT>) {
        @Suppress("UNCHECKED_CAST")
        filteredActions.add(action as FilteredVariantOperation<VariantObjectT>)
    }

    fun executeActions(variant: VariantObjectT) {
        actions.forEach { action -> action.execute(variant) }

        filteredActions.forEach {
            if (it.specificType.isInstance(variant)) {
                val castedVariant = it.specificType.cast(variant)
                it.executeFor(castedVariant)
            }
        }
    }
}

