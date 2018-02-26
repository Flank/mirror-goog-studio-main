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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;

/**
 * Singleton object per variant that holds the list of splits declared by the DSL or discovered.
 */
public class SplitList {

    public static final String RESOURCE_CONFIGS = "ResConfigs";

    /**
     * Split list cache, valid only during this build.
     */
    private ImmutableList<Record> records;

    public static final SplitList EMPTY = new SplitList(ImmutableList.of());

    private SplitList(List<Record> records) {
        this.records = ImmutableList.copyOf(records);
    }

    @NonNull
    public static SplitList load(@NonNull FileCollection persistedList) throws IOException {
        String persistedData = FileUtils.readFileToString(persistedList.getSingleFile());
        Gson gson = new Gson();
        Type collectionType = new TypeToken<ArrayList<Record>>() {}.getType();
        return new SplitList(gson.fromJson(persistedData, collectionType));
    }

    public Set<String> getFilters(OutputFile.FilterType splitType) {
        return getFilters(splitType.name());
    }

    public synchronized Set<String> getFilters(String filterType) {
        Optional<Record> record =
                records.stream().filter(r -> r.splitType.equals(filterType)).findFirst();
        return record.isPresent() ? record.get().getValues() : ImmutableSet.of();
    }

    public interface SplitAction {
        void apply(OutputFile.FilterType filterType, Collection<Filter> filters);
    }

    public void forEach(SplitAction action) {
        records.forEach(
                record -> {
                    if (record.isConfigSplit() && !record.getValues().isEmpty()) {
                        action.apply(
                                OutputFile.FilterType.valueOf(record.splitType), record.filters);
                    }
                });
    }

    public Set<String> getResourcesSplit() {
        ImmutableSet.Builder<String> allFilters = ImmutableSet.builder();
        allFilters.addAll(getFilters(OutputFile.FilterType.DENSITY));
        allFilters.addAll(getFilters(OutputFile.FilterType.LANGUAGE));
        return allFilters.build();
    }

    @NonNull
    public static Set<String> getSplits(
            @NonNull SplitList splitList, @NonNull MultiOutputPolicy multiOutputPolicy) {
        return multiOutputPolicy == MultiOutputPolicy.SPLITS
                ? splitList.getResourcesSplit()
                : ImmutableSet.of();
    }

    public static synchronized void save(
            @NonNull File outputFile,
            @NonNull Collection<Filter> densityFilters,
            @NonNull Collection<Filter> languageFilters,
            @NonNull Collection<Filter> abiFilters,
            @NonNull Collection<Filter> resourceConfigs)
            throws IOException {

        ImmutableList<Record> records =
                ImmutableList.of(
                        new Record(OutputFile.FilterType.DENSITY.name(), densityFilters),
                        new Record(OutputFile.FilterType.LANGUAGE.name(), languageFilters),
                        new Record(OutputFile.FilterType.ABI.name(), abiFilters),
                        new Record(RESOURCE_CONFIGS, resourceConfigs));

        Gson gson = new Gson();
        String listOfFilters = gson.toJson(records);
        FileUtils.write(outputFile, listOfFilters);
    }

    /**
     * Internal records to save split names and types.
     */
    private static final class Record {
        private final String splitType;
        private final Collection<Filter> filters;

        private Record(String splitType, Collection<Filter> filters) {
            this.splitType = splitType;

            Set<String> filterValues = new HashSet<>();
            filters.forEach(
                    filter -> {
                        Preconditions.checkState(!filterValues.contains(filter.getValue()));
                        filterValues.add(filter.getValue());
                    });

            this.filters = filters;
        }

        private boolean isConfigSplit() {
            return !splitType.equals(RESOURCE_CONFIGS);
        }

        public Set<String> getValues() {
            return filters.stream().map(filter -> filter.value).collect(Collectors.toSet());
        }
    }

    public static final class Filter {
        @NonNull private final String value;
        @Nullable private final String simplifiedName;

        public Filter(@NonNull String value, @Nullable String simplifiedName) {
            this.value = value;
            this.simplifiedName = simplifiedName;
        }

        public Filter(@NonNull String value) {
            this(value, null);
        }

        @NonNull
        public String getValue() {
            return value;
        }

        @NonNull
        public String getDisplayName() {
            return simplifiedName != null ? simplifiedName : value;
        }
    }
}
