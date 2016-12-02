/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformInput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskDependency;

/**
 * A collection of {@link TransformStream} that can be queried.
 */
public abstract class FilterableStreamCollection {

    abstract Project getProject();

    @NonNull
    abstract Collection<TransformStream> getStreams();

    @NonNull
    public ImmutableList<TransformStream> getStreams(@NonNull StreamFilter streamFilter) {
        ImmutableList.Builder<TransformStream> streamsByType = ImmutableList.builder();
        for (TransformStream s : getStreams()) {
            if (streamFilter.accept(s.getContentTypes(), s.getScopes())) {
                streamsByType.add(s);
            }
        }

        return streamsByType.build();
    }

    /**
     * Returns a single collection that contains all the files and task dependencies from the
     * streams matching the {@link StreamFilter}.
     * @param streamFilter the stream filter.
     * @return a collection.
     */
    @NonNull
    public FileCollection getPipelineOutputAsFileCollection(
            @NonNull StreamFilter streamFilter) {
        ImmutableList<TransformStream> streams = getStreams(streamFilter);
        if (streams.isEmpty()) {
            return getProject().files();
        }

        // the collection inside the stream cannot be used as is. This is because the intermediate
        // streams contain the root location rather that the actual inputs of the stream. Therefore
        // we need to go through them and create a single collection that contains the actual
        // inputs.
        // However the content of the intermediate root folder isn't known at configuration
        // time so we need to pass a callable that will compute the files dynamically.

        Callable<List<File>> callable = () -> {
            List<File> files = Lists.newArrayList();
            for (TransformStream stream : streams) {
                // get the input for the stream
                TransformInput input = stream.asNonIncrementalInput();

                // collect the files and dependency info for the collection
                for (QualifiedContent content : Iterables.concat(
                        input.getJarInputs(), input.getDirectoryInputs())) {
                    files.add(content.getFile());
                }
            }

            return files;
        };

        // gather dependencies.
        List<TaskDependency> dependencies = streams.stream()
                .map(stream -> stream.getFiles().getBuildDependencies())
                .collect(Collectors.toList());

        return getProject().files(callable).builtBy(dependencies);
    }
}
