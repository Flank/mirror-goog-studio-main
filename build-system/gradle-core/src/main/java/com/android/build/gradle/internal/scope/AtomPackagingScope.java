/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.ApiVersion;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableSet;

import org.gradle.api.Project;

import java.io.File;
import java.util.Set;

/**
 * Packaging scope for an atom.
 */
public class AtomPackagingScope implements PackagingScope {

    protected final VariantOutputScope variantOutputScope;
    protected final VariantScope variantScope;
    protected final GlobalScope globalScope;
    protected final AndroidAtom androidAtom;

    public AtomPackagingScope(@NonNull VariantOutputScope variantOutputScope,
            @NonNull AndroidAtom androidAtom) {
        this.variantOutputScope = variantOutputScope;
        this.variantScope = variantOutputScope.getVariantScope();
        this.globalScope = variantScope.getGlobalScope();
        this.androidAtom = androidAtom;
    }

    @NonNull
    @Override
    public AndroidBuilder getAndroidBuilder() {
        return globalScope.getAndroidBuilder();
    }

    @NonNull
    @Override
    public File getFinalResourcesFile() {
        return variantOutputScope.getProcessResourcePackageOutputFile(androidAtom);
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return variantScope.getFullVariantName();
    }

    @NonNull
    @Override
    public ApiVersion getMinSdkVersion() {
        return variantScope.getMinSdkVersion();
    }

    @NonNull
    @Override
    public InstantRunBuildContext getInstantRunBuildContext() {
        return variantScope.getInstantRunBuildContext();
    }

    @NonNull
    @Override
    public File getInstantRunSupportDir() {
        return variantScope.getInstantRunSupportDir();
    }

    @NonNull
    @Override
    public File getIncrementalDir(@NonNull String name) {
        return variantScope.getIncrementalDir(name);
    }

    @NonNull
    @Override
    public Set<File> getDexFolders() {
        return ImmutableSet.of(variantScope.getDexOutputFolder(androidAtom));
    }

    @NonNull
    @Override
    public Set<File> getJavaResources() {
        return ImmutableSet.of(androidAtom.getJavaResFolder());
    }

    @NonNull
    @Override
    public File getAssetsDir() {
        return androidAtom.getAssetsFolder();
    }

    @NonNull
    @Override
    public Set<File> getJniFolders() {
        return ImmutableSet.of(androidAtom.getLibFolder());
    }

    @NonNull
    @Override
    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return SplitHandlingPolicy.PRE_21_POLICY;
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public ApkOutputFile getMainOutputFile() {
        return variantOutputScope.getMainOutputFile();
    }

    @Nullable
    @Override
    public Set<String> getSupportedAbis() {
        return null;
    }

    @Override
    public boolean isDebuggable() {
        return variantScope.getVariantConfiguration().getBuildType().isDebuggable();
    }

    @Override
    public boolean isJniDebuggable() {
        return variantScope.getVariantConfiguration().getBuildType().isJniDebuggable();
    }

    @Nullable
    @Override
    public CoreSigningConfig getSigningConfig() {
        return variantScope.getVariantConfiguration().getSigningConfig();
    }

    @NonNull
    @Override
    public PackagingOptions getPackagingOptions() {
        return globalScope.getExtension().getPackagingOptions();
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String name) {
        return getTaskName(name, "");
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return variantScope.getTaskName(prefix,
                StringHelper.capitalize(androidAtom.getAtomName()) + suffix);
    }

    @NonNull
    @Override
    public Project getProject() {
        return globalScope.getProject();
    }

    @NonNull
    @Override
    public File getOutputPackage() {
        return variantScope.getPackageAtom(androidAtom);
    }

    @NonNull
    @Override
    public File getIntermediateApk() {
        return variantOutputScope.getIntermediateApk();
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return variantScope.getInstantRunSplitApkOutputFolder();
    }

    @Nullable
    @Override
    public File getAtomMetadataBaseFolder() {
        return androidAtom.getAtomMetadataFile().getParentFile();
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return variantScope.getVariantConfiguration().getApplicationId();
    }

    @Override
    public int getVersionCode() {
        return 0;
    }

    @Nullable
    @Override
    public String getVersionName() {
        return null;
    }

    @NonNull
    @Override
    public AaptOptions getAaptOptions() {
        return globalScope.getExtension().getAaptOptions();
    }

    @NonNull
    @Override
    public VariantType getVariantType() {
        return VariantType.ATOM;
    }

    @NonNull
    @Override
    public File getManifestFile() {
        // TODO: Replace with an empty manifest.
        return androidAtom.getManifest();
    }

}
