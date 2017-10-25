/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.Initializable
import org.gradle.api.Incubating

/**
 * DSL object for configuring per-language splits options.
 *
 *
 * See [APK
 * Splits](https://developer.android.com/studio/build/configure-apk-splits.html).
 */
@Incubating
interface LanguageSplitOptions : Initializable<LanguageSplitOptions> {

    /** Collection of include patterns.  */
    val include: Set<String>

    val applicationFilters: Set<String>

    /** Returns true if splits should be generated for languages.  */
    /** enables or disables splits for language  */
    var enabled: Boolean

    /**
     * Sets whether the build system should automatically determine the splits based on the
     * "language-*" folders in the resources.
     */
    var auto: Boolean
}
