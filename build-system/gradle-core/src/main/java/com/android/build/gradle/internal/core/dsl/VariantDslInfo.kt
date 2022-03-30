/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.gradle.internal.core.dsl

import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.PackagingOptions
import com.android.build.api.variant.ResValue
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.NativeBuiltType
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by main variants.
 */
interface VariantDslInfo: ComponentDslInfo, ConsumableComponentDslInfo {

    /** Returns true if the variant output is a bundle.  */
    val isBundled: Boolean

    val nativeBuildSystem: NativeBuiltType?

    val ndkConfig: MergedNdkConfig

    val externalNativeBuildOptions: CoreExternalNativeBuildOptions

    /**
     * Returns the ABI filters associated with the artifact, or empty set if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    val supportedAbis: Set<String>

    fun getProguardFiles(into: ListProperty<RegularFile>)

    val isRenderscriptDebuggable: Boolean

    val isJniDebuggable: Boolean

    val lintOptions: Lint

    val packaging: PackagingOptions

    ////////////////////////////////////////////////////////////////////////////////////////
    //  APIs below should only be used at CreationConfig/Variant instantiation time       //
    //  DO NOT USE THOSE IN TASKS                                                         //
    ////////////////////////////////////////////////////////////////////////////////////////

    val externalNativeExperimentalProperties: Map<String, Any>
}
