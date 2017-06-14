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

package com.android.build.gradle.managed;

import com.android.build.api.variant.VariantFilter;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.builder.core.LibraryRequest;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.repository.Revision;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.model.Managed;
import org.gradle.model.ModelMap;
import org.gradle.model.Unmanaged;

/**
 * Component model for all Android plugin.
 */
@Managed
public interface AndroidConfig {

    /** Build tool version */
    String getBuildToolsVersion();
    void setBuildToolsVersion(String buildToolsVersion);

    /** Compile SDK version */
    String getCompileSdkVersion();
    void setCompileSdkVersion(String compileSdkVersion);

    /** Build tool revisions */
    @Unmanaged
    Revision getBuildToolsRevision();
    void setBuildToolsRevision(Revision revision);

    /** Default config, shared by all flavors. */
    ProductFlavor getDefaultConfig();

    /** NDK config for specific ABI. */
    ModelMap<NdkAbiOptions> getAbis();

    /** List of device providers */
    @Unmanaged
    Collection<DeviceProvider> getDeviceProviders();
    void setDeviceProviders(Collection<DeviceProvider> providers);

    /** List of remote CI servers */
    @Unmanaged
    Collection<TestServer> getTestServers();
    void setTestServers(Collection<TestServer> providers);

    /** Name of the variant to publish */
    String getDefaultPublishConfig();
    void setDefaultPublishConfig(String defaultPublishConfig);

    /** Filter to determine which variants to build */
    @Unmanaged
    Action<VariantFilter> getVariantFilter();
    void setVariantFilter(Action<VariantFilter> filter);

    /** A prefix to be used when creating new resources. Used by Studio */
    String getResourcePrefix();
    void setResourcePrefix(String resourcePrefix);

    /** Whether to generate pure splits or multi apk */
    Boolean getGeneratePureSplits();
    void setGeneratePureSplits(Boolean generateSplits);

    /** Build types used by this project. */
    ModelMap<BuildType> getBuildTypes();

    /** All product flavors used by this project. */
    ModelMap<ProductFlavor> getProductFlavors();

    /** Signing configs used by this project. */
    ModelMap<SigningConfig> getSigningConfigs();

    /** Android source sets. */
    ModelMap<FunctionalSourceSet> getSources();

    ModelMap<BaseVariantOutput> getBuildOutputs();

    NdkConfig getNdk();

    /** Adb options. */
    @Unmanaged
    AdbOptions getAdbOptions();
    void setAdbOptions(AdbOptions adbOptions);

    /** Options for aapt, tool for packaging resources. */
    @Unmanaged
    AaptOptions getAaptOptions();
    void setAaptOptions(AaptOptions aaptOptions);

    /** Compile options. */
    @Unmanaged
    CompileOptions getCompileOptions();
    void setCompileOptions(CompileOptions compileOptions);

    /** Dex options. */
    @Unmanaged
    DexOptions getDexOptions();
    void setDexOptions(DexOptions dexOptions);

    /** JaCoCo options. */
    @Unmanaged
    JacocoOptions getJacoco();
    void setJacoco(JacocoOptions jacoco);

    /** Lint options. */
    @Unmanaged
    LintOptions getLintOptions();
    void setLintOptions(LintOptions lintOptions);

    /** ExternalNativeBuild options. */
    CoreExternalNativeBuild getExternalNativeBuild();

    /** Packaging options. */
    @Unmanaged
    PackagingOptions getPackagingOptions();
    void setPackagingOptions(PackagingOptions packagingOptions);

    /** Options for running tests. */
    @Unmanaged
    TestOptions getTestOptions();
    void setTestOptions(TestOptions testOptions);

    /** APK splits. */
    @Unmanaged
    Splits getSplits();
    void setSplits(Splits splits);

    @Unmanaged
    Collection<LibraryRequest> getLibraryRequests();
    void setLibraryRequests(Collection<LibraryRequest> libraryRequests);

    Set<String> getAidlPackageWhitelist();

    DataBindingOptions getDataBinding();

    Boolean getBaseFeature();

    void setBaseFeature(Boolean baseFeature);
}
