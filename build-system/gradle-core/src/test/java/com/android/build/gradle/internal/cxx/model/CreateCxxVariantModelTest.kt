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


import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class CreateCxxVariantModelTest {

    @Test
    fun `simple variant does not throw exception`() {
        BasicCmakeMock().let {
            val module = tryCreateCxxModuleModel(it.global)!!
            createCxxVariantModel(
                module,
                it.baseVariantData
            )
        }
    }

    @Test
    fun `fully exercise variant model and check invariants`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(module, it.baseVariantData)
            CxxVariantModel::class.java.methods.toList().onEach { method ->
                val result = method.invoke(variant)
                if (result is File) {
                    if (result.path.contains("/build/")) {
                        assertThat(result.path)
                            .named("Path for CxxVariantModel::${method.name} " +
                                    "should contain intermediates/BuildSystem/Variant")
                            .contains("/intermediates/cmake/debug/")
                    }
                    if (result.path.contains(".cxx/cxx/")) {
                        assertThat(result.path)
                            .named("Path for CxxVariantModel::${method.name} " +
                                    "should contain .cxx/BuildSystem/Variant")
                            .contains("/.cxx/cxx/debug")
                    }
                    if (result.path.contains(".cxx/cmake/")) {
                        assertThat(result.path)
                            .named("Path for CxxVariantModel::${method.name} " +
                                    "should contain .cxx/BuildSystem/Variant")
                            .contains("/.cxx/cmake/debug")
                    }
                }
            }
        }
    }
}