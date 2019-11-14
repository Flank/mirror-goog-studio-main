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

import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantConfiguration
import com.android.build.api.variant.VariantProperties
import org.gradle.api.Action
import java.lang.Boolean.TRUE

open class VariantImpl<T: VariantProperties>(variantConfiguration: VariantConfiguration):
    Variant<T>, VariantConfiguration by variantConfiguration {

    private val actions = DelayedActionExecutor<T>()

    override fun onProperties(action: Action<T>) {
        actions.registerAction(action)
    }

    fun executeActions(target: T) {
        actions.executeActions(target)
    }

    override var enabled: Boolean = TRUE
}
