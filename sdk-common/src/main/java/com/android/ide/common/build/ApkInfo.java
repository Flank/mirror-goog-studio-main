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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.google.common.base.MoreObjects;
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

    @Nullable
    String getFilterName();

    static ApkInfo of(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode) {
        return of(outputType, filters, versionCode, null, true, null);
    }

    static ApkInfo of(
            @NonNull OutputFile.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode,
            @Nullable String versionName,
            boolean enabled,
            @Nullable String outputFileName) {
        return new DefaultApkInfo(
                outputType, filters, versionCode, versionName, outputFileName, enabled);
    }

    class DefaultApkInfo implements ApkInfo {

        private final VariantOutput.OutputType outputType;
        private final Collection<FilterData> filters;
        private final int versionCode;
        private final String versionName;
        private final String outputFileName;
        private final boolean enabled;

        public DefaultApkInfo(
                VariantOutput.OutputType outputType,
                Collection<FilterData> filters,
                int versionCode,
                String versionName,
                String outputFileName,
                boolean enabled) {
            this.outputType = outputType;
            this.filters = filters;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.outputFileName = outputFileName;
            this.enabled = enabled;
        }

        @NonNull
        @Override
        public OutputFile.OutputType getType() {
            return outputType;
        }

        @NonNull
        @Override
        public Collection<FilterData> getFilters() {
            return filters;
        }

        @Nullable
        @Override
        public String getFilterName() {
            return null;
        }

        @Override
        public int getVersionCode() {
            return versionCode;
        }

        @Nullable
        @Override
        public String getVersionName() {
            return versionName;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Nullable
        @Override
        public String getOutputFileName() {
            return outputFileName;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("type", outputType)
                    .add("versionCode", versionCode)
                    .add("versionName", versionName)
                    .add("enabled", enabled)
                    .add("outputFileName", outputFileName)
                    .add("filters", filters)
                    .toString();
        }
    }
}
