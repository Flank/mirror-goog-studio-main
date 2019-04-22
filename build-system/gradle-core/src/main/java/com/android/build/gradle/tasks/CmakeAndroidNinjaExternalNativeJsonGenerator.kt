/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.cxx.services.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.google.wireless.android.sdk.stats.GradleBuildVariant

/**
 * This strategy uses the older custom CMake (version 3.6) that directly generates the JSON file as
 * part of project configuration.
 */
internal class CmakeAndroidNinjaExternalNativeJsonGenerator
    (
    variant: CxxVariantModel,
    abis: List<CxxAbiModel>,
    stats: GradleBuildVariant.Builder
) : CmakeExternalNativeJsonGenerator(variant, abis, stats) {

    internal override fun getCacheArguments(abi: CxxAbiModel): List<String> {
        val cacheArguments = getCommonCacheArguments(abi)
        cacheArguments.add("-DCMAKE_TOOLCHAIN_FILE=${abi.variant.module.cmakeToolchainFile.absolutePath}")
        cacheArguments.add("-DCMAKE_MAKE_PROGRAM=${cmake.ninjaExe.absolutePath}")

        cacheArguments.add("-GAndroid Gradle - Ninja")
        return cacheArguments
    }

    override fun executeProcessAndGetOutput(abi: CxxAbiModel): String {
        val logPrefix = "${variant.variantName}|${abi.abi.tag} :"
        return abi.variant.module.createProcessOutputJunction(
            abi.cxxBuildFolder,
            "android_gradle_generate_cmake_ninja_json_${abi.abi.tag}",
            getProcessBuilder(abi),
            logPrefix
        )
            .logStderrToInfo()
            .logStdoutToInfo()
            .executeAndReturnStdoutString()
    }
}
