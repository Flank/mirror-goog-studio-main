/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import java.io.Serializable;
import java.util.Collection;

/**
 * Represents all the static information about an APK, like its type and the filters associated with
 * it.
 */
public interface ApkInfo extends Serializable {

    /** Returns the output type of the referenced APK. */
    @NonNull
    OutputFile.OutputType getType();

    /** Returns all the split information used to create the APK. */
    @NonNull
    Collection<FilterData> getFilters();

    @Nullable
    FilterData getFilter(@NonNull VariantOutput.FilterType filterType);

    /**
     * Returns the version code for this output.
     *
     * <p>This is convenient method that returns the final version code whether it's coming from the
     * override set in the output or from the variant's merged flavor.
     *
     * @return the version code.
     */
    int getVersionCode();

    @Nullable
    String getVersionName();

    boolean isEnabled();

    @Nullable
    String getOutputFileName();

    boolean requiresAapt();

    @Nullable
    String getFilterName();

    @Nullable
    String getFullName();

    @NonNull
    String getBaseName();

    static ApkInfo of(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode) {
        return of(outputType, filters, versionCode, null, null, null, "", "", true);
    }

    static ApkInfo of(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode,
            @Nullable String versionName,
            @Nullable String filterName,
            @Nullable String outputFileName,
            @NonNull String fullName,
            @NonNull String baseName,
            boolean enabled) {
        return new DefaultApkData(
                outputType,
                filters,
                versionCode,
                versionName,
                filterName,
                outputFileName,
                fullName,
                baseName,
                enabled);
    }
}
