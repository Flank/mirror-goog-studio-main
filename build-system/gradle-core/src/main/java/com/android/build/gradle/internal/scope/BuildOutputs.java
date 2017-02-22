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
import com.android.annotations.VisibleForTesting;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.android.ide.common.build.ApkInfo;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;

/** Services related to loading and handling {@link BuildOutput} */
public class BuildOutputs {

    /**
     * loads persisted build output data from a metadata file located in the passed directory.
     *
     * @param folder the directory containing the metadata file. If there is no metadata file in
     *     that folder, an empty collection is returned.
     * @return a possibly empty collection of {@link BuildOutput}.
     */
    public static Collection<BuildOutput> load(@NonNull File folder) {
        File metadataFile = getMetadataFileIfPresent(folder);
        if (metadataFile == null || !metadataFile.exists()) {
            return ImmutableList.of();
        }
        try {
            return load(new FileReader(metadataFile));
        } catch (FileNotFoundException e) {
            return ImmutableList.of();
        }
    }

    /**
     * loads persisted build output data from a metadata file located in the passed directory.
     *
     * @param fileCollection the directory containing the metadata file. If there is no metadata
     *     file in that fileCollection, an empty collection is returned.
     * @return a possibly empty collection of {@link BuildOutput}.
     */
    public static Collection<BuildOutput> load(@NonNull FileCollection fileCollection) {
        File metadataFile = getMetadataFileIfPresent(fileCollection);
        if (metadataFile == null || !metadataFile.exists()) {
            return ImmutableList.of();
        }
        try {
            return load(new FileReader(metadataFile));
        } catch (FileNotFoundException e) {
            return ImmutableList.of();
        }
    }

    /**
     * loads persisted build output data from a metadata file located in the passed directory.
     *
     * @param types the list of {@link VariantScope.OutputType} that should be loaded. If the
     *     metadata file contains other types that those present in this collection, they will be
     *     ignored and the build outputs will not be included in the returned collection.
     * @param folder the directory in which the build outputs metadata file should be located. If
     *     there is no metadata file in that folder, an empty collection is returned.
     * @return a collection of BuildOutput of the provided types.
     */
    @NonNull
    public static Collection<BuildOutput> load(
            @NonNull Collection<VariantScope.OutputType> types, @NonNull File folder) {
        File metadataFile = getMetadataFileIfPresent(folder);
        if (metadataFile == null || !metadataFile.exists()) {
            return ImmutableList.of();
        }
        try {
            return load(types, new FileReader(metadataFile));
        } catch (FileNotFoundException e) {
            return ImmutableList.of();
        }
    }

    /**
     * loads persisted build output data from a metadata file located in the passed directory.
     *
     * @param type the {@link VariantScope.OutputType} that should be loaded. If the metadata file
     *     contains other types that this parameter value, they will be ignored and the build
     *     outputs will not be included in the returned collection.
     * @param folder the directory in which the build outputs metadata file should be located. If
     *     there is no metadata file in that folder, an empty collection is returned.
     * @return a collection of BuildOutput of the provided type.
     */
    @NonNull
    public static Collection<BuildOutput> load(
            @NonNull VariantScope.OutputType type, @NonNull File folder) {
        return load(ImmutableList.of(type), folder);
    }

    /**
     * loads persisted build output data from a metadata file located in the passed directory.
     *
     * @param type the {@link VariantScope.OutputType} that should be loaded. If the metadata file
     *     contains other types that those present in this collection, they will be ignored and the
     *     build outputs will not be included in the returned collection.
     * @param fileCollection the gradle {@link FileCollection} that should contain the build outputs
     *     and the metdata file for this outputs.
     * @return a collection of BuildOutput of the provided type.
     */
    @NonNull
    public static Collection<BuildOutput> load(
            @NonNull VariantScope.OutputType type, @NonNull FileCollection fileCollection) {
        return load(ImmutableList.of(type), fileCollection);
    }

    @NonNull
    @VisibleForTesting
    static Collection<BuildOutput> load(
            @NonNull Collection<VariantScope.OutputType> outputTypes, @NonNull Reader reader) {
        return load(reader)
                .stream()
                .filter(splitOutput -> outputTypes.contains(splitOutput.getType()))
                .collect(Collectors.toList());
    }

