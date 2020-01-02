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
        AdbOptionsT : AdbOptions,
        BuildTypeT : BuildType,
        CMakeT : Cmake,
        CompileOptionsT : CompileOptions,
        DataBindingT : DataBinding,
        DefaultConfigT : DefaultConfig,
        ExternalNativeBuildT : ExternalNativeBuild<CMakeT, NdkBuildT>,
        JacocoOptionsT : JacocoOptions,
        NdkBuildT : NdkBuild,
        ProductFlavorT : ProductFlavor,
        SigningConfigT : SigningConfig,
        TestOptionsT : TestOptions<UnitTestOptionsT>,
        UnitTestOptionsT : UnitTestOptions> :
    CommonExtension<
            AaptOptionsT,
            AdbOptionsT,
            TestBuildFeatures,
            BuildTypeT,
            CMakeT,
            CompileOptionsT,
            DataBindingT,
            DefaultConfigT,
            ExternalNativeBuildT,
            JacocoOptionsT,
            NdkBuildT,
            ProductFlavorT,
            SigningConfigT,
            TestOptionsT,
            UnitTestOptionsT,
            TestVariant,
            TestVariantProperties> {
    // TODO(b/140406102)
}
