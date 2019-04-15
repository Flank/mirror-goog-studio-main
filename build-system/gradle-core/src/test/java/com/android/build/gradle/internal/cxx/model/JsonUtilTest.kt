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
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.repository.Revision
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class JsonUtilTest {
    @Test
    fun `round trip`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(
                module,
                it.baseVariantData
            )
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                it.global,
                it.baseVariantData
            )
            val json = abi.toJsonString()
            val writtenBackAbi = createCxxAbiModelFromJson(json)
            val writtenBackJson = writtenBackAbi.toJsonString()
            assertThat(json).isEqualTo(writtenBackJson)
            assertThat(writtenBackAbi.variant.module.cxxFolder.path).endsWith(".cxx")
            assertThat(writtenBackAbi.variant.module.ndkVersion.toString()).isEqualTo("19.2.3")
        }
    }
}
