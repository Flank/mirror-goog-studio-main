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

package com.android.build.gradle.internal.variant;

import static com.android.SdkConstants.DOT_RES;
import static com.android.SdkConstants.FN_RES_BASE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.api.ApkOutputFile;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;

/**
 * Output Data about a variant that generates an Atom file.
 */
public class AtomVariantOutputData extends BaseVariantOutputData {

    AtomVariantOutputData(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            @NonNull BaseVariantData variantData) {
        super(outputType, filters, variantData);
    }

    @Override
    public void setOutputFile(@NonNull File file) {
        if (packageAndroidArtifactTask != null) {
            packageAndroidArtifactTask.setOutputFile(file);
        }
    }

    @Nullable
    @Override
    public File getOutputFile() {
        return packageAndroidArtifactTask == null ? null : packageAndroidArtifactTask.getOutputFile();
    }

    @NonNull
    @Override
    public ImmutableList<ApkOutputFile> getOutputs() {
        return ImmutableList.of();
    }

    @Override
    public int getVersionCode() {
        return variantData.getVariantConfiguration().getVersionCode();
    }

    public String getVersionName() {
        return variantData.getVariantConfiguration().getVersionName();
    }

    @NonNull
    @Override
    public File getProcessResourcePackageOutputFile() {
        return new File(getScope().getVariantScope().getBaseBundleDir(), FN_RES_BASE + DOT_RES);
    }

}
