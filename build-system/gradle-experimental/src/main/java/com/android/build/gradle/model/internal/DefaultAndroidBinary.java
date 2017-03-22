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

package com.android.build.gradle.model.internal;

import com.android.build.gradle.internal.NdkOptionsHelper;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.managed.BuildType;
import com.android.build.gradle.managed.NdkConfig;
import com.android.build.gradle.managed.NdkOptions;
import com.android.build.gradle.managed.ProductFlavor;
import com.android.build.gradle.model.NdkConfigImpl;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.platform.base.binary.BaseBinarySpec;

/**
 * Binary for Android.
 */
public class DefaultAndroidBinary extends BaseBinarySpec implements AndroidBinaryInternal {

    private BuildType buildType;

    private List<ProductFlavor> productFlavors;

    private NdkConfig mergedNdkConfig = new NdkConfigImpl();

    private Collection<BaseVariantData> variantDataList;

    private List<NativeLibraryBinarySpec> nativeBinaries = Lists.newArrayList();

    private List<String> targetAbi = Lists.newArrayList();

    @Override
    public BuildType getBuildType() {
        return buildType;
    }

    @Override
    public void setBuildType(BuildType buildType) {
        this.buildType = buildType;
    }

    @Override
    public List<ProductFlavor> getProductFlavors() {
        return productFlavors;
    }

    @Override
    public void setProductFlavors(List<ProductFlavor> productFlavors) {
        this.productFlavors = productFlavors;
    }

    @Override
    public NdkConfig getMergedNdkConfig() {
        return mergedNdkConfig;
    }

    @Override
    public Collection<BaseVariantData> getVariantData() {
        return variantDataList;
    }

    @Override
    public void setVariantData(Collection<BaseVariantData> variantDataList) {
        this.variantDataList = variantDataList;
    }

    @Override
    public List<NativeLibraryBinarySpec> getNativeBinaries() {
        return nativeBinaries;
    }

    @Override
    public List<String> getTargetAbi() {
        return targetAbi;
    }

    @Override
    public void computeMergedNdk(
            NdkConfig ndkConfig,
            List<com.android.build.gradle.managed.ProductFlavor> flavors,
            com.android.build.gradle.managed.BuildType buildType) {


        if (ndkConfig != null) {
            NdkOptionsHelper.merge(mergedNdkConfig, ndkConfig);
        }

        for (int i = flavors.size() - 1 ; i >= 0 ; i--) {
            NdkOptions ndkOptions = flavors.get(i).getNdk();
            if (ndkOptions != null) {
                NdkOptionsHelper.merge(mergedNdkConfig, ndkOptions);
            }
        }

        if (buildType.getNdk() != null) {
            NdkOptionsHelper.merge(mergedNdkConfig, buildType.getNdk());
        }
    }
}
