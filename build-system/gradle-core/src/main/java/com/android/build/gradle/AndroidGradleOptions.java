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
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.incremental.InstantRunApiLevelMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.utils.FileCache;
import com.android.prefs.AndroidLocation;
import com.android.repository.api.Channel;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;

/**
 * Determines if various options, triggered from the command line or environment, are set.
 */
public class AndroidGradleOptions {

    private static final boolean DEFAULT_ENABLE_AAPT2 = false;

    private static final boolean DEFAULT_ENABLE_BUILD_CACHE = true;

    public static final String PROPERTY_TEST_RUNNER_ARGS =
            "android.testInstrumentationRunnerArguments.";

    public static final String PROPERTY_THREAD_POOL_SIZE = "android.threadPoolSize";
    public static final String PROPERTY_THREAD_POOL_SIZE_OLD = "com.android.build.threadPoolSize";

    public static final String USE_DEPRECATED_NDK = "android.useDeprecatedNdk";

    public static final String PROPERTY_DISABLE_RESOURCE_VALIDATION =
            "android.disableResourceValidation";

    public static final String PROPERTY_INCREMENTAL_JAVA_COMPILE =
            "android.incrementalJavaCompile";

    public static final String PROPERTY_KEEP_TIMESTAMPS_IN_APK = "android.keepTimestampsInApk";

    public static final String PROPERTY_ENABLE_AAPT2 = "android.enableAapt2";

    public static final String ANDROID_ADDITIONAL_PLUGINS = "android.additional.plugins";

    public static final String ANDROID_SDK_CHANNEL = "android.sdk.channel";

    /**
     * Set to true to build native .so libraries only for the device it will be run on.
     */
    public static final String PROPERTY_BUILD_ONLY_TARGET_ABI = "android.buildOnlyTargetAbi";

    public static final String PROPERTY_SHARD_TESTS_BETWEEN_DEVICES =
            "android.androidTest.shardBetweenDevices";
    public static final String PROPERTY_SHARD_COUNT =
            "android.androidTest.numShards";
    public static  final String PROPERTY_USE_SDK_DOWNLOAD =
            "android.builder.sdkDownload";

    public static final String PROPERTY_ENABLE_BUILD_CACHE = "android.enableBuildCache";

    public static final String PROPERTY_BUILD_CACHE_DIR = "android.buildCacheDir";

    /**
     * Set to true to delay dependency resolution to task execution.
     */
    public static final String PROPERTY_ENABLE_IMPROVED_DEPENDENCY_RESOLUTION =
            "android.enableImprovedDependenciesResolution";

    public static final String GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY =
            "android.overrideVersionCheck";

    public static final String OLD_GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY =
            "com.android.build.gradle.overrideVersionCheck";

    public static final String OVERRIDE_PATH_CHECK_PROPERTY = "android.overridePathCheck";

    public static final String OLD_OVERRIDE_PATH_CHECK_PROPERTY =
            "com.android.build.gradle.overridePathCheck";

    public static final String INSTANT_RUN_API_LEVEL_PROPERTY = "android.instantRun.apiLevel";

    /**
     * Validate flag options.
     */
    public static void validate(@NonNull Project project) {
        if (isImprovedDependencyResolutionEnabled(project) && !isBuildCacheEnabled(project)) {
            throw new InvalidUserDataException("Build cache must be enable to use improved "
                    + "dependency resolution.  Set -Pandroid.enableBuildCache=true to continue.");
        }
    }

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

    public static boolean generateSourcesOnly(@NonNull Project project) {
        return getBoolean(project, AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY);
    }

    public static boolean keepTimestampsInApk(@NonNull Project project) {
        return getBoolean(project, PROPERTY_KEEP_TIMESTAMPS_IN_APK);
    }

