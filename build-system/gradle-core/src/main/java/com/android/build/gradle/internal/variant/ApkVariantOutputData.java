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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.stream.Collectors;

/** Base output data for a variant that generates an APK file. */
public class ApkVariantOutputData extends BaseVariantOutputData {

    private int versionCodeOverride = -1;
    private String versionNameOverride = null;

    public ApkVariantOutputData(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            @NonNull BaseVariantData variantData) {
        super(outputType, filters, variantData);
    }

    @NonNull
    @Override
    public ImmutableList<ApkOutputFile> getOutputs() {
        return ImmutableList.copyOf(
                variantData
                        .getScope()
                        .getSplitScope()
                        .getOutputs(VariantScope.TaskOutputType.APK)
                        .stream()
                        .map(
                                splitOutput ->
                                        new ApkOutputFile(
                                                splitOutput.getApkInfo().getType(),
                                                splitOutput
                                                        .getApkInfo()
                                                        .getFilters()
                                                        .stream()
                                                        .map(
                                                                filter ->
                                                                        new FilterDataImpl(
                                                                                filter
                                                                                        .getFilterType(),
                                                                                filter
                                                                                        .getIdentifier()))
                                                        .collect(Collectors.toList()),
                                                splitOutput::getOutputFile,
                                                splitOutput.getApkInfo().getVersionCode()))
                        .collect(Collectors.toList()));
    }

    @Override
    public int getVersionCode() {
        if (versionCodeOverride > 0) {
            return versionCodeOverride;
        }

        return variantData.getVariantConfiguration().getVersionCode();
    }

    public String getVersionName() {
        if (versionNameOverride != null) {
            return versionNameOverride;
        }

        return variantData.getVariantConfiguration().getVersionName();
    }

    public void setVersionCodeOverride(int versionCodeOverride) {
        this.versionCodeOverride = versionCodeOverride;
    }

    public int getVersionCodeOverride() {
        return versionCodeOverride;
    }

    public void setVersionNameOverride(String versionNameOverride) {
        this.versionNameOverride = versionNameOverride;
    }

    public String getVersionNameOverride() {
        return versionNameOverride;
    }


}
