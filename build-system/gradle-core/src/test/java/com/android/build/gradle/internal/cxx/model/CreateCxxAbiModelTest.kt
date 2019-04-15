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
import com.android.repository.Revision
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class CreateCxxAbiModelTest {

    // This test is designed to throw an exception if any unexpected gradle object model functions
    // are called during construction. Everything is supposed to be lazy except for what's
    // defined here.
    @Test
    fun `abi, variant, and model are completely lazy`() {
        EmptyGlobalMock().let {
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(
                module,
                it.baseVariantData)
            createCxxAbiModel(variant, Abi.X86, it.global, it.baseVariantData)
        }
    }

    @Test
    fun `fully exercise model and check invariants`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(
                module,
                it.baseVariantData)
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                it.global,
                it.baseVariantData)
            CxxAbiModel::class.java.methods.toList().onEach { method ->
                val result = method.invoke(abi)
                if (result is File) {
                    assertThat(result.path.replace("\\", "/"))
                        .named("Paths in CxxAbiModel::${method.name} should contain variant")
                        .contains("/debug/")
                    assertThat(result.path.replace("\\", "/"))
                        .named("Paths in CxxAbiModel::${method.name} should contain ABI")
                        .contains("/x86")
                }
            }
        }
    }

    // Check some specific issues I had to debug
    @Test
    fun `repeated cmake bug`() {
        BasicCmakeMock().let {
            val module = tryCreateCxxModuleModel(it.global)!!
            val variant = createCxxVariantModel(
                module,
                it.baseVariantData)
            val abi = createCxxAbiModel(variant, Abi.X86, it.global, it.baseVariantData)
            assertThat(abi.cxxBuildFolder.path
                    .replace("\\", "/"))
                .endsWith(".cxx/cmake/debug/x86")
            assertThat(abi.gradleBuildOutputFolder.path
                    .replace("\\", "/"))
                .doesNotContain("cmake/debug/cxx/debug")
            assertThat(abi.cmake!!.buildGenerationStateFile.path!!
                    .replace("\\", "/"))
                .doesNotContain("cmake/cxx/x86")
            assertThat(abi.cmake!!.cmakeListsWrapperFile.path!!
                    .replace("\\", "/"))
                .endsWith(".cxx/cxx/debug/x86/CMakeLists.txt")
        }
    }
}
