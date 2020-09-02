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

package com.android.build.api.extension

import com.android.build.api.component.Component
import org.gradle.api.Incubating

/**
 * Generic extension for Android Gradle Plugin related components.
 *
 * Each component has a type, like application or library and will have a dedicated extension with
 * methods that are related to the particular component type.
 *
 * @param VariantBuilderT the [Component] type produced by this variant.
 */
@Incubating
interface AndroidComponentsExtension<VariantBuilderT: Component> {

    /**
     * Creates a [VariantSelector] instance that can be configured
     * to reduce the set of [VariantBuilderT] instances participating in the [beforeVariants]
     * callback invocation.
     *
     * @return [VariantSelector] to select the variants of interest.
     */
    fun selector(): VariantSelector

    /**
     * Method to register a [callback] to be called with [VariantBuilderT] instances that
     * satisfies the [selector]. The [callback] will be called as soon as the [VariantBuilderT]
     * instance has been created but before any [com.android.build.api.artifact.Artifact] has been
     * determined, therefore the build flow can still be changed when the [callback] is invoked.
     *
     * Example without selection:
     * '''kotlin
     *  androidComponents {
     *      beforeVariants {
     *          println("Called with variant : ${'$'}name")
     *      }
     *  }
     * '''
     *
     * Example with selection:
     * '''kotlin
     *  androidComponents {
     *      val debug = selector().withBuildType("debug")
     *      beforeVariants(debug) {
     *          println("Called with variant : ${'$'}name")
     *      }
     *  }
     * '''
     *
     * @param selector [VariantSelector] instance to select which instance of [VariantBuilderT] are
     * of interest. By default, all instances are of interest.
     * @param callback lambda to be called with each instance of [VariantBuilderT] of interest.
     */
    fun beforeVariants(
            selector: VariantSelector = selector().all(),
            callback: (VariantBuilderT) -> Unit)
}
