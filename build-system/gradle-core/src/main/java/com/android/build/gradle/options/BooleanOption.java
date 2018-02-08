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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.builder.model.AndroidProject;

public enum BooleanOption implements Option<Boolean> {
    ENABLE_AAPT2("android.enableAapt2", true, DeprecationReporter.DeprecationTarget.AAPT),

    ENABLE_BUILD_CACHE("android.enableBuildCache", true),
    ENABLE_PROFILE_JSON("android.enableProfileJson", false),
    ENABLE_SDK_DOWNLOAD("android.builder.sdkDownload", true),
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
    ENABLE_AAPT2_WORKER_ACTIONS("android.enableAapt2WorkerActions", false),
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

    ENABLE_DEPRECATED_NDK("android.useDeprecatedNdk"),
    DISABLE_RESOURCE_VALIDATION("android.disableResourceValidation"),
    CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES("android.consumeDependenciesAsSharedLibraries"),
    CONVERT_NON_NAMESPACED_DEPENDENCIES("android.convertNonNamespacedDependencies"),

    /** Set to true to build native .so libraries only for the device it will be run on. */
    BUILD_ONLY_TARGET_ABI("android.buildOnlyTargetAbi", true),
    KEEP_TIMESTAMPS_IN_APK("android.keepTimestampsInApk"),

    ENABLE_NEW_DSL_AND_API("android.enableNewDsl"),

    ENABLE_DATA_BINDING_V2("android.databinding.enableV2", false),

    IDE_INVOKED_FROM_IDE(AndroidProject.PROPERTY_INVOKED_FROM_IDE),
    IDE_BUILD_MODEL_ONLY(AndroidProject.PROPERTY_BUILD_MODEL_ONLY),
    IDE_BUILD_MODEL_ONLY_ADVANCED(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED),
    IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES(
            AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES),
    IDE_REFRESH_EXTERNAL_NATIVE_MODEL(AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL),
    IDE_GENERATE_SOURCES_ONLY(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY),
    ENABLE_SEPARATE_APK_RESOURCES("android.enableSeparateApkRes", true),
    ENABLE_EXPERIMENTAL_FEATURE_DATABINDING("android.enableExperimentalFeatureDatabinding", false),
    ENABLE_SEPARATE_R_CLASS_COMPILATION("android.enableSeparateRClassCompilation"),
    ;

    @NonNull private final String propertyName;
    private final boolean defaultValue;
    @Nullable private final DeprecationReporter.DeprecationTarget deprecationTarget;

    BooleanOption(@NonNull String propertyName) {
        this(propertyName, false);
    }

    BooleanOption(@NonNull String propertyName, boolean defaultValue) {
        this(propertyName, defaultValue, null);
    }

    BooleanOption(
            @NonNull String propertyName,
            boolean defaultValue,
            @Nullable DeprecationReporter.DeprecationTarget deprecationTarget) {
        this.propertyName = propertyName;
        this.defaultValue = defaultValue;
        this.deprecationTarget = deprecationTarget;
    }

    @Override
    @NonNull
    public String getPropertyName() {
        return propertyName;
    }

    @NonNull
    @Override
    public Boolean getDefaultValue() {
        return defaultValue;
    }

    @NonNull
    @Override
    public Boolean parse(@NonNull Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof CharSequence) {
            return Boolean.parseBoolean(value.toString());
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        throw new IllegalArgumentException(
                "Cannot parse project property "
                        + this.getPropertyName()
                        + "='"
                        + value
                        + "' of type '"
                        + value.getClass()
                        + "' as boolean.");
    }

    @Override
    public boolean isDeprecated() {
        return (deprecationTarget != null);
    }

    @Nullable
    @Override
    public DeprecationReporter.DeprecationTarget getDeprecationTarget() {
        return deprecationTarget;
    }
}
