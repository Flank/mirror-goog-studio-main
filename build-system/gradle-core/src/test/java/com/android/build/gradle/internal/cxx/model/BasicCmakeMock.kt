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
import com.android.build.gradle.internal.dsl.ExternalNativeCmakeOptions
import com.android.utils.FileUtils.join
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

/**
 * Set up a basic environment that will result in a CMake [CxxModuleModel]
 */
open class BasicCmakeMock : BasicModuleModelMock() {

    // Walk all vals in the model and invoke them
    val module by lazy { tryCreateCxxModuleModel(global, cmakeFinder)!! }
    val variant by lazy { createCxxVariantModel(module, variantScope) }
    val abi by lazy { createCxxAbiModel(variant, Abi.X86, global, baseVariantData) }

    private val externalNativeCmakeOptions: ExternalNativeCmakeOptions = mock(
        ExternalNativeCmakeOptions::class.java,
        throwUnmocked
    )

    init {
        doReturn(externalNativeCmakeOptions).`when`(coreExternalNativeBuildOptions).externalNativeCmakeOptions
        doReturn(setOf<String>()).`when`(externalNativeCmakeOptions).abiFilters
        doReturn(listOf("-DCMAKE_ARG=1")).`when`(externalNativeCmakeOptions).arguments
        doReturn(listOf("-DC_FLAG_DEFINED")).`when`(externalNativeCmakeOptions).getcFlags()
        doReturn(listOf("-DCPP_FLAG_DEFINED")).`when`(externalNativeCmakeOptions).cppFlags
        doReturn(setOf<String>()).`when`(externalNativeCmakeOptions).targets
        val makefile = join(allPlatformsProjectRootDir, "CMakeLists.txt")
        doReturn(makefile).`when`(cmake).path
        projectRootDir.mkdirs()
        makefile.writeText("# written by ${BasicCmakeMock::class}")
        // Create the ninja executable files so that the macro expansion can succeed
        module.cmake!!.cmakeExe.parentFile.apply { mkdirs() }.apply {
            resolve("ninja").writeText("whatever")
            resolve("ninja.exe").writeText("whatever")
        }
    }
}
