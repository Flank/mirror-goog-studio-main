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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.sdklib.AndroidVersion
import java.io.File

/**
 * Construct a [CxxAbiModel], careful to be lazy with module level fields.
 */
fun createCxxAbiModel(
    variant: CxxVariantModel,
    abi: Abi,
    global: GlobalScope,
    baseVariantData: BaseVariantData) : CxxAbiModel {
    return object : CxxAbiModel {
        override val variant = variant
        override val abi = abi
        override val abiPlatformVersion by lazy {
            val minSdkVersion =
                baseVariantData.variantConfiguration.mergedFlavor.minSdkVersion
            val version = if (minSdkVersion == null) {
                null
            } else{
                AndroidVersion(minSdkVersion.apiLevel, minSdkVersion.codename)
            }
            global
                .sdkComponents
                .ndkHandlerSupplier.get()
                .ndkPlatform
                .getOrThrow()
                .ndkInfo
                .findSuitablePlatformVersion(abi.tag, version)
        }
        override val cmake by lazy {
            if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                object : CxxCmakeAbiModel {
                    override val cmakeWrappingBaseFolder: File
                        get() = gradleBuildOutputFolder
                }
            } else {
                null
            }
        }
    }
}

