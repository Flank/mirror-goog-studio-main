/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;
import com.android.repository.api.Channel;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Maps;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.gradle.api.Project;

/**
 * Determines if various options, triggered from the command line or environment, are set.
 *
 * @deprecated see {@link com.android.build.gradle.options.ProjectOptions}
 */
@Deprecated
public class AndroidGradleOptions {

    public static final String PROPERTY_TEST_RUNNER_ARGS =
            "android.testInstrumentationRunnerArguments.";

    public static final String USE_DEPRECATED_NDK = "android.useDeprecatedNdk";

    public static final String PROPERTY_DISABLE_RESOURCE_VALIDATION =
            "android.disableResourceValidation";

    public static final String DEPRECATED_NDK_COMPILE_LEASE = "android.deprecatedNdkCompileLease";
    public static final long DEPRECATED_NDK_COMPILE_LEASE_DAYS = 60;
    public static final long DEPRECATED_NDK_COMPILE_LEASE_MILLIS =
            DEPRECATED_NDK_COMPILE_LEASE_DAYS * 24 * 60 * 60 * 1000;

    public static final String PROPERTY_KEEP_TIMESTAMPS_IN_APK = "android.keepTimestampsInApk";

    public static final String ANDROID_ADVANCED_PROFILING_TRANSFORMS =
            "android.advanced.profiling.transforms";

    public static final String ANDROID_SDK_CHANNEL = "android.sdk.channel";


    public static final String PROPERTY_SHARD_TESTS_BETWEEN_DEVICES =
            "android.androidTest.shardBetweenDevices";
    public static final String PROPERTY_SHARD_COUNT =
            "android.androidTest.numShards";
    public static  final String PROPERTY_USE_SDK_DOWNLOAD =
            "android.builder.sdkDownload";


    public static final String GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY =
            "android.overrideVersionCheck";

    public static final String OLD_GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY =
            "com.android.build.gradle.overrideVersionCheck";

    public static final String OVERRIDE_PATH_CHECK_PROPERTY = "android.overridePathCheck";

    public static final String OLD_OVERRIDE_PATH_CHECK_PROPERTY =
            "com.android.build.gradle.overridePathCheck";

    public static boolean getUseSdkDownload(@NonNull Project project) {
        return getBoolean(project, PROPERTY_USE_SDK_DOWNLOAD, true) && !invokedFromIde(project);
    }

    @NonNull
    public static Map<String, String> getExtraInstrumentationTestRunnerArgs(@NonNull Project project) {
        Map<String, String> argsMap = Maps.newHashMap();
        for (Map.Entry<String, ?> entry : project.getProperties().entrySet()) {
            if (entry.getKey().startsWith(PROPERTY_TEST_RUNNER_ARGS)) {
                String argName = entry.getKey().substring(PROPERTY_TEST_RUNNER_ARGS.length());
                String argValue = entry.getValue().toString();

                argsMap.put(argName, argValue);
            }
        }

        return argsMap;
    }

    public static boolean getShardAndroidTestsBetweenDevices(@NonNull Project project) {
        return getBoolean(project, PROPERTY_SHARD_TESTS_BETWEEN_DEVICES, false);
    }

    @Nullable
    public static Integer getInstrumentationShardCount(@NonNull Project project) {
        return getInteger(project, PROPERTY_SHARD_COUNT);
    }

    public static boolean invokedFromIde(@NonNull Project project) {
        return getBoolean(project, AndroidProject.PROPERTY_INVOKED_FROM_IDE);
    }

    public static boolean buildModelOnly(@NonNull Project project) {
        return getBoolean(project, AndroidProject.PROPERTY_BUILD_MODEL_ONLY);
    }

    public static boolean refreshExternalNativeModel(@NonNull Project project) {
        return getBoolean(project, AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL);
    }

    public static boolean buildModelOnlyAdvanced(@NonNull Project project) {
        return getBoolean(project, AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED);
    }

    public static boolean keepTimestampsInApk(@NonNull Project project) {
        return getBoolean(project, PROPERTY_KEEP_TIMESTAMPS_IN_APK);
    }

