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

package com.android.ide.common.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import org.gradle.tooling.model.UnsupportedMethodException;

/** Creates a deep copy of an {@link OutputFile}. */
public final class IdeOutputFile extends IdeModel implements OutputFile {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;
    @Nullable final Integer myVersionCode;
    @NonNull private final String myOutputType;
    @NonNull private final Collection<String> myFilterTypes;
    @NonNull private final Collection<FilterData> myFilters;
    @NonNull private final File myOutputFile;
    @NonNull private final Collection<? extends OutputFile> myOutputs;
    @Nullable private final OutputFile myMainOutputFile;
    private final int myHashCode;

    public IdeOutputFile(@NonNull OutputFile file, @NonNull ModelCache modelCache) {
        super(file, modelCache);
        myOutputType = file.getOutputType();
        myFilterTypes = ImmutableList.copyOf(file.getFilterTypes());
        myFilters =
                copy(file.getFilters(), modelCache, data -> new IdeFilterData(data, modelCache));
        myOutputFile = file.getOutputFile();
        myMainOutputFile =
                copyNewProperty(
                        modelCache,
                        file::getMainOutputFile,
                        outputFile -> new IdeOutputFile(outputFile, modelCache),
                        null);
        //noinspection deprecation
        myOutputs = copyOutputs(file, modelCache);
        myVersionCode = copyNewProperty(file::getVersionCode, null);

        myHashCode = calculateHashCode();
    }

    @NonNull
    private static Collection<? extends OutputFile> copyOutputs(
            @NonNull OutputFile file, @NonNull ModelCache modelCache) {
        try {
            //noinspection deprecation
            Collection<? extends OutputFile> key = file.getOutputs();
            return copy(key, modelCache, outputFile -> new IdeOutputFile(outputFile, modelCache));
        } catch (UnsupportedMethodException ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    @NonNull
    public String getOutputType() {
        return myOutputType;
    }

    @Override
    @NonNull
    public Collection<String> getFilterTypes() {
        return myFilterTypes;
    }

    @Override
    @NonNull
    public Collection<FilterData> getFilters() {
        return myFilters;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return myOutputFile;
    }

    @Override
    @NonNull
    public OutputFile getMainOutputFile() {
        if (myMainOutputFile != null) {
            return myMainOutputFile;
        }
        throw new UnsupportedMethodException("getMainOutputFile()");
    }

    @Override
    @NonNull
    public Collection<? extends OutputFile> getOutputs() {
        return myOutputs;
    }

    @Override
    public int getVersionCode() {
        if (myVersionCode != null) {
            return myVersionCode;
        }
        throw new UnsupportedMethodException("getVersionCode");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeOutputFile)) {
            return false;
        }

        IdeOutputFile file = (IdeOutputFile) o;
        return Objects.equals(myVersionCode, file.myVersionCode)
                && Objects.equals(myOutputType, file.myOutputType)
                && Objects.equals(myFilterTypes, file.myFilterTypes)
                && Objects.equals(myFilters, file.myFilters)
                && Objects.equals(myOutputFile, file.myOutputFile)
                && Objects.equals(myOutputs, file.myOutputs)
                && mainOutputFileEquals(file);
    }

    private boolean mainOutputFileEquals(@NonNull IdeOutputFile file) {
        // Avoid stack overflow.
        return myMainOutputFile == this
                ? file.myMainOutputFile == file
                : Objects.equals(myMainOutputFile, file.myMainOutputFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        int result = myOutputType.hashCode();
        result = 31 * result + myFilterTypes.hashCode();
        result = 31 * result + myFilters.hashCode();
        result = 31 * result + myOutputFile.hashCode();
        result = 31 * result + hashCode(myMainOutputFile);
        result = 31 * result + hashCode(myOutputs);
        result = 31 * result + Objects.hashCode(myVersionCode);
        return result;
    }

    private int hashCode(@NonNull Collection<? extends OutputFile> outputFiles) {
        int hashCode = 1;
        for (OutputFile outputFile : outputFiles) {
            hashCode = 31 * hashCode + hashCode(outputFile);
        }
        return hashCode;
    }

    private int hashCode(@Nullable OutputFile outputFile) {
        return outputFile != this ? Objects.hashCode(outputFile) : 1;
    }

    @Override
    public String toString() {
        return "IdeOutputFile{"
                + "myOutputType='"
                + myOutputType
                + '\''
                + ", myFilterTypes="
                + myFilterTypes
                + ", myFilters="
                + myFilters
                + ", myOutputFile="
                + myOutputFile
                + ", myMainOutputFile="
                + toString(myMainOutputFile)
                + // Avoid stack overflow.
                ", myOutputs="
                + toString(myOutputs)
                + ", myVersionCode="
                + myVersionCode
                + "}";
    }

    @NonNull
    private String toString(@NonNull Collection<? extends OutputFile> outputFiles) {
        int max = outputFiles.size() - 1;
        if (max == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        b.append('[');
        int i = 0;
        for (OutputFile file : outputFiles) {
            b.append(toString(file));
            if (i++ == max) {
                b.append(']');
                break;
            }
            b.append(", ");
        }
        return b.toString();
    }

    @NonNull
    private String toString(@Nullable OutputFile outputFile) {
        if (outputFile == this) {
            return "this";
        }
        return Objects.toString(outputFile);
    }
}
