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
import com.android.build.gradle.internal.cxx.model.EmptyGlobalMock
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.tryCreateCxxModuleModel

import org.junit.Test

class BuiltInCmakeSettingsJsonKtTest {

    //@Test
    fun `NDK-level CMakeSettings is completely lazy`() {
        EmptyGlobalMock().let {
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(
                module,
                it.variant)
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                it.global,
                it.baseVariantData)
            abi.getNdkMetaCmakeSettingsJson()
        }
    }

    //@Test
    fun `Gradle-level CMakeSettings is completely lazy`() {
        EmptyGlobalMock().let {
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(
                module,
                it.variant)
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                it.global,
                it.baseVariantData)
            abi.getAndroidGradleCmakeSettings()
        }
    }

    @Test
    fun `NDK-level CMakeSettings does not throw exception when evaluated`() {
        BasicCmakeMock().let {
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(
                module,
                it.variantScope)
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                it.global,
                it.baseVariantData)
            abi.getNdkMetaCmakeSettingsJson().toJsonString()
        }
    }

    @Test
    fun `Gradle-level CMakeSettings does not throw exception when evaluated`() {
        BasicCmakeMock().apply {
            abi.getAndroidGradleCmakeSettings().toJsonString()
        }
    }

    @Test
    fun `Traditional CMakeSettings does not throw when evaluated`() {
        BasicCmakeMock().apply {
            abi.getCmakeServerDefaultEnvironment().toJsonString()
        }
    }
}