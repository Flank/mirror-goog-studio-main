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

import com.android.build.api.component.Component
import com.android.build.api.component.ComponentProperties
import com.android.build.api.extension.AndroidComponentsExtension
import com.android.build.api.extension.VariantSelector
import com.android.build.gradle.internal.services.DslServices

abstract class AndroidComponentsExtensionImpl<VariantBuilderT: Component<out ComponentProperties>>(
        private val dslServices: DslServices,
        private val operations: OperationsRegistrar<VariantBuilderT>
): AndroidComponentsExtension<VariantBuilderT> {

    override fun beforeVariants(selector: VariantSelector, callback: VariantBuilderT.() -> Unit) {
        operations.addOperation(selector, callback)
    }

    override fun selector(): VariantSelectorImpl =
            dslServices.newInstance(VariantSelectorImpl::class.java)
}
