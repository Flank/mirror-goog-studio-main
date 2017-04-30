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
import com.android.annotations.Nullable;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.CoreShaderOptions;
import com.android.build.gradle.managed.ProductFlavor;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.VectorDrawablesOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;

/**
 * An adaptor to convert a ProductFlavor to CoreProductFlavor.
 */
public class ProductFlavorAdaptor extends BaseConfigAdaptor implements CoreProductFlavor {

    @NonNull
    private final ProductFlavor productFlavor;

    public ProductFlavorAdaptor(@NonNull ProductFlavor productFlavor) {
        super(productFlavor);
        this.productFlavor = productFlavor;
    }

    @NonNull
    @Override
    public String getName() {
        return productFlavor.getName().equals("defaultConfig") ? BuilderConstants.MAIN : productFlavor.getName();
    }

    @Nullable
    @Override
    public String getDimension() {
        return productFlavor.getDimension();
    }

    @Nullable
    @Override
    public String getApplicationId() {
        return productFlavor.getApplicationId();
    }

    @Nullable
    @Override
    public Integer getVersionCode() {
        return productFlavor.getVersionCode();
    }

    @Nullable
    @Override
    public String getVersionName() {
        return productFlavor.getVersionName();
    }

    @Nullable
    @Override
    public ApiVersion getMinSdkVersion() {
        return ApiVersionAdaptor.isEmpty(productFlavor.getMinSdkVersion()) ?
                null :
                new ApiVersionAdaptor(productFlavor.getMinSdkVersion());
    }

    @Nullable
    @Override
    public ApiVersion getTargetSdkVersion() {
        return ApiVersionAdaptor.isEmpty(productFlavor.getTargetSdkVersion()) ?
                null :
                new ApiVersionAdaptor(productFlavor.getTargetSdkVersion());
    }

    @Nullable
    @Override
    public Integer getMaxSdkVersion() {
        return productFlavor.getMaxSdkVersion();
    }

    @Nullable
    @Override
    public Integer getRenderscriptTargetApi() {
        return productFlavor.getRenderscriptTargetApi();
    }

    @Nullable
    @Override
    public Boolean getRenderscriptSupportModeEnabled() {
        return productFlavor.getRenderscriptSupportModeEnabled();
    }

    @Nullable
    @Override
    public Boolean getRenderscriptSupportModeBlasEnabled() {
        return productFlavor.getRenderscriptSupportModeBlasEnabled();
    }


    @Nullable
    @Override
    public Boolean getRenderscriptNdkModeEnabled() {
        return productFlavor.getRenderscriptNdkModeEnabled();
    }

    @Nullable
    @Override
    public String getTestApplicationId() {
        return productFlavor.getTestApplicationId();
    }

    @Nullable
    @Override
    public String getTestInstrumentationRunner() {
        return productFlavor.getTestInstrumentationRunner();
    }

    @NonNull
    @Override
    public Map<String, String> getTestInstrumentationRunnerArguments() {
        // TODO: To be implemented.
        return Maps.newHashMap();
    }

    @Nullable
    @Override
    public Boolean getTestHandleProfiling() {
        return productFlavor.getTestHandleProfiling();
    }

    @Nullable
    @Override
    public Boolean getTestFunctionalTest() {
        return productFlavor.getTestFunctionalTest();
    }

    @NonNull
    @Override
    public Collection<String> getResourceConfigurations() {
        return ImmutableList.copyOf(productFlavor.getResourceConfigurations());
    }

    @Nullable
    @Override
    public SigningConfig getSigningConfig() {
        return productFlavor.getSigningConfig() == null ?
                null :
                new SigningConfigAdaptor(productFlavor.getSigningConfig());
    }

    @NonNull
    @Override
    public VectorDrawablesOptions getVectorDrawables() {
        return productFlavor.getVectorDrawables();
    }

    @Nullable
    @Override
    public Boolean getWearAppUnbundled() {
        return productFlavor.getWearAppUnbundled();
    }

    @Override
    public CoreNdkOptions getNdkConfig() {
        return new NdkOptionsAdaptor(productFlavor.getNdk());
    }

    @Nullable
    @Override
    public CoreExternalNativeBuildOptions getExternalNativeBuildOptions() {
        return new ExternalNativeBuildOptionsAdaptor(productFlavor.getExternalNativeBuild());
    }

    @NonNull
    @Override
    public CoreJackOptions getJackOptions() {
        return new JackOptionsAdaptor(productFlavor.getJackOptions());
    }

    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
        return new JavaCompileOptionsAdaptor(productFlavor.getJavaCompileOptions());
    }

    @NonNull
    @Override
    public CoreShaderOptions getShaders() {
        return new ShaderOptionsAdaptor(productFlavor.getShaders());
    }
}
