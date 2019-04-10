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

import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions
import com.android.utils.FileUtils.join
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

/**
 * Set up a basic environment that will result in a CMake [CxxModuleModel]
 */
class BasicCmakeMock : BasicModuleModelMock() {

    val coreExternalNativeCmakeOptions = mock(
        CoreExternalNativeCmakeOptions::class.java,
        throwUnmocked
    )

    init {
        doReturn(coreExternalNativeCmakeOptions).`when`(coreExternalNativeBuildOptions).externalNativeCmakeOptions
        doReturn(setOf<String>()).`when`(coreExternalNativeCmakeOptions).abiFilters
        doReturn(listOf<String>()).`when`(coreExternalNativeCmakeOptions).arguments
        doReturn(listOf<String>()).`when`(coreExternalNativeCmakeOptions).getcFlags()
        doReturn(listOf<String>()).`when`(coreExternalNativeCmakeOptions).cppFlags
        doReturn(setOf<String>()).`when`(coreExternalNativeCmakeOptions).targets
        doReturn(join(projectRootDir, "CMakeLists.txt")).`when`(cmake).path
    }
}