/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.tasks.NativeBuildSystem

/**
 * This class represents a single native build variant config that is abstract against the
 * underlying native build system. That is, it hides whether the build system is CMake or ndk-build.
 */
class NativeBuildSystemVariantConfig(
        buildSystem: NativeBuildSystem,
        config: GradleVariantConfiguration) {
    /**
     * Get the set of abiFilters from the externalNativeBuild part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         externalNativeBuild {
     *             cmake {
     *                 abiFilters "x86", "x86_64"
     *             }
     *         }
     *     }
     * </pre>
     *
     * @return a Set of ABIs to build. Return the empty set if nothing was specified.
     */
    val externalNativeBuildAbiFilters: Set<String> = when (buildSystem) {
        NativeBuildSystem.CMAKE ->
            config.externalNativeBuildOptions.externalNativeCmakeOptions?.abiFilters ?: setOf()
        NativeBuildSystem.NDK_BUILD ->
            config.externalNativeBuildOptions.externalNativeNdkBuildOptions?.abiFilters ?: setOf()
        else -> throw IllegalArgumentException("Unknown ExternalNativeJsonGenerator type")}

    /**
     * Get the set of abiFilters from the ndk part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         ndk {
     *             abiFilters "x86", "x86_64"
     *         }
     *     }
     * </pre>
     *
     * @return a Set of ABIs to build. Return the empty set if nothing was specified.
     */
    val ndkAbiFilters: Set<String> = config.ndkConfig.abiFilters ?: setOf()
}
