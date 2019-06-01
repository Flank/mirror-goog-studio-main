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

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.replaceWith
import com.android.build.gradle.tasks.NativeBuildSystem
import java.io.File

/**
 * If there is a CMakeSettings.json then replace relevant model values with settings from it.
 */
fun CxxAbiModel.rewriteCxxAbiModelWithCMakeSettings() : CxxAbiModel {
    if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
        val original = this
        val configuration by lazy {
            original.getCMakeSettingsConfiguration(variant.cmakeSettingsConfiguration)
                ?: original.getCMakeSettingsConfiguration(TRADITIONAL_CONFIGURATION_NAME)!!
        }
        val cmakeModule = original.variant.module.cmake!!.replaceWith(
            cmakeExe = { configuration.cmakeExecutable.toFile()
                ?: original.variant.module.cmake!!.cmakeExe
            }
        )
        val module = original.variant.module.replaceWith(
            cmake = { cmakeModule },
            cmakeToolchainFile = {
                configuration.cmakeToolchain.toFile()
                    ?: original.variant.module.cmakeToolchainFile
            }
        )
        val variant = original.variant.replaceWith(
            module = { module }
        )
        val cmakeAbi = original.cmake?.replaceWith(
            cmakeArtifactsBaseFolder =  {
                configuration.buildRoot.toFile()
                    ?: original.cmake!!.cmakeArtifactsBaseFolder
            },
            generator = { configuration.generator ?: original.cmake!!.generator }
        )
        return original.replaceWith(
            cmake = { cmakeAbi },
            variant = { variant },
            cxxBuildFolder = { configuration.buildRoot.toFile() ?: original.cxxBuildFolder }
        )
    }
    return this
}

/**
 * Turn a string into a File with null propagation.
 */
private fun String?.toFile() = if (this != null) File(this) else null
