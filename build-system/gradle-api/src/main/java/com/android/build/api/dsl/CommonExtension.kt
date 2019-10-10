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

package com.android.build.api.dsl

import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * Common extension properties for the Android Application. Library and Dynamic Feature Plugins.
 *
 *
 * Only the Android Gradle Plugin should create instances of this interface.
 */
@Incubating
interface CommonExtension<VariantT: Variant<VariantPropertiesT>, VariantPropertiesT: VariantProperties> {
    /**
     * Registers an [Action] to be executed on each [Variant] of the project.
     *
     * @param action an [Action] taking a [Variant] as a parameter.
     */
    fun onVariants(action: Action<VariantT>)

    /**
     * Registers an [Action] to be executed on each [VariantProperties] of the project.
     *
     * @param action an [Action] taking a [VariantProperties] as a parameter.
     */
    fun onVariantsProperties(action: Action<VariantPropertiesT>)

    // TODO(b/140406102)
}
