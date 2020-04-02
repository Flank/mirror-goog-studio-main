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

import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantProperties
import org.gradle.api.Incubating

/**
 * Extension for the Android Test Gradle Plugin.
 *
 *
 * Only the Android Gradle Plugin should create instances of this interface.
 */
@Incubating
interface TestExtension<
        AaptOptionsT : AaptOptions,
        AbiSplitT : AbiSplit,
        AdbOptionsT : AdbOptions,
        AndroidSourceSetT : AndroidSourceSet,
        AnnotationProcessorOptionsT : AnnotationProcessorOptions,
        BuildTypeT : BuildType<AnnotationProcessorOptionsT, SigningConfigT>,
        CMakeT : Cmake,
        CompileOptionsT : CompileOptions,
        DataBindingT : DataBinding,
        DefaultConfigT : DefaultConfig<AnnotationProcessorOptionsT, SigningConfigT>,
        DensitySplitT : DensitySplit,
        ExternalNativeBuildT : ExternalNativeBuild<CMakeT, NdkBuildT>,
        JacocoOptionsT : JacocoOptions,
        LintOptionsT : LintOptions,
        NdkBuildT : NdkBuild,
        PackagingOptionsT : PackagingOptions,
        ProductFlavorT : ProductFlavor<AnnotationProcessorOptionsT, SigningConfigT>,
        SigningConfigT : SigningConfig,
        SplitsT : Splits<AbiSplitT, DensitySplitT>,
        TestOptionsT : TestOptions<UnitTestOptionsT>,
        UnitTestOptionsT : UnitTestOptions> :
    CommonExtension<
            AaptOptionsT,
            AbiSplitT,
            AdbOptionsT,
            AndroidSourceSetT,
            AnnotationProcessorOptionsT,
            TestBuildFeatures,
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
            UnitTestOptionsT,
            TestVariant<TestVariantProperties>,
            TestVariantProperties> {
    // TODO(b/140406102)
    /**
     * The Gradle path of the project that this test project tests.
     */
    var targetProjectPath: String?

}
