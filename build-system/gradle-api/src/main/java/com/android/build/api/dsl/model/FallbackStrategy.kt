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

package com.android.build.api.dsl.model

interface FallbackStrategy {

    /**
     * Fall-backs to use during variant-aware dependency resolution in case a dependency does not
     * have the current product flavor.
     *
     * The order of the fallbacks matters. Earlier items in the list are considered first.
     */
    var matchingFallbacks: MutableList<String>

    @Deprecated("Use matchingFallbacks property")
    fun setMatchingFallbacks(vararg fallbacks: String)

    @Deprecated("Use matchingFallbacks property")
    fun setMatchingFallbacks(fallback: String)
}
