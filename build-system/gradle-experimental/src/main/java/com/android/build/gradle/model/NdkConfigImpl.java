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

package com.android.build.gradle.model;

import com.android.annotations.NonNull;
import com.android.build.gradle.managed.NdkConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

/**
 * Implementation of NdkConfig.
 * Used in AndroidBinary, which is currently not a Managed type.
 */
public class NdkConfigImpl implements NdkConfig {

    String moduleName;

    String platformVersion;

    String toolchain;

    String toolchainVersion;

    Boolean useUnifiedHeaders;

    Set<String> abiFilters = Sets.newHashSet();

    List<String> cFlags = Lists.newArrayList();

    List<String> cppFlags = Lists.newArrayList();

    List<String> ldFlags = Lists.newArrayList();

    List<String> ldLibs = Lists.newArrayList();

    String stl;

    String stlVersion;

    Boolean isDebuggable;

    Boolean renderscriptNdkMode;

    @Override
    public String getModuleName() {
        return moduleName;
    }

    @Override
    public void setModuleName(@NonNull String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    public String getPlatformVersion() {
        return platformVersion;
    }

    @Override
    public void setPlatformVersion(@NonNull String platformVersion) {
        this.platformVersion = platformVersion;
    }

    @Override
    public String getToolchain() {
        return toolchain;
    }

    @Override
    public void setToolchain(@NonNull String toolchain) {
        this.toolchain = toolchain;
    }

    @Override
    public String getToolchainVersion() {
        return toolchainVersion;
    }

    @Override
    public void setToolchainVersion(@NonNull String toolchainVersion) {
        this.toolchainVersion = toolchainVersion;
    }

    @Override
    public Boolean getUseUnifiedHeaders() {
        return useUnifiedHeaders;
    }

    @Override
    public void setUseUnifiedHeaders(@NonNull Boolean useUnifiedHeaders) {
        this.useUnifiedHeaders = useUnifiedHeaders;
    }

    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @Override
    public List<String> getCFlags() {
        return cFlags;
    }

    @Override
    public List<String> getCppFlags() {
        return cppFlags;
    }

    @Override
    public List<String> getLdFlags() {
        return ldFlags;
    }

    @Override
    public List<String> getLdLibs() {
        return ldLibs;
    }

    @Override
    public String getStl() {
        return stl;
    }

    @Override
    public void setStl(@NonNull String stl) {
        this.stl = stl;
    }

    @Override
    public String getStlVersion() {
        return this.stlVersion;
    }

    @Override
    public void setStlVersion(String stlVersion) {
        this.stlVersion = stlVersion;
    }

    @Override
    public Boolean getDebuggable() {
        return isDebuggable;
    }

    @Override
    public void setDebuggable(Boolean isDebuggable) {
        this.isDebuggable = isDebuggable;
    }

    @Override
    public Boolean getRenderscriptNdkMode() {
        return renderscriptNdkMode;
    }

    @Override
    public void setRenderscriptNdkMode(Boolean renderscriptNdkMode) {
        this.renderscriptNdkMode = renderscriptNdkMode;
    }
}
