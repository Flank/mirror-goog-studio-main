/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.builder.model.v2.ide

import com.android.builder.model.v2.AndroidModel

/**
 * Basic information about a build Variant.
 *
 * This is basically the source set information.
 */
interface BasicVariant: AndroidModel {
    /**
     * The name of the variant.
     */
    val name: String

    /**
     * The main artifact for this variant.
     */
    val mainArtifact: BasicArtifact

    /**
     * The AndroidTest artifact for this variant, if applicable.
     */
    val androidTestArtifact: BasicArtifact?

    /**
     * The Unit Test artifact for this variant, if applicable.
     */
    val unitTestArtifact: BasicArtifact?

    /**
     * The TestFixtures artifact for this variant, if applicable.
     */
    val testFixturesArtifact: BasicArtifact?

    /**
     * The build type name.
     *
     * If null, no build type is associated with the variant (this generally means that no build
     * types exist, which can only happen for libraries)
     */
    val buildType: String?

    /**
     * The flavors for this variants. This can be empty if no flavors are configured.
     */
    val productFlavors: List<String>

    /**
     * Returns the application ID of the main artifact of this build variant.
     *
     * Known for:
     *  - Application plugin main artifacts
     *  - Test-only plugin main artifacts
     *
     *  Not included (null) for:
     *   - Library plugin main artifacts, as no APK is produced
     *   - Dynamic feature plugin main artifacts, as the application ID comes from the base
     *     application, and is therefore not available in dynamic feature projects during
     *     configuration. In this case Android Studio must look at the dependency graph to find the
     *     base application to find this value.
     */
    val applicationId: String?

    /**  Returns the application ID of the AndroidTest artifact of this build variant.
     *
     *   Known for:
     *    - AndroidTest components of all project types
     */
    val testApplicationId: String?
}

