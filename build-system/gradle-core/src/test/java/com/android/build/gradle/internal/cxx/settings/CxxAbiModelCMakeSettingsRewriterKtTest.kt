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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.CmakeSettingsMock
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.toJsonString
import com.google.common.truth.Truth.assertThat

import org.junit.Test

class CxxAbiModelCMakeSettingsRewriterKtTest {

    @Test
    fun `ensure traditional environment rewrite is nop`() {
        BasicCmakeMock().apply {
            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()
            assertThat(abi.toJsonString()).isEqualTo(rewritten.toJsonString())
        }
    }

    @Test
    fun `check rewrite with CMakeSettings json`() {
        CmakeSettingsMock().apply {
            val variant = object : CxxVariantModel by variant {
                override val cmakeSettingsConfiguration = DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
            }
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                global,
                baseVariantData)
            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()
            assertThat(rewritten.cmake!!.generator).isEqualTo("some other generator")
            assertThat(rewritten.cxxBuildFolder.path).contains("some other build root folder")
            assertThat(rewritten.variant.module.cmake!!.cmakeExe.path
                .replace('\\', '/')).isEqualTo("my/path/to/cmake")
            assertThat(rewritten.variant.module.cmakeToolchainFile.path
                .replace('\\', '/')).isEqualTo("my/path/to/toolchain")
        }
    }
}