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

package com.android.build.gradle.options

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.model.AndroidProject

enum class BooleanOption(
    override val propertyName: String,
    override val defaultValue: Boolean = false,
    override val status: Option.Status = Option.Status.EXPERIMENTAL
) : Option<Boolean> {
    ENABLE_AAPT2("android.enableAapt2", true, DeprecationReporter.DeprecationTarget.AAPT),

    ENABLE_BUILD_CACHE("android.enableBuildCache", true),
    ENABLE_PROFILE_JSON("android.enableProfileJson", false),
    // Used by Studio as workaround for b/71054106, b/75955471
    ENABLE_SDK_DOWNLOAD("android.builder.sdkDownload", true, status = Option.Status.STABLE),
    ENABLE_TEST_SHARDING("android.androidTest.shardBetweenDevices"),
    ENABLE_DEX_ARCHIVE(
            "android.useDexArchive", true, DeprecationReporter.DeprecationTarget.LEGACY_DEXER),

    ENABLE_INTERMEDIATE_ARTIFACTS_CACHE("android.enableIntermediateArtifactsCache", true),
    ENABLE_EXTRACT_ANNOTATIONS("android.enableExtractAnnotations", true),
    VERSION_CHECK_OVERRIDE_PROPERTY("android.overrideVersionCheck"),
    OVERRIDE_PATH_CHECK_PROPERTY("android.overridePathCheck"),
    ENABLE_DESUGAR(
            "android.enableDesugar", true, DeprecationReporter.DeprecationTarget.DESUGAR_TOOL),
    ENABLE_INCREMENTAL_DESUGARING(
            "android.enableIncrementalDesugaring",
            true,
            DeprecationReporter.DeprecationTarget.INCREMENTAL_DESUGARING),
    ENABLE_GRADLE_WORKERS("android.enableGradleWorkers", false),
    ENABLE_AAPT2_WORKER_ACTIONS("android.enableAapt2WorkerActions", true),
    ENABLE_CORE_LAMBDA_STUBS(
            "android.enableCoreLambdaStubs",
            true,
            DeprecationReporter.DeprecationTarget.CORE_LAMBDA_STUBS),

    ENABLE_D8("android.enableD8", true, DeprecationReporter.DeprecationTarget.LEGACY_DEXER),
    ENABLE_D8_DESUGARING("android.enableD8.desugaring", true),
    ENABLE_D8_MAIN_DEX_LIST(
            "android.enableD8MainDexList",
            true,
            DeprecationReporter.DeprecationTarget.LEGACY_DEXER),

    ENABLE_R8("android.enableR8", false),
    /** Set to true by default, but has effect only if R8 is enabled. */
    ENABLE_R8_DESUGARING("android.enableR8.desugaring", true),

    // Marked as stable to avoid reporting deprecation twice.
    ENABLE_DEPRECATED_NDK("android.useDeprecatedNdk", status = Option.Status.STABLE),
    DISABLE_RESOURCE_VALIDATION("android.disableResourceValidation"),
    CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES("android.consumeDependenciesAsSharedLibraries"),
    CONVERT_NON_NAMESPACED_DEPENDENCIES("android.convertNonNamespacedDependencies"),
    USE_AAPT2_FROM_MAVEN("android.useAapt2FromMaven", true),

    /** Set to true to build native .so libraries only for the device it will be run on. */
    BUILD_ONLY_TARGET_ABI("android.buildOnlyTargetAbi", true),
    KEEP_TIMESTAMPS_IN_APK("android.keepTimestampsInApk"),

    ENABLE_NEW_DSL_AND_API("android.enableNewDsl"),

    ENABLE_DATA_BINDING_V2("android.databinding.enableV2", true),

    IDE_INVOKED_FROM_IDE(AndroidProject.PROPERTY_INVOKED_FROM_IDE, status = Option.Status.STABLE),
    IDE_BUILD_MODEL_ONLY(AndroidProject.PROPERTY_BUILD_MODEL_ONLY, status = Option.Status.STABLE),
    IDE_BUILD_MODEL_ONLY_ADVANCED(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED, status = Option.Status.STABLE),
    IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES(
            AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES, status = Option.Status.STABLE),
    IDE_REFRESH_EXTERNAL_NATIVE_MODEL(AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL, status = Option.Status.STABLE),
    IDE_GENERATE_SOURCES_ONLY(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY, status = Option.Status.STABLE),
    ENABLE_SEPARATE_APK_RESOURCES("android.enableSeparateApkRes", true),
    ENABLE_EXPERIMENTAL_FEATURE_DATABINDING("android.enableExperimentalFeatureDatabinding", false),
    ENABLE_SEPARATE_R_CLASS_COMPILATION("android.enableSeparateRClassCompilation"),
    ENABLE_JETIFIER("android.enableJetifier", false, status = Option.Status.STABLE),
    USE_ANDROID_X("android.useAndroidX", false, status = Option.Status.STABLE),
    ENABLE_UNIT_TEST_BINARY_RESOURCES("android.enableUnitTestBinaryResources", false),
    DISABLE_EARLY_MANIFEST_PARSING("android.disableEarlyManifestParsing", false),
    ENABLE_PARALLEL_NATIVE_JSON_GEN("android.enableParallelJsonGen", true),
    ;

    constructor(
        propertyName: String,
        defaultValue: Boolean,
        deprecationTarget: DeprecationReporter.DeprecationTarget
    ) :
            this(propertyName, defaultValue, Option.Status.Deprecated(deprecationTarget))

    override fun parse(value: Any): Boolean {
        return when (value) {
            is Boolean -> value
            is CharSequence -> java.lang.Boolean.parseBoolean(value.toString())
            is Number -> value.toInt() != 0
            else -> throw IllegalArgumentException(
                "Cannot parse project property "
                        + this.propertyName
                        + "='"
                        + value
                        + "' of type '"
                        + value.javaClass
                        + "' as boolean."
            )
        }
    }

}
