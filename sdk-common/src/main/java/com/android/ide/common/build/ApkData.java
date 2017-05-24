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
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * FIX ME : wrong name but convenient until I can clean up existing classes/namespace.
 *
 * <p>This split represents a Variant output, which can be a main (base) split, a full split, a
 * configuration pure splits. Each variant has one to many of such outputs depending on its
 * configuration.
 *
 * <p>this is used to model outputs of a variant during configuration and it is sometimes altered at
 * execution when new pure splits are discovered.
 */
public abstract class ApkData implements ApkInfo, VariantOutput {

    // TO DO : move it to a subclass, we cannot override versions for SPLIT
    private String versionName;
    private int versionCode;
    private AtomicBoolean enabled = new AtomicBoolean(true);
    private String outputFileName;


    public ApkData() {}

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return getFilters().stream().map(FilterData::getFilterType).collect(Collectors.toList());
    }

    // FIX-ME: we can have more than one value, especially for languages...
    // so far, we will return things like "fr,fr-rCA" for a single value.
    @Nullable
    public String getFilter(FilterType filterType) {
        return ApkData.getFilter(getFilters(), filterType);
    }

    @Nullable
    public String getFilter(String filterType) {
        return ApkData.getFilter(getFilters(), FilterType.valueOf(filterType));
    }

    public boolean requiresAapt() {
        return true;
    }

    public abstract String getFilterName();

    public abstract String getBaseName();

    public abstract String getFullName();

    @Override
    public abstract OutputType getType();

    /**
     * Returns a directory name relative to a variant specific location to save split specific
     * output files or null to use the variant specific folder.
     *
     * @return a directory name of null.
     */
    @NonNull
    public abstract String getDirName();

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public void setOutputFileName(@NonNull String outputFileName) {
        this.outputFileName = outputFileName;
    }

    @Override
    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    @NonNull
    public String getOutputFileName() {
        return outputFileName;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", getType())
                .add("fullName", getFullName())
                .add("filters", getFilters())
                .toString();
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        throw new UnsupportedOperationException(
                "getMainOutputFile is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @NonNull
    @Override
    public Collection<? extends OutputFile> getOutputs() {
        throw new UnsupportedOperationException(
                "getOutputs is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @NonNull
    @Override
    public String getOutputType() {
        return getType().name();
    }

    // FIX-ME: we can have more than one value, especially for languages...
    // so far, we will return things like "fr,fr-rCA" for a single value.
    @Nullable
    public static String getFilter(
            Collection<FilterData> filters, OutputFile.FilterType filterType) {

        for (FilterData filter : filters) {
            if (VariantOutput.FilterType.valueOf(filter.getFilterType()) == filterType) {
                return filter.getIdentifier();
            }
        }
        return null;
    }

    public void disable() {
        enabled.set(false);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApkData that = (ApkData) o;
        return versionCode == that.versionCode
                && Objects.equals(outputFileName, that.outputFileName)
                && Objects.equals(versionName, that.versionName)
                && Objects.equals(enabled.get(), that.enabled.get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionCode, enabled.get(), versionName, outputFileName);
    }
}