    @Nullable
    private static File getMetadataFileIfPresent(@NonNull FileCollection fileCollection) {
        for (File file : fileCollection.getAsFileTree().getFiles()) {
            if (file.getName().equals("output.json")) {
                return file;
            }
        }
        return null;
    }

    @NonNull
    public static File getMetadataFile(@NonNull File folder) {
        return new File(folder, "output.json");
    }

    @NonNull
    private static Collection<BuildOutput> load(
            @NonNull Collection<VariantScope.OutputType> outputTypes,
            @NonNull FileCollection fileCollection) {
        File metadataFile = getMetadataFileIfPresent(fileCollection);
        if (metadataFile == null || !metadataFile.exists()) {
            return ImmutableList.of();
        }
        try {
            return load(outputTypes, new FileReader(metadataFile));
        } catch (FileNotFoundException e) {
            return ImmutableList.of();
        }
    }

    @Nullable
    private static File getMetadataFileIfPresent(@NonNull File folder) {
        File outputFile = BuildOutputs.getMetadataFile(folder);
        return outputFile.exists() ? outputFile : null;
    }

    @NonNull
    private static Collection<BuildOutput> load(@NonNull Reader reader) {
        GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ApkInfo.class, new ApkInfoAdapter());
        gsonBuilder.registerTypeAdapter(VariantScope.OutputType.class, new OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        Type recordType = new TypeToken<List<BuildOutput>>() {}.getType();
        return gson.fromJson(reader, recordType);
    }

    static class ApkInfoAdapter extends TypeAdapter<ApkInfo> {

        @Override
        public void write(JsonWriter out, ApkInfo value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("type").value(value.getType().toString());
            out.name("splits").beginArray();
            for (FilterData filter : value.getFilters()) {
                out.beginObject();
                out.name("filterType").value(filter.getFilterType());
                out.name("value").value(filter.getIdentifier());
                out.endObject();
            }
            out.endArray();
            out.name("versionCode").value(value.getVersionCode());
            out.endObject();
        }

        @Override
        public ApkInfo read(JsonReader in) throws IOException {
            in.beginObject();
            String outputType = null;
            ImmutableList.Builder<FilterData> filters = ImmutableList.builder();
            int versionCode = 0;

            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "type":
                        outputType = in.nextString();
                        break;
                    case "splits":
                        readFilters(in, filters);
                        break;
                    case "versionCode":
                        versionCode = in.nextInt();
                        break;
                }
            }
            in.endObject();

            return ApkInfo.of(
                    OutputFile.OutputType.valueOf(outputType), filters.build(), versionCode);
        }

        private static void readFilters(JsonReader in, ImmutableList.Builder<FilterData> filters)
                throws IOException {

            in.beginArray();
            while (in.hasNext()) {
                in.beginObject();
                OutputFile.FilterType filterType = null;
                String value = null;
                while (in.hasNext()) {
                    switch (in.nextName()) {
                        case "filterType":
                            filterType = OutputFile.FilterType.valueOf(in.nextString());
                            break;
                        case "value":
                            value = in.nextString();
                            break;
                    }
                }
                if (filterType != null && value != null) {
                    filters.add(new FilterDataImpl(filterType, value));
                }
                in.endObject();
            }
            in.endArray();
        }
    }

    static class OutputTypeTypeAdapter extends TypeAdapter<VariantScope.OutputType> {

        @Override
        public void write(JsonWriter out, VariantScope.OutputType value) throws IOException {
            out.beginObject();
            out.name("type").value(value.name());
            out.endObject();
        }

        @Override
        public VariantScope.OutputType read(JsonReader in) throws IOException {
            in.beginObject();
            if (!in.nextName().endsWith("type")) {
                throw new IOException("Invalid format");
            }
            String nextString = in.nextString();
            VariantScope.OutputType outputType;
            try {
                outputType = VariantScope.TaskOutputType.valueOf(nextString);
            } catch (IllegalArgumentException e) {
                outputType = VariantScope.AnchorOutputType.valueOf(nextString);
            }
            in.endObject();
            return outputType;
        }
    }
}
