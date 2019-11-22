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
    override val defaultValue: Boolean,
    val stage: Stage,
    override val additionalInfo: String = ""
) : Option<Boolean> {

    /* ----------
     * STABLE API
     */

    // IDE properties
    IDE_INVOKED_FROM_IDE(AndroidProject.PROPERTY_INVOKED_FROM_IDE, false, ApiStage.Stable),
    IDE_BUILD_MODEL_ONLY(AndroidProject.PROPERTY_BUILD_MODEL_ONLY, false, ApiStage.Stable),
    IDE_BUILD_MODEL_ONLY_ADVANCED(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED, false, ApiStage.Stable),
    IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES(AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES, false, ApiStage.Stable),
    IDE_REFRESH_EXTERNAL_NATIVE_MODEL(AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL, false, ApiStage.Stable),
    IDE_GENERATE_SOURCES_ONLY(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY, false, ApiStage.Stable),

    // tell bundletool to only extract instant APKs.
    IDE_EXTRACT_INSTANT(AndroidProject.PROPERTY_EXTRACT_INSTANT_APK, false, ApiStage.Stable),

    // Flag used to indicate a "deploy as instant" run configuration.
    IDE_DEPLOY_AS_INSTANT_APP(AndroidProject.PROPERTY_DEPLOY_AS_INSTANT_APP, false, ApiStage.Stable),

    ENABLE_STUDIO_VERSION_CHECK("android.injected.studio.version.check", true, ApiStage.Stable),

    // Features' default values
    BUILD_FEATURE_AIDL("android.defaults.buildfeatures.aidl", true, ApiStage.Stable),
    BUILD_FEATURE_BUILDCONFIG("android.defaults.buildfeatures.buildconfig", true, ApiStage.Stable),
    BUILD_FEATURE_DATABINDING("android.defaults.buildfeatures.databinding", false, ApiStage.Stable),
    BUILD_FEATURE_RENDERSCRIPT("android.defaults.buildfeatures.renderscript", true, ApiStage.Stable),
    BUILD_FEATURE_RESVALUES("android.defaults.buildfeatures.resvalues", true, ApiStage.Stable),
    BUILD_FEATURE_SHADERS("android.defaults.buildfeatures.shaders", true, ApiStage.Stable),
    BUILD_FEATURE_VIEWBINDING("android.defaults.buildfeatures.viewbinding", false, ApiStage.Stable),

    // AndroidX & Jetifier
    USE_ANDROID_X("android.useAndroidX", false, ApiStage.Stable),
    ENABLE_JETIFIER("android.enableJetifier", false, ApiStage.Stable),

    DEBUG_OBSOLETE_API("android.debug.obsoleteApi", false, ApiStage.Stable),

    /* ------------------
     * SUPPORTED FEATURES
     */

    // Used by Studio as workaround for b/71054106, b/75955471
    ENABLE_SDK_DOWNLOAD("android.builder.sdkDownload", true, FeatureStage.Supported),

    ENFORCE_UNIQUE_PACKAGE_NAMES("android.uniquePackageNames", false, FeatureStage.Supported),

    // Flag added to work around b/130596259.
    FORCE_JACOCO_OUT_OF_PROCESS("android.forceJacocoOutOfProcess", false, FeatureStage.Supported),

    ENABLE_R8_LIBRARIES("android.enableR8.libraries", true, FeatureStage.Supported),
    ENABLE_UNCOMPRESSED_NATIVE_LIBS_IN_BUNDLE("android.bundle.enableUncompressedNativeLibs", true, FeatureStage.Supported),
    ENABLE_DEXING_ARTIFACT_TRANSFORM("android.enableDexingArtifactTransform", true, FeatureStage.Supported),
    USE_RELATIVE_PATH_IN_TEST_CONFIG("android.testConfig.useRelativePath", true, FeatureStage.Supported),
    ENABLE_INCREMENTAL_DATA_BINDING("android.databinding.incremental", true, FeatureStage.Supported),
    PRECOMPILE_DEPENDENCIES_RESOURCES("android.precompileDependenciesResources", true, FeatureStage.Supported),

    /* ---------------------
     * EXPERIMENTAL FEATURES
     */

    ENABLE_PROFILE_JSON("android.enableProfileJson", false, FeatureStage.Experimental),
    WARN_ABOUT_DEPENDENCY_RESOLUTION_AT_CONFIGURATION("android.dependencyResolutionAtConfigurationTime.warn", false, FeatureStage.Experimental),
    DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION("android.dependencyResolutionAtConfigurationTime.disallow", false, FeatureStage.Experimental),
    ENABLE_TEST_SHARDING("android.androidTest.shardBetweenDevices", false, FeatureStage.Experimental),
    VERSION_CHECK_OVERRIDE_PROPERTY("android.overrideVersionCheck", false, FeatureStage.Experimental),
    OVERRIDE_PATH_CHECK_PROPERTY("android.overridePathCheck", false, FeatureStage.Experimental),
    ENABLE_GRADLE_WORKERS("android.enableGradleWorkers", true, FeatureStage.Experimental),
    DISABLE_RESOURCE_VALIDATION("android.disableResourceValidation", false, FeatureStage.Experimental),
    CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES("android.consumeDependenciesAsSharedLibraries", false, FeatureStage.Experimental),
    DISABLE_EARLY_MANIFEST_PARSING("android.disableEarlyManifestParsing", false, FeatureStage.Experimental),
    DEPLOYMENT_USES_DIRECTORY("android.deployment.useOutputDirectory", false, FeatureStage.Experimental),
    DEPLOYMENT_PROVIDES_LIST_OF_CHANGES("android.deployment.provideListOfChanges", false, FeatureStage.Experimental),
    ENABLE_JVM_RESOURCE_COMPILER("android.enableJvmResourceCompiler", false, FeatureStage.Experimental),
    ENABLE_RESOURCE_NAMESPACING_DEFAULT("android.enableResourceNamespacingDefault", false, FeatureStage.Experimental),
    NAMESPACED_R_CLASS("android.namespacedRClass", false, FeatureStage.Experimental),
    FULL_R8("android.enableR8.fullMode", false, FeatureStage.Experimental),
    CONDITIONAL_KEEP_RULES("android.useConditionalKeepRules", false, FeatureStage.Experimental),
    KEEP_SERVICES_BETWEEN_BUILDS("android.keepWorkerActionServicesBetweenBuilds", false, FeatureStage.Experimental),
    USE_NON_FINAL_RES_IDS("android.nonFinalResIds", false, FeatureStage.Experimental),
    ENABLE_SIDE_BY_SIDE_NDK("android.enableSideBySideNdk", true, FeatureStage.Experimental),
    ENABLE_R_TXT_RESOURCE_SHRINKING("android.enableRTxtResourceShrinking", false, FeatureStage.Experimental),
    ENABLE_PARTIAL_R_INCREMENTAL_BUILDS("android.enablePartialRIncrementalBuilds", false, FeatureStage.Experimental),

    /** When set R classes are treated as compilation classpath in libraries, rather than runtime classpath, with values set to 0. */
    ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT("android.enableAdditionalTestOutput", true, FeatureStage.Experimental),

    ENABLE_APP_COMPILE_TIME_R_CLASS("android.enableAppCompileTimeRClass", false, FeatureStage.Experimental),
    COMPILE_CLASSPATH_LIBRARY_R_CLASSES("android.useCompileClasspathLibraryRClasses", true, FeatureStage.Experimental),
    ENABLE_BUILD_CACHE("android.enableBuildCache", true, FeatureStage.Experimental),
    ENABLE_INTERMEDIATE_ARTIFACTS_CACHE("android.enableIntermediateArtifactsCache", true, FeatureStage.Experimental),
    ENABLE_EXTRACT_ANNOTATIONS("android.enableExtractAnnotations", true, FeatureStage.Experimental),
    ENABLE_AAPT2_WORKER_ACTIONS("android.enableAapt2WorkerActions", true, FeatureStage.Experimental),
    ENABLE_D8_DESUGARING("android.enableD8.desugaring", true, FeatureStage.Experimental),

    /** Set to true by default, but has effect only if R8 is enabled. */
    ENABLE_R8_DESUGARING("android.enableR8.desugaring", true, FeatureStage.Experimental),

    // Marked as stable to avoid reporting deprecation twice.
    CONVERT_NON_NAMESPACED_DEPENDENCIES("android.convertNonNamespacedDependencies", true, FeatureStage.Experimental),

    /** Set to true to build native .so libraries only for the device it will be run on. */
    BUILD_ONLY_TARGET_ABI("android.buildOnlyTargetAbi", true, FeatureStage.Experimental),

    ENABLE_PARALLEL_NATIVE_JSON_GEN("android.enableParallelJsonGen", true, FeatureStage.Experimental),
    ENABLE_SIDE_BY_SIDE_CMAKE("android.enableSideBySideCmake", true, FeatureStage.Experimental),
    ENABLE_NATIVE_COMPILER_SETTINGS_CACHE("android.enableNativeCompilerSettingsCache", false, FeatureStage.Experimental),
    ENABLE_CMAKE_BUILD_COHABITATION("android.enableCmakeBuildCohabitation", false, FeatureStage.Experimental),
    ENABLE_PROGUARD_RULES_EXTRACTION("android.proguard.enableRulesExtraction", true, FeatureStage.Experimental),
    USE_DEPENDENCY_CONSTRAINTS("android.dependency.useConstraints", true, FeatureStage.Experimental),
    ENABLE_DUPLICATE_CLASSES_CHECK("android.enableDuplicateClassesCheck", true, FeatureStage.Experimental),
    ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM("android.enableDexingArtifactTransform.desugaring", true, FeatureStage.Experimental),
    GENERATE_R_JAVA("android.generateRJava", false, FeatureStage.Experimental),
    MINIMAL_KEEP_RULES("android.useMinimalKeepRules", true, FeatureStage.Experimental),
    USE_NEW_JAR_CREATOR("android.useNewJarCreator", true, FeatureStage.Experimental),
    USE_NEW_APK_CREATOR("android.useNewApkCreator", true, FeatureStage.Experimental),
    EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES("android.bundle.excludeResSourcesForRelease", true, FeatureStage.Experimental),

    // Options related to new Variant API
    USE_SAFE_PROPERTIES("android.variant.safe.properties", false, FeatureStage.Experimental),

    /* ------------------------
     * SOFTLY-ENFORCED FEATURES
     */

    ENABLE_DESUGAR("android.enableDesugar", true, FeatureStage.SoftlyEnforced(DeprecationReporter.DeprecationTarget.DESUGAR_TOOL)),
    ENABLE_D8("android.enableD8", true, FeatureStage.SoftlyEnforced(DeprecationReporter.DeprecationTarget.LEGACY_DEXER)),

    /** Whether Jetifier will skip libraries that already support AndroidX. */
    JETIFIER_SKIP_IF_POSSIBLE("android.jetifier.skipIfPossible", true, FeatureStage.SoftlyEnforced(DeprecationReporter.DeprecationTarget.IN_A_FUTURE_RELEASE)),

    ; // end of enums

    override val status = stage.status

    override fun parse(value: Any): Boolean {
        return parseBoolean(propertyName, value)
    }
}