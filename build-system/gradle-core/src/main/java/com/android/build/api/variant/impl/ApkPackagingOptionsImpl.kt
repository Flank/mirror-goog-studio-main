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

package com.android.build.api.variant.impl

import com.android.build.api.variant.ApkPackagingOptions
import com.android.build.api.variant.DexPackagingOptions
import com.android.build.api.variant.JniLibsApkPackagingOptions
import com.android.build.gradle.internal.services.VariantPropertiesApiServices

class ApkPackagingOptionsImpl(
    dslPackagingOptions: com.android.build.gradle.internal.dsl.PackagingOptions,
    variantPropertiesApiServices: VariantPropertiesApiServices,
    minSdk: Int
) : PackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices), ApkPackagingOptions {

    override val dex =
        DexPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, minSdk)

    override fun dex(action: DexPackagingOptions.() -> Unit) {
        action.invoke(dex)
    }

    override val jniLibs =
        JniLibsApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, minSdk)

    override fun jniLibs(action: JniLibsApkPackagingOptions.() -> Unit) {
        action.invoke(jniLibs)
    }

    override val resources =
        ResourcesApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)
}
