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
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * Base Implementation of TaskOutputHolder.
 */
public abstract class TaskOutputHolderImpl implements TaskOutputHolder {
    private final Map<OutputType, FileCollection> outputMap = Maps.newHashMap();

    protected abstract Project getProject();

    @NonNull
    @Override
    public FileCollection getOutputs(@NonNull OutputType outputType) {
        FileCollection fileCollection = outputMap.get(outputType);
        if (fileCollection == null) {
            throw new IllegalStateException("No output of type: " + outputType.toString());
        }
        return fileCollection;
    }

    @Override
    public boolean hasOutput(@NonNull OutputType outputType) {
        return outputMap.containsKey(outputType);
    }

    @Override
    public ConfigurableFileCollection addTaskOutput(
            @NonNull TaskOutputType outputType, @NonNull File file, @NonNull String taskName) {
        ConfigurableFileCollection collection = createCollection(file, taskName);
        addTaskOutput(outputType, collection);
        return collection;
    }

    @Override
    public void addTaskOutput(@NonNull TaskOutputType outputType,
            @NonNull FileCollection fileCollection) {
        if (outputMap.containsKey(outputType)) {
            throw new IllegalStateException("Output already registered for type: " + outputType);
        }

        outputMap.put(outputType, fileCollection);
    }

    @NonNull
    @Override
    public FileCollection createAnchorOutput(@NonNull AnchorOutputType outputType) {
        if (outputMap.containsKey(outputType)) {
            throw new IllegalStateException("Anchor Output already created for type: " + outputType);
        }

        FileCollection fileCollection = getProject().files();
        outputMap.put(outputType, fileCollection);

        return fileCollection;
    }

    @Override
    public void addToAnchorOutput(
            @NonNull AnchorOutputType outputType,
            @NonNull File file,
            @NonNull String taskName) {
        addToAnchorOutput(outputType, createCollection(file, taskName));
    }

    @Override
    public void addToAnchorOutput(
            @NonNull AnchorOutputType outputType,
            @NonNull FileCollection fileCollection) {

        FileCollection anchorCollection = outputMap.get(outputType);
        if (anchorCollection == null) {
            throw new IllegalStateException("No Anchor output created for type: " + outputType);
        }

        if (!(anchorCollection instanceof ConfigurableFileCollection)) {
            throw new IllegalStateException(
                    "Anchor File collection for type '"
                            + outputType
                            + "' is not a ConfigurableFileCollection.");
        }

        ((ConfigurableFileCollection) anchorCollection).from(fileCollection);
    }

    @NonNull
    protected ConfigurableFileCollection createCollection(@NonNull File file, @NonNull String taskName) {
        return getProject().files(file).builtBy(taskName);
    }
}
