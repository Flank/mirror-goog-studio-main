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
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private final SetMultimap<VariantScope.OutputType, BuildOutput> splitOutputs =
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

    public void parallelForEach(VariantScope.OutputType outputType, SplitAction action)
            throws IOException {

        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();
        apkDatas.forEach(
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
            Collection<BuildOutput> inputs,
            VariantScope.OutputType inputType,
            VariantScope.OutputType outputType,
            SplitOutputAction action) {

        parallelForEachOutput(
                inputs,
                inputType,
                outputType,
                (ParameterizedSplitOutputAction<Void>)
                        (apkData, output, param) -> action.processSplit(apkData, output),
                null);
    }

    public <T> void parallelForEachOutput(
            Collection<BuildOutput> inputs,
            VariantScope.OutputType inputType,
            VariantScope.OutputType outputType,
            ParameterizedSplitOutputAction<T> action,
            T parameter) {

        parallelForEachOutput(
                inputs,
                inputType,
                outputType,
                (TwoParameterizedSplitOutputAction<T, Void>)
                        (apkData, output, paramOne, paramTwo) ->
                                action.processSplit(apkData, output, paramOne),
                parameter,
                null);
    }

    public <T, U> void parallelForEachOutput(
            Collection<BuildOutput> inputs,
            VariantScope.OutputType inputType,
            VariantScope.OutputType outputType,
            TwoParameterizedSplitOutputAction<T, U> action,
            T parameterOne,
            U parameterTwo) {
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();
        apkDatas.forEach(
                split -> {
                    executor.execute(
                            () -> {
                                BuildOutput buildOutput = getOutput(inputs, inputType, split);
                                if (buildOutput != null) {
                                    addOutputForSplit(
                                            outputType,
                                            split,
                                            action.processSplit(
                                                    split,
                                                    buildOutput.getOutputFile(),
                                                    parameterOne,
                                                    parameterTwo),
                                            buildOutput.getProperties());
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

    public interface ParameterizedSplitOutputAction<T> {
        @Nullable
        File processSplit(@NonNull ApkData apkData, @Nullable File output, T param)
                throws IOException;
    }

    public interface TwoParameterizedSplitOutputAction<T, U> {
        @Nullable
        File processSplit(@NonNull ApkData apkData, @Nullable File output, T paramOne, U paramTwo)
                throws IOException;
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
        Writer writer = new FileWriter(BuildOutputs.getMetadataFile(folder));
        try {
            writer.append(persistedString);
        } finally {
            writer.close();
        }
    }

    @Nullable
    public static BuildOutput getOutput(
            Collection<BuildOutput> buildOutputs,
            VariantScope.OutputType outputType,
            ApkInfo apkData) {
        Optional<BuildOutput> matchingOutput =
                buildOutputs
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
    public static BuildOutput getOutput(
            Collection<BuildOutput> buildOutputs,
            VariantScope.OutputType outputType,
            OutputFile.OutputType apkType) {
        Optional<BuildOutput> matchingOutput =
                buildOutputs
                        .stream()
                        .filter(
                                splitOutput ->
                                        splitOutput.getType() == outputType
                                                && splitOutput.getApkInfo().getType() == apkType)
                        .findFirst();
        return matchingOutput.orElse(null);
    }

    @NonNull
    public Collection<BuildOutput> getOutputs(VariantScope.OutputType outputType) {
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

    public void addOutputForSplit(
            VariantScope.OutputType outputType, ApkData apkData, @Nullable File outputFile) {
        if (outputFile != null) {
            splitOutputs.put(outputType, new BuildOutput(outputType, apkData, outputFile));
        }
    }

    public void addOutputForSplit(
            VariantScope.OutputType outputType,
            ApkData apkData,
            @Nullable File outputFile,
            Map<String, String> properties) {

        if (outputFile != null) {
            splitOutputs.put(
                    outputType, new BuildOutput(outputType, apkData, outputFile, properties));
        }
    }

    public String persist(ImmutableList<VariantScope.OutputType> outputTypes) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ApkInfo.class, new BuildOutputs.ApkInfoAdapter());
        gsonBuilder.registerTypeAdapter(
                VariantScope.TaskOutputType.class, new BuildOutputs.OutputTypeTypeAdapter());
        gsonBuilder.registerTypeAdapter(
                VariantScope.AnchorOutputType.class, new BuildOutputs.OutputTypeTypeAdapter());
        Gson gson = gsonBuilder.create();
        List<BuildOutput> buildOutputs =
                outputTypes
                        .stream()
                        .map(this.splitOutputs::get)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
        return gson.toJson(buildOutputs);
    }
}
