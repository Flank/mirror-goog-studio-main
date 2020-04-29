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

import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantProperties
import org.gradle.api.Incubating

/**
 * Extension for the Android Gradle Plugin Application plugin.
 *
 *
 * Only the Android Gradle Plugin should create instances of this interface.
 */
@Incubating
interface ApplicationExtension<
        AaptOptionsT : AaptOptions,
        AbiSplitT : AbiSplit,
        AdbOptionsT : AdbOptions,
        AndroidSourceSetT : AndroidSourceSet,
        BuildTypeT : ApplicationBuildType<SigningConfigT>,
        CMakeT : Cmake,
        CompileOptionsT : CompileOptions,
        DataBindingT : DataBinding,
        DefaultConfigT : ApplicationDefaultConfig<SigningConfigT>,
        DensitySplitT : DensitySplit,
        ExternalNativeBuildT : ExternalNativeBuild<CMakeT, NdkBuildT>,
        JacocoOptionsT : JacocoOptions,
        LintOptionsT : LintOptions,
        NdkBuildT : NdkBuild,
        PackagingOptionsT : PackagingOptions,
        ProductFlavorT : ApplicationProductFlavor<SigningConfigT>,
        SigningConfigT : SigningConfig,
        SplitsT : Splits<AbiSplitT, DensitySplitT>,
        TestOptionsT : TestOptions> :
    CommonExtension<
            AaptOptionsT,
            AbiSplitT,
            AdbOptionsT,
            AndroidSourceSetT,
            ApplicationBuildFeatures,
            BuildTypeT,
            CMakeT,
            CompileOptionsT,
            DataBindingT,
            DefaultConfigT,
            DensitySplitT,
            ExternalNativeBuildT,
            JacocoOptionsT,
            LintOptionsT,
            NdkBuildT,
            PackagingOptionsT,
            ProductFlavorT,
            SigningConfigT,
            SplitsT,
            TestOptionsT,
            ApplicationVariant<ApplicationVariantProperties>,
            ApplicationVariantProperties>,
    ApkExtension,
    TestedExtension {
    // TODO(b/140406102)

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    val dependenciesInfo: DependenciesInfo

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    fun dependenciesInfo(action: DependenciesInfo.() -> Unit)

    val bundle: Bundle

    fun bundle(action: Bundle.() -> Unit)

    var dynamicFeatures: MutableSet<String>

    /**
     * Set of asset pack subprojects to be included in the app's bundle.
     */
    var assetPacks: MutableSet<String>
}
