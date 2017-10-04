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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import java.io.File;
import java.util.Objects;

public class AndroidArtifactOutputStub extends VariantOutputStub implements AndroidArtifactOutput {
    @NonNull private final String myAssembleTaskName;
    @NonNull private final File myOutputFile;

    public AndroidArtifactOutputStub() {
        this("name", new File("outputFile"));
    }

    public AndroidArtifactOutputStub(@NonNull String name, @NonNull File outputFile) {
        myAssembleTaskName = name;
        myOutputFile = outputFile;
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        return myAssembleTaskName;
    }

    @Override
    @NonNull
    public File getGeneratedManifest() {
        throw new UnusedModelMethodException("getGeneratedManifest");
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return myOutputFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AndroidArtifactOutput)) {
            return false;
        }
        AndroidArtifactOutput output = (AndroidArtifactOutput) o;
        return getVersionCode() == output.getVersionCode()
                && Objects.equals(getMainOutputFile(), output.getMainOutputFile())
                && Objects.equals(getOutputs(), output.getOutputs())
                && Objects.equals(getOutputType(), output.getOutputType())
                && Objects.equals(getFilterTypes(), output.getFilterTypes())
                && Objects.equals(getFilters(), output.getFilters())
                && Objects.equals(getAssembleTaskName(), output.getAssembleTaskName())
                && Objects.equals(getOutputFile(), output.getOutputFile());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getMainOutputFile(),
                getOutputs(),
                getOutputType(),
                getFilterTypes(),
                getFilters(),
                getVersionCode(),
                getAssembleTaskName(),
                getOutputFile());
    }

    @Override
    public String toString() {
        return "AndroidArtifactOutputStub{"
                + "myAssembleTaskName='"
                + myAssembleTaskName
                + '\''
                + ", myOutputFile="
                + myOutputFile
                + "} "
                + super.toString();
    }
}
