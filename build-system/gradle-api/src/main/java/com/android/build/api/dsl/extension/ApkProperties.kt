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

package com.android.build.api.dsl.extension

import com.android.build.api.dsl.options.PackagingOptions
import com.android.build.api.dsl.options.Splits
import org.gradle.api.Action
import org.gradle.api.Incubating

/** Partial extension properties for modules that generate APKs  */
@Incubating
interface ApkProperties {

    /** Packaging options.  */
    val packagingOptions: PackagingOptions

    fun packagingOptions(action: Action<PackagingOptions>)

    /**
     * APK splits options.
     *
     *
     * See [APK Splits](https://developer.android.com/studio/build/configure-apk-splits.html).
     */
    val splits: Splits

    fun splits(action: Action<Splits>)

    /** Whether to generate pure splits or multi apk.  */
    var generatePureSplits: Boolean
}
