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
import com.android.utils.FileUtils.join
import org.mockito.Mockito.doReturn

/**
 * Set up a basic environment that will result in a CMake [CxxModuleModel]
 */
open class BasicCmakeMock(createFakeNinja : Boolean = true) : BasicModuleModelMock() {

    // Walk all vals in the model and invoke them
    val module by lazy { createCxxModuleModel(sdkComponents, configurationParameters, cmakeFinder) }
    val variant by lazy { createCxxVariantModel(configurationParameters, module) }
    val abi by lazy { createCxxAbiModel(sdkComponents, configurationParameters, variant, Abi.X86) }

    init {
        doReturn(setOf<String>()).`when`(externalNativeCmakeOptions).abiFilters
        doReturn(listOf("-DCMAKE_ARG=1")).`when`(externalNativeCmakeOptions).arguments
        doReturn(listOf("-DC_FLAG_DEFINED")).`when`(externalNativeCmakeOptions).getcFlags()
        doReturn(listOf("-DCPP_FLAG_DEFINED")).`when`(externalNativeCmakeOptions).cppFlags
        doReturn(setOf<String>()).`when`(externalNativeCmakeOptions).targets
        val makefile = join(allPlatformsProjectRootDir, "CMakeLists.txt")
        doReturn(makefile).`when`(cmake).path
        projectRootDir.mkdirs()
        makefile.writeText("# written by ${BasicCmakeMock::class}")
        if (createFakeNinja) {
            // Create the ninja executable files so that the macro expansion can succeed
            cmakeDir.apply { mkdirs() }.apply {
                resolve("ninja").writeText("whatever")
                resolve("ninja.exe").writeText("whatever")
            }
        }
    }
}
