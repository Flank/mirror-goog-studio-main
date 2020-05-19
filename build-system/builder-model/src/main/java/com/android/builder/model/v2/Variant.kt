/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.builder.model.v2

/**
 * A build Variant.
 *
 * This is the combination of a Build Type and 0+ Product Flavors (exactly one for each existing
 * Flavor Dimension).
 *
 * Build Types and Flavors both contribute source folders, so this Variant is the direct
 * representation of a set of source folders (and configuration parameters) used to build something.
 *
 * However the output of a Variant is not a single item.
 *
 * First there can be several artifacts.
 * - Main Artifact: this is the main Android output(s). The app or the library being generated.
 * - Extra Android Artifacts: these are ancillary artifacts, most likely test app(s).
 * - Extra Java artifacts: these are pure-Java ancillary artifacts (junit support for instance).
 */
interface Variant {
    /**
     * Returns the name of the variant. It is made up of the build type and flavors (if applicable)
     *
     * @return the name of the variant.
     */
    val name: String

    /**
     * Returns a display name for the variant. It is made up of the build type and flavors
     * (if applicable)
     *
     * @return the name.
     */
    val displayName: String

    /**
     * Returns the main artifact for this variant.
     *
     * @return the artifact.
     */
    val mainArtifact: AndroidArtifact
    val extraAndroidArtifacts: Collection<AndroidArtifact>
    val extraJavaArtifacts: Collection<JavaArtifact>

    /**
     * Returns the build type. All variants have a build type, so this is never null.
     *
     * @return the name of the build type.
     */
    val buildType: String

    /**
     * Returns the flavors for this variants. This can be empty if no flavors are configured.
     *
     * @return a list of flavors which can be empty.
     */
    val productFlavors: List<String>

    /**
     * The result of the merge of all the flavors and of the main default config. If no flavors
     * are defined then this is the same as the default config.
     *
     * This is directly a ProductFlavor instance of a ProductFlavorContainer since this a composite
     * of existing ProductFlavors.
     *
     * @return the merged flavors.
     *
     * @see AndroidProject.getDefaultConfig
     */
    val mergedFlavor: ProductFlavor

    /**
     * Returns the list of target projects and the variants that this variant is testing.
     * This is specified for the test only variants (ones using the test plugin).
     *
     * @return all tested variants
     */
    val testedTargetVariants: Collection<TestedTargetVariant>

    /**
     * Returns true if this variant is instant app compatible, intended to be possibly built and
     * served in an instant app context. This is populated during sync from the project's manifest.
     * Only application modules and dynamic feature modules will set this property.
     *
     * @return true if this variant is instant app compatible
     * @since 3.3
     */
    val isInstantAppCompatible: Boolean

    /**
     * Returns all desugared methods including backported methods handled by D8 and methods provided
     * by core library desugaring. Only D8 backported methods are returned if coreLibraryDesugaring
     * is disabled or we are not able to find expected lint files from the dependency of
     * coreLibraryDesugaring configuration.
     *
     * @return all desugared methods
     * @since 4.1
     */
    val desugaredMethods: List<String>
}