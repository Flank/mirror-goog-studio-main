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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.SigningConfig;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class BuildTypeStub extends BaseConfigStub implements BuildType {
    private final boolean myDebuggable;
    private final boolean myJniDebuggable;
    private final boolean myRenderscriptDebuggable;
    private final int myRenderscriptOptimLevel;
    private final boolean myMinifyEnabled;
    private final boolean myZipAlignEnabled;

    public BuildTypeStub() {
        super();
        myDebuggable = true;
        myJniDebuggable = true;
        myRenderscriptDebuggable = true;
        myRenderscriptOptimLevel = 1;
        myMinifyEnabled = true;
        myZipAlignEnabled = true;
    }

    public BuildTypeStub(
            @NonNull String name,
            @NonNull Map<String, ClassField> buildConfigFields,
            @NonNull Map<String, ClassField> resValues,
            @NonNull Map<String, String> flavorSelections,
            @NonNull Collection<File> proguardFiles,
            @NonNull Collection<File> consumerProguardFiles,
            @NonNull Collection<File> testProguardFiles,
            @NonNull Map<String, Object> manifestPlaceholders,
            @Nullable String applicationIdSuffix,
            @Nullable String versionNameSuffix,
            @Nullable Boolean multiDexEnabled,
            @Nullable File multiDexKeepFile,
            @Nullable File multiDexKeepProguard,
            boolean debuggable,
            boolean jniDebuggable,
            boolean renderscriptDebuggable,
            int level,
            boolean minifyEnabled,
            boolean zipAlignEnabled) {
        super(
                name,
                buildConfigFields,
                resValues,
                flavorSelections,
                proguardFiles,
                consumerProguardFiles,
                testProguardFiles,
                manifestPlaceholders,
                applicationIdSuffix,
                versionNameSuffix,
                multiDexEnabled,
                multiDexKeepFile,
                multiDexKeepProguard);
        myDebuggable = debuggable;
        myJniDebuggable = jniDebuggable;
        myRenderscriptDebuggable = renderscriptDebuggable;
        myRenderscriptOptimLevel = level;
        myMinifyEnabled = minifyEnabled;
        myZipAlignEnabled = zipAlignEnabled;
    }

    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        throw new UnusedModelMethodException("getSigningConfig");
    }

    @Override
    public boolean isDebuggable() {
        return myDebuggable;
    }

    @Override
    public boolean isTestCoverageEnabled() {
        throw new UnusedModelMethodException("isTestCoverageEnabled");
    }

    @Override
    public boolean isPseudoLocalesEnabled() {
        throw new UnusedModelMethodException("isPseudoLocalesEnabled");
    }

    @Override
    public boolean isJniDebuggable() {
        return myJniDebuggable;
    }

    @Override
    public boolean isRenderscriptDebuggable() {
        return myRenderscriptDebuggable;
    }

    @Override
    public int getRenderscriptOptimLevel() {
        return myRenderscriptOptimLevel;
    }

    @Override
    public boolean isMinifyEnabled() {
        return myMinifyEnabled;
    }

    @Override
    public boolean isZipAlignEnabled() {
        return myZipAlignEnabled;
    }

    @Override
    public boolean isEmbedMicroApp() {
        throw new UnusedModelMethodException("isEmbedMicroApp");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BuildType)) {
            return false;
        }
        BuildType that = (BuildType) o;
        //noinspection deprecation
        return Objects.equals(getName(), that.getName())
                && Objects.equals(getResValues(), that.getResValues())
                && Objects.equals(getProguardFiles(), that.getProguardFiles())
                && Objects.equals(getConsumerProguardFiles(), that.getConsumerProguardFiles())
                && Objects.equals(getManifestPlaceholders(), that.getManifestPlaceholders())
                && Objects.equals(getApplicationIdSuffix(), that.getApplicationIdSuffix())
                && Objects.equals(getVersionNameSuffix(), that.getVersionNameSuffix())
                && isDebuggable() == that.isDebuggable()
                && isJniDebuggable() == that.isJniDebuggable()
                && isRenderscriptDebuggable() == that.isRenderscriptDebuggable()
                && getRenderscriptOptimLevel() == that.getRenderscriptOptimLevel()
                && isMinifyEnabled() == that.isMinifyEnabled()
                && isZipAlignEnabled() == that.isZipAlignEnabled();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getResValues(),
                getProguardFiles(),
                getConsumerProguardFiles(),
                getManifestPlaceholders(),
                getApplicationIdSuffix(),
                getVersionNameSuffix(),
                isDebuggable(),
                isJniDebuggable(),
                isRenderscriptDebuggable(),
                getRenderscriptOptimLevel(),
                isMinifyEnabled(),
                isZipAlignEnabled());
    }

    @Override
    public String toString() {
        return "BuildTypeStub{"
                + "myDebuggable="
                + myDebuggable
                + ", myJniDebuggable="
                + myJniDebuggable
                + ", myRenderscriptDebuggable="
                + myRenderscriptDebuggable
                + ", myRenderscriptOptimLevel="
                + myRenderscriptOptimLevel
                + ", myMinifyEnabled="
                + myMinifyEnabled
                + ", myZipAlignEnabled="
                + myZipAlignEnabled
                + "} "
                + super.toString();
    }
}
