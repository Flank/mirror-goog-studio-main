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
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.file.FileCollection;
import org.gradle.tooling.BuildException;

/**
 * Information about expected Outputs from a build. Expected outputs can contain :
 *
 * <ul>
 *   a single main APK
 * </ul>
 *
 * <ul>
 *   multiple FULL_APKs
 * </ul>
 *
 * <ul>
 *   a single main APK with multiple split APKs
 * </ul>
 */
public class SplitScope implements Serializable {

    private final SplitHandlingPolicy splitHandlingPolicy;
    private final List<ApkData> apkDatas;
    private final SetMultimap<VariantScope.OutputType, SplitOutput> splitOutputs =
            Multimaps.synchronizedSetMultimap(HashMultimap.create());

    public SplitScope(SplitHandlingPolicy splitHandlingPolicy) {
        this.splitHandlingPolicy = splitHandlingPolicy;
        this.apkDatas = new ArrayList<>();
    }

    public SplitScope(SplitHandlingPolicy splitHandlingPolicy, Collection<ApkData> apkDatas) {
        this.splitHandlingPolicy = splitHandlingPolicy;
        this.apkDatas = new ArrayList<>(apkDatas);
    }

    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return splitHandlingPolicy;
    }

    void addSplit(@NonNull ApkData apkData) {
        apkDatas.add(apkData);
    }

    /**
     * Returns the enabled splits for this variant. A split can be disabled due to build
     * optimization.
     *
     * @return list of splits to process for this variant.
     */
    @NonNull
    public List<ApkData> getApkDatas() {
        return apkDatas.stream().filter(ApkData::isEnabled).collect(Collectors.toList());
    }

    @Nullable
    public ApkData getSplit(Collection<FilterData> filters) {
        for (ApkData apkData : apkDatas) {
            if (apkData.getFilters().equals(filters)) {
                return apkData;
            }
        }
        return null;
    }

    @Nullable
    public ApkData getMainSplit() {

        // no ABI specified, look for the main split.
        List<ApkData> splitsByType = getSplitsByType(OutputFile.OutputType.MAIN);
        if (!splitsByType.isEmpty()) {
            return splitsByType.get(0);
        }
        // can't find the main split, look for the universal full split.
        Optional<ApkData> universal =
                getApkDatas()
                        .stream()
                        .filter(split -> split.getFilterName().equals(SplitFactory.UNIVERSAL))
                        .findFirst();
        if (universal.isPresent()) {
            return universal.get();
        }
        // ok look for the first full split, it will do.
        Optional<ApkData> firstFullSplit =
                getApkDatas()
                        .stream()
                        .filter(split -> split.getType() == OutputFile.OutputType.FULL_SPLIT)
                        .findFirst();
        return firstFullSplit.orElse(null);
    }

    @NonNull
    public List<ApkData> getSplitsByType(OutputFile.OutputType outputType) {
        return apkDatas.stream()
                .filter(split -> split.getType() == outputType)
                .collect(Collectors.toList());
    }

    public Stream<ApkData> streamSplits() {
        return apkDatas.stream();
    }

    public void forEach(VariantScope.OutputType outputType, SplitAction action) throws IOException {
        for (ApkData apkData : apkDatas) {
            addOutputForSplit(outputType, apkData, action.processSplit(apkData));
        }
    }

    public void parallelForEach(VariantScope.OutputType outputType, SplitAction action)
            throws IOException {

        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();
        streamSplits()
                .forEach(
                        split ->
                                executor.execute(
                                        () -> {
                                            addOutputForSplit(
                                                    outputType, split, action.processSplit(split));
                                            return null;
                                        }));
        try {
            List<WaitableExecutor.TaskResult<Void>> taskResults = executor.waitForAllTasks();
            taskResults.forEach(
                    taskResult -> {
                        if (taskResult.exception != null) {
                            throw new BuildException(
                                    taskResult.exception.getMessage(), taskResult.exception);
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public void deleteAllEntries(VariantScope.OutputType outputType) {
        splitOutputs.removeAll(outputType);
    }

    public void parallelForEachOutput(
            Collection<SplitOutput> inputs,
            VariantScope.OutputType inputType,
            VariantScope.OutputType outputType,
            SplitOutputAction action) {
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();
        apkDatas.forEach(
                split -> {
                    executor.execute(
                            () -> {
                                SplitOutput splitOutput = getOutput(inputs, inputType, split);
                                if (splitOutput != null) {
                                    addOutputForSplit(
                                            outputType,
                                            split,
                                            action.processSplit(split, splitOutput.getOutputFile()),
                                            splitOutput.getProperties());
                                }
                                return null;
                            });
                });
        try {
            List<WaitableExecutor.TaskResult<Void>> taskResults = executor.waitForAllTasks();
            taskResults.forEach(
                    taskResult -> {
                        if (taskResult.exception != null) {
                            throw new BuildException(
                                    taskResult.exception.getMessage(), taskResult.exception);
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public interface SplitAction {
        @Nullable
        File processSplit(ApkData apkData) throws IOException;
    }

    public interface SplitOutputAction {
        @Nullable
        File processSplit(@NonNull ApkData apkData, @Nullable File output) throws IOException;
    }

    public void save(VariantScope.OutputType outputType, File folder) throws IOException {
        save(ImmutableList.of(outputType), folder);
    }

    public void save(ImmutableList<VariantScope.OutputType> outputTypes, File folder)
            throws IOException {
        String persistedString = persist(outputTypes);
        if (persistedString.isEmpty()) {
            return;
        }
        FileUtils.mkdirs(folder);
        Writer writer = new FileWriter(new File(folder, "output.gson"));
        try {
            writer.append(persistedString);
        } finally {
            writer.close();
        }
    }

    @Nullable
    private static File getOutputFile(FileCollection fileCollection) {
        for (File file : fileCollection.getAsFileTree().getFiles()) {
            if (file.getName().equals("output.gson")) {
                return file;
            }
        }
        return null;
    }

    @NonNull
    public static File getOutputFileLocation(File folder) {
        return new File(folder, "output.gson");
    }

    @Nullable
    private static File getOutputFile(File folder) {
        File outputFile = getOutputFileLocation(folder);
        return outputFile.exists() ? outputFile : null;
    }

    @Nullable
    public SplitOutput getOutputForTesting(
            Collection<SplitOutput> splitOutputs,
            VariantScope.OutputType outputType,
            ApkInfo apkData) {
        return SplitScope.getOutput(splitOutputs, outputType, apkData);

    }

    @Nullable
    public static SplitOutput getOutput(
            Collection<SplitOutput> splitOutputs,
            VariantScope.OutputType outputType,
            ApkInfo apkData) {
        Optional<SplitOutput> matchingOutput =
                splitOutputs
                        .stream()
                        .filter(
                                splitOutput ->
                                        splitOutput.getType() == outputType
                                                && splitOutput.getApkInfo().getType()
                                                        == apkData.getType()
                                                && splitOutput
                                                        .getFilters()
                                                        .equals(apkData.getFilters()))
                        .findFirst();
        return matchingOutput.orElse(null);
    }

    @Nullable
    public static SplitOutput getOutput(
            Collection<SplitOutput> splitOutputs,
            VariantScope.OutputType outputType,
            OutputFile.OutputType apkType) {
        Optional<SplitOutput> matchingOutput =
                splitOutputs
                        .stream()
                        .filter(
                                splitOutput ->
                                        splitOutput.getType() == outputType
                                                && splitOutput.getApkInfo().getType() == apkType)
                        .findFirst();
        return matchingOutput.orElse(null);
    }

    @NonNull
    public Collection<SplitOutput> getOutputs(VariantScope.OutputType outputType) {
        return splitOutputs.get(outputType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SplitScope that = (SplitScope) o;
        return Objects.equals(splitOutputs, that.splitOutputs)
                && Objects.equals(apkDatas, that.apkDatas)
                && splitHandlingPolicy == that.splitHandlingPolicy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), splitOutputs, apkDatas, splitHandlingPolicy);
    }

    public static final class SplitOutput implements OutputFile, Serializable {
        private final VariantScope.OutputType outputType;
        private final ApkInfo apkInfo;
        private final File outputFile;
        @NonNull private final Map<String, String> properties;

        public SplitOutput(VariantScope.OutputType outputType, ApkInfo apkInfo, File outputFile) {
            this.outputType = outputType;
            this.apkInfo = apkInfo;
            this.outputFile = outputFile;
            this.properties = ImmutableMap.of();
        }

        public SplitOutput(
                VariantScope.OutputType outputType,
                ApkData apkData,
                File outputFile,
                Map<String, String> properties) {
            this.outputType = outputType;
            this.apkInfo = apkData;
            this.outputFile = outputFile;
            this.properties = properties;
        }

        public ApkInfo getApkInfo() {
            return apkInfo;
        }

        @NonNull
        @Override
        public File getOutputFile() {
            return new File(outputFile.getPath());
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("apkInfo", apkInfo)
                    .add("outputFile", outputFile)
                    .add("properties", Joiner.on(",").join(properties.entrySet()))
                    .toString();
        }

        public VariantScope.OutputType getType() {
            return outputType;
        }

        // TODO : DELETE.
        @NonNull
        @Override
        public String getOutputType() {
            return apkInfo.getType().toString();
        }

        @NonNull
        @Override
        public Collection<String> getFilterTypes() {
            return apkInfo.getFilters()
                    .stream()
                    .map(FilterData::getFilterType)
                    .collect(Collectors.toList());
        }

        @NonNull
        @Override
        public Collection<FilterData> getFilters() {
            return apkInfo.getFilters();
        }

        @NonNull
        @Override
        public OutputFile getMainOutputFile() {
            return this;
        }

        @NonNull
        @Override
        public Collection<? extends OutputFile> getOutputs() {
            return ImmutableList.of(this);
        }

        @Override
        public int getVersionCode() {
            return apkInfo.getVersionCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SplitOutput that = (SplitOutput) o;
            return outputType == that.outputType
                    && Objects.equals(properties, that.properties)
                    && Objects.equals(apkInfo, that.apkInfo)
                    && Objects.equals(outputFile, that.outputFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outputType, apkInfo, outputFile, properties);
        }

        @NonNull
        public Map<String, String> getProperties() {
            return properties;
        }
    }

    public void addOutputForSplit(
            VariantScope.OutputType outputType, ApkData apkData, @Nullable File outputFile) {
        if (outputFile != null) {
            splitOutputs.put(outputType, new SplitOutput(outputType, apkData, outputFile));
        }
    }

    public void addOutputForSplit(
            VariantScope.OutputType outputType,
            ApkData apkData,
            @Nullable File outputFile,
            Map<String, String> properties) {

        if (outputFile != null) {
            splitOutputs.put(
                    outputType, new SplitOutput(outputType, apkData, outputFile, properties));
        }
    }

    public String persist(ImmutableList<VariantScope.OutputType> outputTypes) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ApkInfo.class, new ApkInfoAdapter());
        gsonBuilder.registerTypeAdapter(
                VariantScope.TaskOutputType.class, new OutputTypeTypeAdapter());
        gsonBuilder.registerTypeAdapter(
                VariantScope.AnchorOutputType.class, new OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        List<SplitOutput> splitOutputs =
                outputTypes
                        .stream()
                        .map(this.splitOutputs::get)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
        return gson.toJson(splitOutputs);
    }

    public static Collection<SplitOutput> load(VariantScope.OutputType type, File outputDirectory) {
        return load(ImmutableList.of(type), outputDirectory);
    }

    public static Collection<SplitOutput> load(
            Collection<VariantScope.OutputType> types, File outputDirectory) {
        File metadataFile = getOutputFile(outputDirectory);
        if (metadataFile == null || !metadataFile.exists()) {
            return ImmutableList.of();
        }
        try {
            return load(types, new FileReader(metadataFile));
        } catch (FileNotFoundException e) {
            return ImmutableList.of();
        }
    }

    public static Collection<SplitOutput> load(
            VariantScope.OutputType outputType, FileCollection fileCollection) {
        return load(ImmutableList.of(outputType), fileCollection);
    }

    public static Collection<SplitOutput> load(
            Collection<VariantScope.OutputType> outputTypes, FileCollection fileCollection) {
        File metadataFile = getOutputFile(fileCollection);
        if (metadataFile == null || !metadataFile.exists()) {
            return ImmutableList.of();
        }
        try {
            return load(outputTypes, new FileReader(metadataFile));
        } catch (FileNotFoundException e) {
            return ImmutableList.of();
        }
    }

    public static Collection<SplitOutput> load(
            Collection<VariantScope.OutputType> outputTypes, Reader reader) {
        return load(reader)
                .stream()
                .filter(splitOutput -> outputTypes.contains(splitOutput.getType()))
                .collect(Collectors.toList());
    }

    public static Collection<SplitOutput> load(Reader reader) {
        GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ApkInfo.class, new ApkInfoAdapter());
        gsonBuilder.registerTypeAdapter(VariantScope.OutputType.class, new OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        Type recordType = new TypeToken<List<SplitOutput>>() {}.getType();
        return gson.fromJson(reader, recordType);
    }

    public static Collection<SplitOutput> load(File metadataFile) throws FileNotFoundException {
        return load(new FileReader(metadataFile));
    }

    private static class ApkInfoAdapter extends TypeAdapter<ApkInfo> {

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

    private static class OutputTypeTypeAdapter extends TypeAdapter<VariantScope.OutputType> {

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
