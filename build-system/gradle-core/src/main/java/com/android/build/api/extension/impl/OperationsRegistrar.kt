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

package com.android.build.api.extension.impl

import com.android.build.api.extension.VariantSelector

/**
 * Registrar object to keep track of Variant API operations registered on the [VariantBuilderT]
 */
class OperationsRegistrar<VariantBuilderT> {

    private class Operation<VariantBuilderT>(
            val selector: VariantSelectorImpl,
            val callBack: (variantBuilder: VariantBuilderT) -> Unit
    )

    private val operations= mutableListOf<Operation<VariantBuilderT>>()

    fun addOperation(selector: VariantSelector, callback: VariantBuilderT.() -> Unit) {
        operations.add(Operation(selector as VariantSelectorImpl, callback))
    }

    fun executeOperations(variant: VariantBuilderT) {
        operations.forEach { operation ->
            if (operation.selector.appliesTo(variant)) {
                operation.callBack.invoke(variant)
            }
        }
    }
}
