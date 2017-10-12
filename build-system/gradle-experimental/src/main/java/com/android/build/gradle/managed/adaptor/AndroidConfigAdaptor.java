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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.build.api.dsl.sourceSets.AndroidSourceDirectorySet;
import com.android.build.api.dsl.sourceSets.AndroidSourceFile;
import com.android.build.api.dsl.sourceSets.AndroidSourceSet;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.VariantFilter;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.managed.AndroidConfig;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.LibraryRequest;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.repository.Revision;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;

/**
 * An adaptor to convert a managed.AndroidConfig to an model.AndroidConfig.
 */
public class AndroidConfigAdaptor implements com.android.build.gradle.AndroidConfig {

    private final AndroidConfig model;
    private NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer;

    public AndroidConfigAdaptor(
            AndroidConfig model,
            NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer) {
        this.model = model;
        this.sourceSetsContainer = sourceSetsContainer;
        applyProjectSourceSet();
    }

    @Override
    public String getBuildToolsVersion() {
        return model.getBuildToolsVersion();
    }

    @Override
    public String getCompileSdkVersion() {
        return model.getCompileSdkVersion();
    }

    @Override
    public Revision getBuildToolsRevision() {
        return model.getBuildToolsRevision();
    }

    @Override
    public CoreProductFlavor getDefaultConfig() {
        return new ProductFlavorAdaptor(model.getDefaultConfig());
    }

    @Override
    @NonNull
    public List<DeviceProvider> getDeviceProviders() {
        return ImmutableList.copyOf(model.getDeviceProviders());
    }

    @Override
    @NonNull
    public List<TestServer> getTestServers() {
        return ImmutableList.copyOf(model.getTestServers());
    }

    @NonNull
    @Override
    public List<Transform> getTransforms() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public List<List<Object>> getTransformsDependencies() {
        return ImmutableList.of();
    }

    @Override
    public String getDefaultPublishConfig() {
        return model.getDefaultPublishConfig();
    }

    @Override
    public Action<VariantFilter> getVariantFilter() {
        return model.getVariantFilter();
    }

    @Override
    public String getResourcePrefix() {
        return model.getResourcePrefix();
    }

    @Override
    public List<String> getFlavorDimensionList() {
        return getProductFlavors().stream()
                .filter(flavor -> flavor.getDimension() != null)
                .map(CoreProductFlavor::getDimension)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public boolean getGeneratePureSplits() {
        return model.getGeneratePureSplits();
    }

    @Override
    public Collection<CoreBuildType> getBuildTypes() {
        return ImmutableList.copyOf(model.getBuildTypes().values().stream()
                .map(BuildTypeAdaptor::new).iterator());
    }

    @Override
    public Collection<CoreProductFlavor> getProductFlavors() {
        return ImmutableList.copyOf(model.getProductFlavors().values().stream()
                .map(ProductFlavorAdaptor::new).iterator());
    }

    @Override
    public Collection<com.android.builder.model.SigningConfig> getSigningConfigs() {
        return ImmutableList.copyOf(model.getSigningConfigs().values().stream()
                .map(SigningConfigAdaptor::new).iterator());
    }

    @Override
    public NamedDomainObjectContainer<AndroidSourceSet> getSourceSets() {
        return sourceSetsContainer;
    }

    @Override
    public Boolean getPackageBuildConfig() {
        return true;
    }

    @Override
    public Boolean getBaseFeature() {
        return model.getBaseFeature();
    }

    public ModelMap<FunctionalSourceSet> getSources() {
        return model.getSources();
    }

    public CoreNdkOptions getNdk() {
        return new NdkOptionsAdaptor(model.getNdk());
    }

    @Override
    public AdbOptions getAdbOptions() {
        return model.getAdbOptions();
    }

    @Override
    public AaptOptions getAaptOptions() {
        return model.getAaptOptions();
    }

    @Override
    public CompileOptions getCompileOptions() {
        return model.getCompileOptions();
    }

    @Override
    public DexOptions getDexOptions() {
        return model.getDexOptions();
    }

    @Override
    public JacocoOptions getJacoco() {
        return model.getJacoco();
    }

    @Override
    public LintOptions getLintOptions() {
        return model.getLintOptions();
    }

    @Override
    public PackagingOptions getPackagingOptions() {
        return model.getPackagingOptions();
    }

    @Override
    public TestOptions getTestOptions() {
        return model.getTestOptions();
    }

    @Override
    public Splits getSplits() {
        return model.getSplits();
    }

    @Override
    public CoreExternalNativeBuild getExternalNativeBuild() {
        return model.getExternalNativeBuild();
    }

    @Override
    public DataBindingOptions getDataBinding() {
        return new DataBindingOptionsAdapter(model.getDataBinding());
    }

    @Override
    public Collection<BaseVariantOutput> getBuildOutputs() {
        ArrayList<BaseVariantOutput> buildOutputs = new ArrayList<>();
        Iterators.addAll(buildOutputs, model.getBuildOutputs().iterator());
        return buildOutputs;
    }

    @Override
    public Collection<LibraryRequest> getLibraryRequests() {
        return model.getLibraryRequests();
    }

    @Override
    public Collection<String> getAidlPackageWhiteList() {
        return ImmutableSet.copyOf(model.getAidlPackageWhitelist());
    }


    private void applyProjectSourceSet() {
        for (String name : getSources().keySet()) {
            FunctionalSourceSet source = getSources().get(name);
            AndroidSourceSet androidSource = name.equals(BuilderConstants.MAIN) ?
                    sourceSetsContainer.maybeCreate(getDefaultConfig().getName()) :
                    sourceSetsContainer.maybeCreate(name);

            convertSourceFile(androidSource.getManifest(), source, "manifest");
            convertSourceSet(androidSource.getResources(), source, "resource");
            convertSourceSet(androidSource.getJava(), source, "java");
            convertSourceSet(androidSource.getRes(), source, "res");
            convertSourceSet(androidSource.getAssets(), source, "assets");
            convertSourceSet(androidSource.getAidl(), source, "aidl");
            convertSourceSet(androidSource.getRenderscript(), source, "renderscript");
            convertSourceSet(androidSource.getJni(), source, "jni");
            convertSourceSet(androidSource.getJniLibs(), source, "jniLibs");
        }
    }

    /**
     * Convert a FunctionalSourceSet to an AndroidSourceFile.
     */
    private static void convertSourceFile(
            AndroidSourceFile androidFile,
            FunctionalSourceSet source,
            String sourceName) {
        LanguageSourceSet languageSourceSet = source.get(sourceName);
        if (languageSourceSet == null) {
            return;
        }
        SourceDirectorySet dir = languageSourceSet.getSource();
        if (dir == null) {
            return;
        }
        // We use the first file in the file tree until Gradle has a way to specify one source file
        // instead of an entire source set.
        Set<File> files = dir.getAsFileTree().getFiles();
        if (!files.isEmpty()) {
            androidFile.srcFile(Iterables.getOnlyElement(files));
        }
    }

    /**
     * Convert a FunctionalSourceSet to an AndroidSourceDirectorySet.
     */
    private static void convertSourceSet(
            AndroidSourceDirectorySet androidDir,
            FunctionalSourceSet source,
            String sourceName) {
        LanguageSourceSet languageSourceSet = source.get(sourceName);
        if (languageSourceSet == null) {
            return;
        }
        SourceDirectorySet dir = languageSourceSet.getSource();
        if (dir == null) {
            return;
        }
        androidDir.setSrcDirs(dir.getSrcDirs());
        androidDir.include(dir.getIncludes());
        androidDir.exclude(dir.getExcludes());
    }
}
