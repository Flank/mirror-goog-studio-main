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
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.VariantFilter;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.builder.core.LibraryRequest;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.SigningConfig;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.repository.Revision;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

/**
 * User configuration settings for all android plugins.
 */
public interface AndroidConfig {

    String CONFIG_DESC = "%s dependencies for '%s' sources.";
    String CONFIG_DESC_OLD = "%s dependencies for '%s' sources (deprecated: use '%s' instead).";
    String DEPRECATED_CONFIG_WARNING = "Configuration '%s' in project '%s' is deprecated. Use '%s' instead.";

    /** Build tools version. */
    String getBuildToolsVersion();

    /** Compile SDK version. */
    String getCompileSdkVersion();

    /** Build tool revisions. */
    Revision getBuildToolsRevision();

    /** Name of the variant to publish. */
    String getDefaultPublishConfig();

    /** Filter to determine which variants to build. */
    Action<VariantFilter> getVariantFilter();

    /** Adb options. */
    AdbOptions getAdbOptions();

    /** A prefix to be used when creating new resources. Used by Android Studio. */
    String getResourcePrefix();

    /** List of flavor dimensions. */
    List<String> getFlavorDimensionList();

    /** Whether to generate pure splits or multi apk. */
    boolean getGeneratePureSplits();

    /** Default config, shared by all flavors. */
    CoreProductFlavor getDefaultConfig();

    /** Options for aapt, tool for packaging resources. */
    AaptOptions getAaptOptions();

    /** Compile options. */
    CompileOptions getCompileOptions();

    /** Dex options. */
    DexOptions getDexOptions();

    /** JaCoCo options. */
    JacocoOptions getJacoco();

    /** Lint options. */
    LintOptions getLintOptions();

    /** External native build options. */
    CoreExternalNativeBuild getExternalNativeBuild();

    /** Packaging options. */
    PackagingOptions getPackagingOptions();

    /**
     * APK splits options.
     *
     * <p>See <a href="https://developer.android.com/studio/build/configure-apk-splits.html">APK Splits</a>.
     */
    Splits getSplits();

    /** Options for running tests. */
    TestOptions getTestOptions();

    /** List of device providers */
    @NonNull
    List<DeviceProvider> getDeviceProviders();

    /** List of remote CI servers. */
    @NonNull
    List<TestServer> getTestServers();

    @NonNull
    List<Transform> getTransforms();
    @NonNull
    List<List<Object>> getTransformsDependencies();

    /** All product flavors used by this project. */
    Collection<? extends CoreProductFlavor> getProductFlavors();

    /** Build types used by this project. */
    Collection<? extends CoreBuildType> getBuildTypes();

    /** Signing configs used by this project. */
    Collection<? extends SigningConfig> getSigningConfigs();

    /** Source sets for all variants. */
    NamedDomainObjectContainer<AndroidSourceSet> getSourceSets();

    /** build outputs for all variants */
    Collection<BaseVariantOutput> getBuildOutputs();

    /** Whether to package build config class file. */
    Boolean getPackageBuildConfig();

    /** Aidl files to package in the aar. */
    Collection<String> getAidlPackageWhiteList();

    Collection<LibraryRequest> getLibraryRequests();

    /** Data Binding options. */
    DataBindingOptions getDataBinding();

    /** Whether the feature module is the base feature. */
    Boolean getBaseFeature();

    @NonNull
    Map<String, Map<String, List<String>>> getFlavorAttrMap();

    @NonNull
    Map<String, List<String>> getBuildTypeAttrMap();

    final class DeprecatedConfigurationAction implements Action<Dependency> {

        @NonNull
        private final Project project;
        @NonNull
        private final Configuration configuration;
        @NonNull
        private final String replacement;
        private boolean warningPrintedAlready = false;

        public DeprecatedConfigurationAction(
                @NonNull Project project,
                @NonNull Configuration configuration,
                @NonNull String replacement) {
            this.project = project;
            this.configuration = configuration;
            this.replacement = replacement;
        }

        @Override
        public void execute(Dependency dependency) {
            if (!warningPrintedAlready) {
                warningPrintedAlready = true;
                System.out.println(String.format(
                        DEPRECATED_CONFIG_WARNING,
                        configuration.getName(),
                        project.getPath(),
                        replacement));
            }
        }
    }
}
