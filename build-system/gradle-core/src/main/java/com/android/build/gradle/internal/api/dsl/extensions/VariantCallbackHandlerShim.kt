/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.extension.VariantCallbackHandler
import com.android.build.api.dsl.variant.Variant

class VariantCallbackHandlerShim<T: Variant>(private val internalObject: VariantCallbackHandler<T>)
    : VariantCallbackHandler<T> by internalObject {
    override fun withName(name: String): VariantCallbackHandler<T> {
        return VariantCallbackHandlerShim(internalObject.withName(name))
    }

    override fun <S : Variant> withType(variantClass: Class<S>): VariantCallbackHandler<S> {
        return VariantCallbackHandlerShim(internalObject.withType(variantClass))
    }

    override fun withBuildType(name: String): VariantCallbackHandler<T> {
        return VariantCallbackHandlerShim(internalObject.withBuildType(name))
    }

    override fun withProductFlavor(name: String): VariantCallbackHandler<T> {
        return VariantCallbackHandlerShim(internalObject.withProductFlavor(name))
    }
}