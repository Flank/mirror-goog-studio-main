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

open class VariantSelectorImpl: VariantSelector {

    // TODO: implement this.
    // so far return this, implementation in a subsequent CL.
    override fun all(): VariantSelector = this

    // TODO: implement this.
    fun <VariantBuilderT> appliesTo(variant: VariantBuilderT): Boolean {
        return true;
    }
}