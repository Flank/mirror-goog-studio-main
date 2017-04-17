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

import static java.util.stream.Stream.concat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.BaseTask;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;

/**
 * A base task with stream fields that properly use Gradle's input/output annotations to
 * return the stream's content as input/output.
 */
public class StreamBasedTask extends BaseTask {

    protected Collection<TransformStream> consumedInputStreams;
    protected Collection<TransformStream> referencedInputStreams;
    protected IntermediateStream outputStream;

    private Iterable<FileCollection> allInputs;

    @NonNull
    @InputFiles
    public Iterable<FileCollection> getStreamInputs() {
        if (allInputs == null) {
            allInputs =
                    concat(consumedInputStreams.stream(), referencedInputStreams.stream())
                            .map(TransformStream::getFileCollection)
                            .collect(Collectors.toList());
        }

        return allInputs;
    }

    @Nullable
    @Optional
    @OutputDirectory
    public File getStreamOutputFolder() {
        if (outputStream != null) {
            return outputStream.getRootLocation();
        }

        return null;
    }
}