    public static boolean getTestOnly(@NonNull Project project) {
        return getBoolean(project, AndroidProject.PROPERTY_TEST_ONLY);
    }

    /**
     * Returns the level of model-only mode.
     *
     * The model-only mode is triggered when the IDE does a sync, and therefore we do
     * things a bit differently (don't throw exceptions for instance). Things evolved a bit
     * over versions and the behavior changes. This reflects the mode to use.
     *
     * @param project the project
     * @return an integer or null if we are not in model-only mode.
     *
     * @see AndroidProject#MODEL_LEVEL_0_ORIGINAL
     * @see AndroidProject#MODEL_LEVEL_1_SYNC_ISSUE
     * @see AndroidProject#MODEL_LEVEL_2_DONT_USE
     */
    @Nullable
    public static Integer buildModelOnlyVersion(@NonNull Project project) {
        String revision = getString(project, AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED);
        if (revision != null) {
            return Integer.parseInt(revision);
        }

        if (getBoolean(project, AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED)) {
            return AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE;
        }

        if (getBoolean(project, AndroidProject.PROPERTY_BUILD_MODEL_ONLY)) {
            return AndroidProject.MODEL_LEVEL_0_ORIGINAL;
        }

        return null;
    }

    public static boolean buildModelWithFullDependencies(@NonNull Project project) {
        String value = getString(project, AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES);
        if (value == null) {
            return false;
        }
        return Boolean.valueOf(value);
    }

    /**
     * Obtains the location for APKs as defined in the project.
     *
     * @param project the project
     * @return the location for APKs or {@code null} if not defined
     */
    @Nullable
    public static File getApkLocation(@NonNull Project project) {
        String locString = getString(project, AndroidProject.PROPERTY_APK_LOCATION);
        if (locString == null) {
            return null;
        }

        return project.file(locString);
    }

    @Nullable
    public static String getBuildTargetDensity(@NonNull Project project) {
        return getString(project, AndroidProject.PROPERTY_BUILD_DENSITY);
    }

    @Nullable
    public static String getBuildTargetAbi(@NonNull Project project) {
        return getString(project, AndroidProject.PROPERTY_BUILD_ABI);
    }

    /**
     * Returns the feature level for the target device.
     *
     * <p>For preview versions that is the last stable version + 1.
     *
     * @param project the project being built
     * @return a the feature level for the targeted device, following the {@link
     *     AndroidProject#PROPERTY_BUILD_API} value passed by Android Studio.
     */
    public static AndroidVersion getTargetAndroidVersion(@NonNull Project project) {
        Integer apiLevel = getInteger(project, AndroidProject.PROPERTY_BUILD_API);
        if (apiLevel == null) {
            return AndroidVersion.DEFAULT;
        }
        @Nullable String codeName = getString(project, AndroidProject.PROPERTY_BUILD_API_CODENAME);
        return new AndroidVersion(apiLevel, codeName);
    }

    public static boolean useDeprecatedNdk(@NonNull Project project) {
        return getBoolean(project, USE_DEPRECATED_NDK);
    }

    public static long getFreshDeprecatedNdkCompileLease() {
        return Instant.now().toEpochMilli();
    }

    public static boolean isDeprecatedNdkCompileLeaseExpired(@NonNull Project project) {
        Long leaseDate = getLong(project, DEPRECATED_NDK_COMPILE_LEASE);
        if (leaseDate == null) {
            // There is no lease so it is expired by definition
            return true;
        }
        long freshLease = getFreshDeprecatedNdkCompileLease();
        if (freshLease - leaseDate > DEPRECATED_NDK_COMPILE_LEASE_MILLIS) {
            // There is a lease but it expired
            return true;
        }
        if (leaseDate > freshLease) {
            // The lease date is set too far in the future so it is expired by definition
            return true;
        }
        return false;
    }

