/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.utils.FileUtils
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.mockito.Mockito

/**
 * Set up a basic environment that will result in an ndk-build [CxxModuleModel]
 */
open class BasicNdkBuildMock : BasicModuleModelMock() {

    // Walk all vals in the model and invoke them
    val module by lazy {
        createCxxModuleModel(
            sdkComponents,
            androidLocationProvider,
            configurationParameters,
            cmakeFinder
        )
    }
    val variant by lazy { createCxxVariantModel(configurationParameters, module) }
    val abi by lazy { createCxxAbiModel(sdkComponents, configurationParameters, variant, Abi.X86) }

    init {
        Mockito.doReturn(makeSetProperty(setOf())).`when`(variantExternalNativeBuild).abiFilters
        Mockito.doReturn(makeListProperty(listOf("APP_STL=c++_shared"))).`when`(variantExternalNativeBuild).arguments
        Mockito.doReturn(makeListProperty(listOf("-DC_FLAG_DEFINED"))).`when`(variantExternalNativeBuild).cFlags
        Mockito.doReturn(makeListProperty(listOf("-DCPP_FLAG_DEFINED"))).`when`(variantExternalNativeBuild).cppFlags
        Mockito.doReturn(makeSetProperty(setOf())).`when`(variantExternalNativeBuild).targets
        val makefile = FileUtils.join(allPlatformsProjectRootDir, "Android.mk")
        Mockito.doReturn(makefile).`when`(ndkBuild).path
        projectRootDir.mkdirs()
        makefile.writeText("# written by ${BasicNdkBuildMock::class}")
    }

    private fun makeListProperty(values: List<String>): ListProperty<*> =
        Mockito.mock(ListProperty::class.java).also {
            Mockito.doReturn(values).`when`(it).get()
        }

    private fun makeSetProperty(values: Set<String>): SetProperty<*> =
            Mockito.mock(SetProperty::class.java).also {
                Mockito.doReturn(values).`when`(it).get()
            }

}