    public static boolean isAapt2Enabled(@NonNull Project project) {
        return getBoolean(project, PROPERTY_ENABLE_AAPT2, DEFAULT_ENABLE_AAPT2);
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

    public static boolean isBuildOnlyTargetAbiEnabled(@NonNull Project project) {
        return getBoolean(project, PROPERTY_BUILD_ONLY_TARGET_ABI);
    }

    @Nullable
    public static String getBuildTargetAbi(@NonNull Project project) {
        return getString(project, AndroidProject.PROPERTY_BUILD_ABI);
    }

    /**
     * Returns the feature level for the target device.
     *
     * For preview versions that is the last stable version + 1.
     *
     * @param project the project being built
     * @return a the feature level for the targeted device, following the
     *         {@link AndroidProject#PROPERTY_BUILD_API} value passed by Android Studio.
     */
    public static int getTargetFeatureLevel(@NonNull Project project) {
        String featureLevelString = getString(project, AndroidProject.PROPERTY_BUILD_API);
        if (featureLevelString == null) {
            return AndroidVersion.DEFAULT.getFeatureLevel();
        }

        try {
            return Integer.parseInt(featureLevelString);
        } catch (NumberFormatException ignore) {
            project.getLogger().warn("Wrong build target version passed ", ignore);
            return AndroidVersion.DEFAULT.getFeatureLevel();
        }
    }

    @Nullable
    public static String getColdswapMode(@NonNull Project project) {
        return getString(project, AndroidProject.PROPERTY_SIGNING_COLDSWAP_MODE);
    }

    public static boolean useDeprecatedNdk(@NonNull Project project) {
        return getBoolean(project, USE_DEPRECATED_NDK);
    }

    @Nullable
    public static Integer getThreadPoolSize(@NonNull Project project) {
        Integer size = getInteger(project, PROPERTY_THREAD_POOL_SIZE);
        if (size == null) {
            size = getInteger(project, PROPERTY_THREAD_POOL_SIZE_OLD);
        }

        return size;
    }

    @Nullable
    public static SigningOptions getSigningOptions(@NonNull Project project) {
        String signingStoreFile =
                getString(project, AndroidProject.PROPERTY_SIGNING_STORE_FILE);
        String signingStorePassword =
                getString(project, AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD);
        String signingKeyAlias =
                getString(project, AndroidProject.PROPERTY_SIGNING_KEY_ALIAS);
        String signingKeyPassword =
                getString(project, AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD);

        if (signingStoreFile != null
                && signingStorePassword != null
                && signingKeyAlias != null
                && signingKeyPassword != null) {
            String signingStoreType =
                    getString(project, AndroidProject.PROPERTY_SIGNING_STORE_TYPE);

            return new SigningOptions(
                    signingStoreFile,
                    signingStorePassword,
                    signingKeyAlias,
                    signingKeyPassword,
                    signingStoreType,
                    getOptionalBoolean(project, AndroidProject.PROPERTY_SIGNING_V1_ENABLED),
                    getOptionalBoolean(project, AndroidProject.PROPERTY_SIGNING_V2_ENABLED));
        }

        return null;
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

    @Nullable
    public static Integer getVersionCodeOverride(@NonNull Project project) {
        return getInteger(project, AndroidProject.PROPERTY_VERSION_CODE);
    }

    @Nullable
    public static String getVersionNameOverride(@NonNull Project project) {
        return getString(project, AndroidProject.PROPERTY_VERSION_NAME);
    }

    @NonNull
    public static InstantRunApiLevelMode getInstantRunApiLevelMode(@NonNull Project project) {
        String valueName = getString(project, INSTANT_RUN_API_LEVEL_PROPERTY);
        if (valueName != null) {
            try {
                return InstantRunApiLevelMode.valueOf(valueName);
            } catch (IllegalArgumentException ignored) {
                // Return the default value below.
            }
        }

        return InstantRunApiLevelMode.COMPILE_SDK;
    }

    public static boolean isImprovedDependencyResolutionEnabled(@NonNull Project project) {
        return getBoolean(project, PROPERTY_ENABLE_IMPROVED_DEPENDENCY_RESOLUTION);
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

    private static boolean getBoolean(
            @NonNull Project project,
            @NonNull String propertyName) {
        return getBoolean(project, propertyName, false /*defaultValue*/);
    }

    @Nullable
    private static Boolean getOptionalBoolean(
            @NonNull Project project,
            @NonNull String propertyName) {
        if (project.hasProperty(propertyName)) {
            return getBoolean(project, propertyName);
        } else {
            return null;
        }
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

    public static boolean isJavaCompileIncrementalPropertySet(@NonNull Project project) {
        return project.hasProperty(PROPERTY_INCREMENTAL_JAVA_COMPILE);
    }

    public static String[] getAdditionalPlugins(Project project) {
        String string = getString(project, ANDROID_ADDITIONAL_PLUGINS);
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

    public static boolean isBuildCacheEnabled(@NonNull Project project) {
        return getBoolean(project, PROPERTY_ENABLE_BUILD_CACHE, DEFAULT_ENABLE_BUILD_CACHE);
    }

    @NonNull
    public static File getBuildCacheDir(@NonNull Project project) {
        String buildCacheDir = getString(project, PROPERTY_BUILD_CACHE_DIR);
        if (buildCacheDir != null) {
            return project.getRootProject().file(buildCacheDir);
        } else {
            // Use a directory under the ".android" directory if the build cache directory is not
            // set
            try {
                return new File(AndroidLocation.getFolder(), "build-cache");
            } catch (AndroidLocation.AndroidLocationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NonNull
    public static Optional<FileCache> getBuildCache(@NonNull Project project) {
        if (isBuildCacheEnabled(project)) {
            File buildCacheDir = getBuildCacheDir(project);
            try {
                return Optional.of(FileCache.getInstanceWithInterProcessLocking(buildCacheDir));
            } catch (Exception exception) {
                throw new RuntimeException(
                        String.format(
                                "Unable to create the build cache at '%1$s'.\n"
                                        + "%2$s",
                                buildCacheDir.getAbsolutePath(),
                                BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE),
                        exception);
            }
        } else {
            return Optional.empty();
        }
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

    public static class SigningOptions {
        @NonNull public final String storeFile;
        @NonNull public final String storePassword;
        @NonNull public final String keyAlias;
        @NonNull public final String keyPassword;
        @Nullable public final String storeType;
        @Nullable public final Boolean v1Enabled;
        @Nullable public final Boolean v2Enabled;

        SigningOptions(
                @NonNull String storeFile,
                @NonNull String storePassword,
                @NonNull String keyAlias,
                @NonNull String keyPassword,
                @Nullable String storeType,
                @Nullable Boolean v1Enabled,
                @Nullable Boolean v2Enabled) {
            this.storeFile = storeFile;
            this.storeType = storeType;
            this.storePassword = storePassword;
            this.keyAlias = keyAlias;
            this.keyPassword = keyPassword;
            this.v1Enabled = v1Enabled;
            this.v2Enabled = v2Enabled;
        }
    }
}
