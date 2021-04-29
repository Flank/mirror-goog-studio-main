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
import com.android.build.gradle.internal.cxx.model.buildIsPrefabCapable
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import org.junit.Test

class BuiltInSettingsJsonKtTest {


    @Test
    fun `NDK-level CMakeSettings does not throw exception when evaluated`() {
        BasicCmakeMock().let {
            val module = createCxxModuleModel(
                it.sdkComponents,
                it.androidLocationProvider,
                it.configurationParameters)
            val variant = createCxxVariantModel(
                it.configurationParameters,
                module)
            val abi = createCxxAbiModel(
                it.sdkComponents,
                it.configurationParameters,
                variant,
                Abi.X86)
            abi.getNdkMetaSettingsJson().toJsonString()
        }
    }

    @Test
    fun `Gradle-level CMakeSettings does not throw exception when evaluated`() {
        BasicCmakeMock().apply {
            abi.getAndroidGradleSettings().toJsonString()
        }
    }

    @Test
    fun `Traditional CMakeSettings does not throw when evaluated`() {
        BasicCmakeMock().apply {
            getCmakeDefaultEnvironment(abi.buildIsPrefabCapable()).toJsonString()
        }
    }
}