    @NonNull
    public static EnumSet<OptionalCompilationStep> getOptionalCompilationSteps(
            @NonNull Project project) {

        String values = getString(project, AndroidProject.PROPERTY_OPTIONAL_COMPILATION_STEPS);
        if (values != null) {
            List<OptionalCompilationStep> optionalCompilationSteps = new ArrayList<>();
            StringTokenizer st = new StringTokenizer(values, ",");
            while(st.hasMoreElements()) {
                optionalCompilationSteps.add(OptionalCompilationStep.valueOf(st.nextToken()));
            }
            return EnumSet.copyOf(optionalCompilationSteps);
        }
        return EnumSet.noneOf(OptionalCompilationStep.class);
    }

    public static boolean isResourceValidationEnabled(@NonNull Project project) {
        return !getBoolean(project, PROPERTY_DISABLE_RESOURCE_VALIDATION);
    }

    public static boolean isImprovedDependencyResolutionEnabled(@NonNull Project project) {
        return getBoolean(
                project,
                BooleanOption.ENABLE_IMPROVED_DEPENDENCY_RESOLUTION.getPropertyName(),
                BooleanOption.ENABLE_IMPROVED_DEPENDENCY_RESOLUTION.getDefaultValue());
    }

    @Nullable
    private static String getString(@NonNull Project project, String propertyName) {
        return project.hasProperty(propertyName) ? project.property(propertyName).toString() : null;
    }

    @Nullable
    private static Integer getInteger(@NonNull Project project, String propertyName) {
        if (project.hasProperty(propertyName)) {
            try {
                return Integer.parseInt(project.property(propertyName).toString());
            } catch (NumberFormatException e) {
                throw new RuntimeException("Property " + propertyName + " needs to be an integer.");
            }
        }

        return null;
    }

    @Nullable
    private static Long getLong(@NonNull Project project, String propertyName) {
        if (project.hasProperty(propertyName)) {
            try {
                return Long.parseLong(project.property(propertyName).toString());
            } catch (NumberFormatException e) {
                throw new RuntimeException("Property " + propertyName + " needs to be a long.");
            }
        }

        return null;
    }

    private static boolean getBoolean(
            @NonNull Project project,
            @NonNull String propertyName) {
        return getBoolean(project, propertyName, false /*defaultValue*/);
    }

    private static boolean getBoolean(
            @NonNull Project project,
            @NonNull String propertyName,
            boolean defaultValue) {
        if (project.hasProperty(propertyName)) {
            Object value = project.property(propertyName);
            if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            } else if (value instanceof Boolean) {
                return ((Boolean) value);
            }
        }

        return defaultValue;
    }

    @NonNull
    public static String[] getAdvancedProfilingTransforms(@NonNull Project project) {
        String string = getString(project, ANDROID_ADVANCED_PROFILING_TRANSFORMS);
        return string == null ?  new String[]{} : string.split(",");
    }

    @Nullable
    public static String getRestrictVariantProject(@NonNull Project project) {
        return getString(project, AndroidProject.PROPERTY_RESTRICT_VARIANT_PROJECT);
    }

    @Nullable
    public static String getRestrictVariantName(@NonNull Project project) {
        return getString(project, AndroidProject.PROPERTY_RESTRICT_VARIANT_NAME);
    }

    public static Channel getSdkChannel(@NonNull Project project) {
        Integer channel = getInteger(project, ANDROID_SDK_CHANNEL);
        if (channel != null) {
            return Channel.create(channel);
        } else {
            return Channel.DEFAULT;
        }
    }

    public static boolean overrideGradleVersionCheck(@NonNull Project project) {
        if (project.hasProperty(GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY)) {
            return getBoolean(project, GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY);
        } else {
            return Boolean.getBoolean(OLD_GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY);
        }
    }

    public static boolean overridePathCheck(@NonNull Project project) {
        if (project.hasProperty(OVERRIDE_PATH_CHECK_PROPERTY)) {
            return getBoolean(project, OVERRIDE_PATH_CHECK_PROPERTY);
        } else if (project.hasProperty(OLD_OVERRIDE_PATH_CHECK_PROPERTY)) {
            return getBoolean(project, OLD_OVERRIDE_PATH_CHECK_PROPERTY);
        } else {
            return Boolean.getBoolean(OLD_OVERRIDE_PATH_CHECK_PROPERTY);
        }
    }

}
