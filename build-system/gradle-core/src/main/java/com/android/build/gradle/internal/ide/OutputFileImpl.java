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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of the {@link OutputFile} interface for the model.
 */
@Immutable
public final class OutputFileImpl implements OutputFile, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final Collection<FilterData> filters;
    @NonNull
    private final Collection<String> filterTypes;
    @NonNull
    private final String type;
    @NonNull
    private final File outputFile;

    public OutputFileImpl(
            @NonNull Collection<FilterData> filters,
            @NonNull String type,
            @NonNull File file) {
        this.filters = filters;
        this.type = type;
        ImmutableList.Builder<String> filterTypes = ImmutableList.builder();
        for (FilterData filter : filters) {
            filterTypes.add(filter.getFilterType());
        }
        this.filterTypes = filterTypes.build();
        this.outputFile = file;
    }

    @NonNull
    @Override
    public String getOutputType() {
        return type;
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return filterTypes;
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return filters;
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OutputFileImpl that = (OutputFileImpl) o;
        return Objects.equals(filters, that.filters) &&
                Objects.equals(filterTypes, that.filterTypes) &&
                Objects.equals(type, that.type) &&
                Objects.equals(outputFile, that.outputFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filters, filterTypes, type, outputFile);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("filters", filters)
                .add("filterTypes", filterTypes)
                .add("type", type)
                .add("outputFile", outputFile)
                .toString();
    }
}
