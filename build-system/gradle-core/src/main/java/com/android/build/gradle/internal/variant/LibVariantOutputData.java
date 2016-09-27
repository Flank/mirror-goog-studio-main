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
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Output Data about a variant that produce a Library bundle (.aar)
 */
public class LibVariantOutputData extends BaseVariantOutputData {

    public Zip packageLibTask;

    LibVariantOutputData(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            @NonNull BaseVariantData variantData) {
        super(outputType, filters, variantData);
    }

    @Override
    public void setOutputFile(@NonNull File file) {
        packageLibTask.setDestinationDir(file.getParentFile());
        packageLibTask.setArchiveName(file.getName());
    }



    @NonNull
    @Override
    public ImmutableList<ApkOutputFile> getOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return packageLibTask == null
                ? getScope().getVariantScope().getOutputBundleFile()
                : packageLibTask.getArchivePath();
    }

    @Override
    public int getVersionCode() {
        return 0;
    }
}
