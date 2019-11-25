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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.api.variant.impl.VariantOperations
import com.android.build.api.variant.impl.VariantScopeTransformers
import org.gradle.api.Action

/** Internal implementation of the 'new' DSL interface */
abstract class CommonExtensionImpl<VARIANT : Variant<VARIANT_PROPERTIES>, VARIANT_PROPERTIES : VariantProperties>
    : CommonExtension<VARIANT, VARIANT_PROPERTIES> {

    protected val variantOperations =
        VariantOperations<VARIANT>(VariantScopeTransformers.toVariant)
    protected val variantPropertiesOperations = VariantOperations<VARIANT_PROPERTIES>(
        VariantScopeTransformers.toVariantProperties
    )

    override fun onVariants(action: Action<VARIANT>) {
        variantOperations.actions.add(action)
    }

    override fun onVariants(action: (VARIANT) -> Unit) {
        variantOperations.actions.add(Action { action.invoke(it) } )
    }

    override fun onVariantProperties(action: Action<VARIANT_PROPERTIES>) {
        variantPropertiesOperations.actions.add(action)
    }

    override fun onVariantProperties(action: (VARIANT_PROPERTIES) -> Unit) {
        variantPropertiesOperations.actions.add(Action { action.invoke(it) } )
    }
}