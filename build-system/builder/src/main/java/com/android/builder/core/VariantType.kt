/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.core

import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleBuildVariant

/**
 * Type of a variant.
 */
enum class VariantType {
    APK(
            exportsDataBindingClassList = false,
            canHaveSplits = true,
            analyticsVariantType = GradleBuildVariant.VariantType.APPLICATION),
    LIBRARY(
            exportsDataBindingClassList = true,
            canHaveSplits = false,
            analyticsVariantType = GradleBuildVariant.VariantType.LIBRARY),
    FEATURE(exportsDataBindingClassList = true,
            canHaveSplits =  true,
            analyticsVariantType = GradleBuildVariant.VariantType.FEATURE),
    INSTANTAPP(
            exportsDataBindingClassList = false,
            canHaveSplits = false,
            analyticsVariantType = GradleBuildVariant.VariantType.INSTANTAPP),
    ANDROID_TEST(
            prefix = "androidTest",
            suffix = "AndroidTest",
            isSingleBuildType = true,
            artifactName = AndroidProject.ARTIFACT_ANDROID_TEST,
            artifactType = ArtifactMetaData.TYPE_ANDROID,
            analyticsVariantType = GradleBuildVariant.VariantType.ANDROID_TEST),
    UNIT_TEST(
            prefix = "test",
            suffix = "UnitTest",
            isSingleBuildType = false,
            artifactName = AndroidProject.ARTIFACT_UNIT_TEST,
            artifactType = ArtifactMetaData.TYPE_JAVA,
            analyticsVariantType = GradleBuildVariant.VariantType.UNIT_TEST);

    /**
     * Returns true if the variant is automatically generated for testing purposed, false
     * otherwise.
     */
    val isForTesting: Boolean
    /**
     * Returns prefix used for naming source directories. This is an empty string in
     * case of non-testing variants and a camel case string otherwise, e.g. "androidTest".
     */
    val prefix: String
    /**
     * Returns suffix used for naming Gradle tasks. This is an empty string in
     * case of non-testing variants and a camel case string otherwise, e.g. "AndroidTest".
     */
    val suffix: String
    /**
     * Whether the artifact type supports only a single build type.
     */
    val isSingleBuildType: Boolean
    /**
     * Returns the name used in the builder model for artifacts that correspond to this variant
     * type.
     */
    val artifactName: String
    /**
     * Returns the artifact type used in the builder model.
     */
    val artifactType: Int
    /**
     * Whether the artifact type should export the data binding class list.
     */
    val isExportDataBindingClassList: Boolean
    /**
     * Returns the corresponding variant type used by the analytics system.
     */
    val analyticsVariantType: GradleBuildVariant.VariantType
    /** Whether this variant can have split outputs.  */
    val canHaveSplits: Boolean

    /** App, Library variant.  */
    constructor(
        exportsDataBindingClassList: Boolean,
        canHaveSplits: Boolean,
        analyticsVariantType: GradleBuildVariant.VariantType) {
        this.isForTesting = false
        this.prefix = ""
        this.suffix = ""
        this.artifactName = AndroidProject.ARTIFACT_MAIN
        this.artifactType = ArtifactMetaData.TYPE_ANDROID
        this.isSingleBuildType = false
        this.isExportDataBindingClassList = exportsDataBindingClassList
        this.canHaveSplits = canHaveSplits
        this.analyticsVariantType = analyticsVariantType
    }

    /** Testing variant.  */
    constructor(
        prefix: String,
        suffix: String,
        isSingleBuildType: Boolean,
        artifactName: String,
        artifactType: Int,
        analyticsVariantType: GradleBuildVariant.VariantType) {
        this.artifactName = artifactName
        this.artifactType = artifactType
        this.isForTesting = true
        this.prefix = prefix
        this.suffix = suffix
        this.isSingleBuildType = isSingleBuildType
        this.isExportDataBindingClassList = false
        this.canHaveSplits = false
        this.analyticsVariantType = analyticsVariantType
    }

    companion object {
        @JvmStatic
        val testingTypes: ImmutableList<VariantType>
            get() {
                val result = ImmutableList.builder<VariantType>()
                for (variantType in values()) {
                    if (variantType.isForTesting) {
                        result.add(variantType)
                    }
                }
                return result.build()
            }
    }
}
