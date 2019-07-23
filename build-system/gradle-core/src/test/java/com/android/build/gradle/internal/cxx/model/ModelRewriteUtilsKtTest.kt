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

import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.CMakeSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.EnvironmentVariable
import com.google.common.truth.Truth.assertThat

import org.junit.Test
import java.io.File

class ModelRewriteUtilsKtTest {
    @Test
    fun `validate basic rewrite`() {
        BasicCmakeMock().apply {
            val cmakeModulePrime = abi.variant.module.cmake!!.replaceWith(
                cmakeExe = { File("my/cmake") }
            )
            val modulePrime = abi.variant.module.replaceWith(
                cmake = { cmakeModulePrime },
                cmakeToolchainFile = { File("my/toolchain") }
            )
            val variantPrime = abi.variant.replaceWith(
                module = { modulePrime }
            )
            val cmakeAbiPrime = abi.cmake!!.replaceWith(
                cmakeArtifactsBaseFolder = { File("my/build-root") },
                effectiveConfiguration = { CMakeSettingsConfiguration() }
            )
            val buildSettings = BuildSettingsConfiguration(
                listOf(EnvironmentVariable(
                    name = "env",
                    value = "val"
                ))
            )
            val abiPrime = abi.replaceWith(
                cmake = { cmakeAbiPrime },
                variant = { variantPrime },
                cxxBuildFolder = { File("my/build-root") },
                buildSettings = { buildSettings }
            )
            assertThat(cmakeModulePrime.cmakeExe.path.replace('\\', '/')).contains("my/cmake")
            assertThat(modulePrime.cmakeToolchainFile.path.replace('\\', '/')).contains("my/toolchain")
            assertThat(cmakeAbiPrime.cmakeArtifactsBaseFolder.path.replace('\\', '/')).contains("my/build-root")
            assertThat(abiPrime.cxxBuildFolder.path.replace('\\', '/')).contains("my/build-root")
            assertThat(abiPrime.buildSettings.environmentVariables).isEqualTo(listOf(EnvironmentVariable(
                name = "env",
                value = "val"
            )))
        }
    }
}