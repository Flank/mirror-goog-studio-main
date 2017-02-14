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
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.ide.common.build.Split;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
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
    private final List<Split> splits;
    private final SetMultimap<VariantScope.OutputType, SplitOutput> splitOutputs =
            Multimaps.synchronizedSetMultimap(HashMultimap.create());

    public SplitScope(SplitHandlingPolicy splitHandlingPolicy) {
        this.splitHandlingPolicy = splitHandlingPolicy;
        this.splits = new ArrayList<>();
    }

    public SplitScope(SplitHandlingPolicy splitHandlingPolicy, Collection<Split> splits) {
        this.splitHandlingPolicy = splitHandlingPolicy;
        this.splits = new ArrayList<>(splits);
    }

    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return splitHandlingPolicy;
    }

    void addSplit(@NonNull Split split) {
        splits.add(split);
    }

    /**
     * Returns the enabled splits for this variant. A split can be disabled due to build
     * optimization.
     *
     * @return list of splits to process for this variant.
     */
    @NonNull
    public List<Split> getSplits() {
        return splits.stream().filter(Split::isEnabled).collect(Collectors.toList());
    }

    @Nullable
    public Split getSplit(ImmutableList<FilterData> filters) {
        for (Split split : splits) {
            if (split.getFilters().equals(filters)) {
                return split;
            }
        }
        return null;
    }

    @Nullable
    public Split getMainSplit() {

        // no ABI specified, look for the main split.
        List<Split> splitsByType = getSplitsByType(OutputFile.OutputType.MAIN);
        if (!splitsByType.isEmpty()) {
            return splitsByType.get(0);
        }
        // can't find the main split, look for the universal full split.
        Optional<Split> universal =
                getSplits()
                        .stream()
                        .filter(split -> split.getFilterName().equals(SplitFactory.UNIVERSAL))
                        .findFirst();
        if (universal.isPresent()) {
            return universal.get();
        }
        // ok look for the first full split, it will do.
        Optional<Split> firstFullSplit =
                getSplits()
                        .stream()
                        .filter(split -> split.getType() == OutputFile.OutputType.FULL_SPLIT)
                        .findFirst();
        return firstFullSplit.orElse(null);
    }

    @NonNull
    public List<Split> getSplitsByType(OutputFile.OutputType outputType) {
        return splits.stream()
                .filter(split -> split.getType() == outputType)
                .collect(Collectors.toList());
    }

    public Stream<Split> streamSplits() {
        return splits.stream();
    }

    public void forEach(VariantScope.OutputType outputType, SplitAction action) throws IOException {
        for (Split split : splits) {
            addOutputForSplit(outputType, split, action.processSplit(split));
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
            VariantScope.OutputType inputType,
            VariantScope.OutputType outputType,
            SplitOutputAction action) {
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();
        splits.forEach(
                split -> {
                    executor.execute(
                            () -> {
                                SplitOutput splitOutput = getOutput(inputType, split);
                                if (splitOutput != null) {
                                    addOutputForSplit(
                                            outputType,
                                            split,
                                            action.processSplit(
                                                    split, splitOutput.getOutputFile()));
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
        File processSplit(Split split) throws IOException;
    }

    public interface SplitOutputAction {
        @Nullable
        File processSplit(@NonNull Split split, @Nullable File output) throws IOException;
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
    public File getOutputFile(FileCollection fileCollection) {
        for (File file : fileCollection.getAsFileTree().getFiles()) {
            if (file.getName().equals("output.gson")) {
                return file;
            }
        }
        return null;
    }

    @Nullable
    public File getOutputFile(File folder) {
        File outputFile = new File(folder, "output.gson");
        return outputFile.exists() ? outputFile : null;
    }

    @Nullable
    public SplitOutput getOutput(VariantScope.OutputType outputType, Split split) {
        for (SplitOutput splitOutput : getOutputs(outputType)) {
            if (splitOutput.getSplit().equals(split)) {
                return splitOutput;
            }
        }
        return null;
    }

    @Nullable
    public SplitOutput getOutput(VariantScope.OutputType outputType, OutputFile.OutputType type) {
        for (SplitOutput splitOutput : getOutputs(outputType)) {
            if (splitOutput.getSplit().getType() == type) {
                return splitOutput;
            }
        }
        return null;
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
                && Objects.equals(splits, that.splits)
                && splitHandlingPolicy == that.splitHandlingPolicy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), splitOutputs, splits, splitHandlingPolicy);
    }

    public static final class SplitOutput implements OutputFile, Serializable {
        private final VariantScope.OutputType outputType;
        private final Split split;
        private final File outputFile;

        public SplitOutput(VariantScope.OutputType outputType, Split split, File outputFile) {
            this.outputType = outputType;
            this.split = split;
            this.outputFile = outputFile;
        }

        public Split getSplit() {
            return split;
        }

        @NonNull
        @Override
        public File getOutputFile() {
            return outputFile;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("split", split)
                    .add("outputFile", outputFile)
                    .toString();
        }

        @NonNull
        @Override
        public String getOutputType() {
            return split.getOutputType();
        }

        @NonNull
        @Override
        public Collection<String> getFilterTypes() {
            return split.getFilterTypes();
        }

        @NonNull
        @Override
        public Collection<FilterData> getFilters() {
            return split.getFilters();
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
            return split.getVersionCode();
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
                    && Objects.equals(split, that.split)
                    && Objects.equals(outputFile, that.outputFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outputType, split, outputFile);
        }
    }

    public void addOutputForSplit(
            VariantScope.OutputType outputType, Split split, @Nullable File outputFile) {
        if (outputFile != null) {
            splitOutputs.put(outputType, new SplitOutput(outputType, split, outputFile));
        }
    }

    public String persist(ImmutableList<VariantScope.OutputType> outputTypes) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(
                Split.class, new SplitAdapter(this, null /* splitCreator */));
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

    public boolean load(VariantScope.OutputType type, File outputDirectory) {
        return load(ImmutableList.of(type), outputDirectory);
    }

    public boolean load(Collection<VariantScope.OutputType> types, File outputDirectory) {
        File metadataFile = new File(outputDirectory, "output.gson");
        if (metadataFile.exists()) {
            return loadMetadata(types, metadataFile);
        }
        return false;
    }

    public boolean load(
            Collection<VariantScope.OutputType> types,
            File folderOrFile,
            SplitCreator splitCreator) {
        File metadataFile =
                folderOrFile.isDirectory() ? new File(folderOrFile, "output.gson") : folderOrFile;
        if (metadataFile.exists()) {
            try {
                load(types, new FileReader(metadataFile), splitCreator);
                return true;
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public boolean load(VariantScope.OutputType outputType, FileCollection fileCollection) {
        return load(
                ImmutableList.of(outputType),
                fileCollection,
                (SplitCreator) (outputType1, filters) -> null);
    }

    public boolean load(
            Collection<VariantScope.OutputType> outputTypes,
            FileCollection fileCollection,
            SplitCreator splitCreator) {
        File metadataFile = getOutputFile(fileCollection);
        if (metadataFile == null || !metadataFile.exists()) {
            return false;
        }
        return load(outputTypes, metadataFile, splitCreator);
    }

    private boolean loadMetadata(
            Collection<VariantScope.OutputType> outputTypes, File metadataFile) {
        try {
            load(outputTypes, new FileReader(metadataFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public void load(VariantScope.OutputType outputType, Reader reader) {
        load(ImmutableList.of(outputType), reader);
    }

    public interface SplitCreator extends Serializable {
        @Nullable
        Split create(
                @NonNull VariantOutput.OutputType outputType,
                @NonNull Collection<FilterData> filters);
    }

    public void load(Collection<VariantScope.OutputType> outputTypes, Reader reader) {
        load(outputTypes, reader, (SplitCreator) (outputType, filters) -> null);
    }

    public void load(
            Collection<VariantScope.OutputType> outputTypes,
            Reader reader,
            SplitCreator configurationSplitCreator) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(
                Split.class, new SplitAdapter(this, configurationSplitCreator));
        gsonBuilder.registerTypeAdapter(VariantScope.OutputType.class, new OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        Type recordType = new TypeToken<List<SplitOutput>>() {}.getType();
        List<SplitOutput> records = gson.fromJson(reader, recordType);
        for (SplitOutput record : records) {
            if (record.split != null && outputTypes.contains(record.outputType)) {
                addOutputForSplit(
                        record.outputType, record.split, new File(record.outputFile.getPath()));
            }
        }
    }

    private static class SplitAdapter extends TypeAdapter<Split> {

        private final SplitScope splitScope;
        private final SplitCreator splitCreator;

        private SplitAdapter(SplitScope splitScope, SplitCreator splitCreator) {
            this.splitScope = splitScope;
            this.splitCreator = splitCreator;
        }

        @Override
        public void write(JsonWriter out, Split value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("type").value(value.getClass().getSimpleName());
            out.name("splits").beginArray();
            for (FilterData filter : value.getFilters()) {
                out.beginObject();
                out.name("filterType").value(filter.getFilterType());
                out.name("value").value(filter.getIdentifier());
                out.endObject();
            }
            out.endArray();
            out.endObject();
        }

        @Override
        public Split read(JsonReader in) throws IOException {
            in.beginObject();
            String simpleClassName = null;
            ImmutableList.Builder<FilterData> filters = ImmutableList.builder();

            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "type":
                        simpleClassName = in.nextString();
                        break;
                    case "splits":
                        readFilters(in, filters);
                        break;
                }
            }
            in.endObject();

            if (simpleClassName != null
                    && simpleClassName.equals(SplitFactory.DefaultSplit.class.getSimpleName())) {
                return splitCreator.create(VariantOutput.OutputType.SPLIT, filters.build());
            } else {
                // should we make sure the split type has not changed, does it matter ?
                return splitScope.getSplit(filters.build());
            }
        }

        private void readFilters(JsonReader in, ImmutableList.Builder<FilterData> filters)
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
