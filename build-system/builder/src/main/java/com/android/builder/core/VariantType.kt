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
interface VariantType {
    /**
     * Returns true is the variant outputs an AAR.
     */
    val isAar: Boolean

    /**
     * Returns true is the variant outputs an APK.
     */
    val isApk: Boolean

    /**
     * Returns true is the variant is a base module. This is only true if it can have features.
     * If the variant can never have feature (TEST modules for instance), then this is false.
     */
    val isBaseModule: Boolean

    /**
     * Returns true if the variant is a dual-type. This is only valid for BASE_FEATURE/FEATURE.
     * The library component of a feature returns false.
     */
    val isHybrid: Boolean

    /**
     * Returns true if the variant publishes artifacts to meta-data.
     */
    val publishToMetadata: Boolean

    /**
     * Returns true if this is the test component of the module.
     */
    val isTestComponent: Boolean
    /**
     * Returns true if the variant is a test variant, whether this is the test component of a module
     * (testing the prod component of the same module) or a separate test-only module.
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

    val consumeType: String
    val publishType: String

    val name: String

    companion object {
        const val ANDROID_TEST_PREFIX = "androidTest"
        const val ANDROID_TEST_SUFFIX = "AndroidTest"
        const val UNIT_TEST_PREFIX = "test"
        const val UNIT_TEST_SUFFIX = "UnitTest"

        val testComponents: ImmutableList<VariantType>
            get() {
                val result = ImmutableList.builder<VariantType>()
                for (variantType in VariantTypeImpl.values()) {
                    if (variantType.isTestComponent) {
                        result.add(variantType)
                    }
                }
                return result.build()
            }

    }
}

// TODO: synchronize with AndroidTypeAttr somehow. Probably move this to gradle-core with new API/DSL...
const val ATTR_APK = "Apk"
const val ATTR_AAR = "Aar"
const val ATTR_FEATURE = "Feature"
const val ATTR_METADATA = "Metadata"

enum class VariantTypeImpl(
    override val isAar: Boolean,
    override val isApk: Boolean,
    override val isBaseModule: Boolean = false,
    override val isHybrid: Boolean = false,
    override val publishToMetadata: Boolean = false,
    override val isForTesting: Boolean,
    override val prefix: String,
    override val suffix: String,
    override val isSingleBuildType: Boolean,
    override val artifactName: String,
    override val artifactType: Int,
    override val isExportDataBindingClassList: Boolean,
    override val analyticsVariantType: GradleBuildVariant.VariantType,
    override val canHaveSplits: Boolean,
    private val consumeTypeOptional: String?,
    private val publishTypeOptional: String?

): VariantType {
    BASE_APK(
        isAar = false,
        isApk = true,
        isBaseModule = true,
        isHybrid = false,
        publishToMetadata = true,
        isForTesting = false,
        prefix = "",
        suffix = "",
        isSingleBuildType = false,
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.APPLICATION,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_AAR,
        publishTypeOptional = ATTR_APK
    ),
    OPTIONAL_APK(
        isAar = false,
        isApk = true,
        isBaseModule = false,
        isHybrid = false,
        publishToMetadata = true,
        isForTesting = false,
        prefix = "",
        suffix = "",
        isSingleBuildType = false,
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.APPLICATION,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_APK,
        publishTypeOptional = ATTR_APK),
    BASE_FEATURE(
        isAar = false,
        isApk = true,
        isBaseModule = true,
        isHybrid = true,
        publishToMetadata = false,
        isForTesting = false,
        prefix = "",
        suffix = "",
        isSingleBuildType = false,
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.FEATURE,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_AAR,
        publishTypeOptional = ATTR_FEATURE
    ),
    FEATURE(
        isAar = false,
        isApk = true,
        isBaseModule = false,
        isHybrid = true,
        publishToMetadata = true,
        isForTesting = false,
        prefix = "",
        suffix = "",
        isSingleBuildType = false,
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.FEATURE,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_FEATURE,
        publishTypeOptional = ATTR_FEATURE
    ),
    LIBRARY(
        isAar = true,
        isApk = false,
        isBaseModule = false,
        isHybrid = false,
        publishToMetadata = false,
        isForTesting = false,
        prefix = "",
        suffix = "",
        isSingleBuildType = false,
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = true,
        analyticsVariantType = GradleBuildVariant.VariantType.LIBRARY,
        canHaveSplits = true,
        consumeTypeOptional = ATTR_AAR,
        publishTypeOptional = ATTR_AAR),
    INSTANTAPP(
        isAar = false,
        isApk = false,
        isBaseModule = false,
        isHybrid = false,
        publishToMetadata = false,
        isForTesting = false,
        prefix = "",
        suffix = "",
        isSingleBuildType = false,
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.INSTANTAPP,
        canHaveSplits = false,
        consumeTypeOptional = ATTR_FEATURE,
        publishTypeOptional = ATTR_FEATURE
    ),
    TEST_APK(
        isAar = false,
        isApk = true,
        isBaseModule = false,
        isHybrid = false,
        publishToMetadata = false,
        isForTesting = true,
        prefix = "",
        suffix = "",
        isSingleBuildType = false,
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.APPLICATION,
        canHaveSplits = false,
        consumeTypeOptional = ATTR_APK,
        publishTypeOptional = ATTR_APK),
    ANDROID_TEST(
        isAar = false,
        isApk = true,
        isBaseModule = false,
        isHybrid = false,
        publishToMetadata = false,
        isForTesting = true,
        prefix = VariantType.ANDROID_TEST_PREFIX,
        suffix = VariantType.ANDROID_TEST_SUFFIX,
        isSingleBuildType = true,
        artifactName = AndroidProject.ARTIFACT_ANDROID_TEST,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.ANDROID_TEST,
        canHaveSplits = false,
        consumeTypeOptional = null,
        publishTypeOptional = null),
    UNIT_TEST(
        isAar = false,
        isApk = false,
        isBaseModule = false,
        isHybrid = false,
        publishToMetadata = false,
        isForTesting = true,
        prefix = VariantType.UNIT_TEST_PREFIX,
        suffix = VariantType.UNIT_TEST_SUFFIX,
        isSingleBuildType = true,
        artifactName = AndroidProject.ARTIFACT_UNIT_TEST,
        artifactType = ArtifactMetaData.TYPE_JAVA,
        isExportDataBindingClassList = false,
        analyticsVariantType = GradleBuildVariant.VariantType.UNIT_TEST,
        canHaveSplits = false,
        consumeTypeOptional = null,
        publishTypeOptional = null);

    override val isTestComponent: Boolean
        get() = isForTesting && this != TEST_APK

    override val consumeType: String
        get() = consumeTypeOptional ?: throw RuntimeException("Unsupported consumeType for VariantType: ${this.name}")
    override val publishType: String
        get() = publishTypeOptional ?: throw RuntimeException("Unsupported publishType for VariantType: ${this.name}")
}
