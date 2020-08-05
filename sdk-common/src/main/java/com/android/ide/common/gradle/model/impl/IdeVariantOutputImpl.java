/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import static com.android.ide.common.gradle.model.impl.ModelCache.copy;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a {@link VariantOutput}. */
public abstract class IdeVariantOutputImpl implements VariantOutput, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final Collection<? extends OutputFile> myOutputs;
    @NonNull private final Collection<String> myFilterTypes;
    @Nullable private final Collection<FilterData> myFilters;
    @Nullable private final OutputFile myMainOutputFile;
    @Nullable private final String myOutputType;
    private final int myVersionCode;
    private final int hashCode;

    // Used for serialization by the IDE.
    IdeVariantOutputImpl() {
        myOutputs = Collections.emptyList();
        myFilterTypes = Collections.emptyList();
        myFilters = null;
        myMainOutputFile = null;
        myOutputType = null;
        myVersionCode = 0;

        hashCode = 0;
    }

    public IdeVariantOutputImpl(
            @NotNull List<IdeOutputFileImpl> outputs,
            @NotNull List<String> filterTypes,
            @Nullable Collection<FilterData> filters,
            @Nullable IdeOutputFileImpl mainOutputFile,
            @Nullable String outputType,
            int versionCode) {
        //noinspection deprecation
        myOutputs = outputs;
        myFilterTypes = filterTypes;
        myFilters = filters;
        myMainOutputFile = mainOutputFile;
        myOutputType = outputType;
        myVersionCode = versionCode;

        hashCode = calculateHashCode();
    }

    @Nullable
    public static Collection<FilterData> copyFilters(@NonNull VariantOutput output) {
        try {
            return copy(output.getFilters(), data -> ModelCache.filterDataFrom(data));
        } catch (UnsupportedOperationException ignored) {
            return null;
        }
    }

    @Override
    @NonNull
    public OutputFile getMainOutputFile() {
        if (myMainOutputFile != null) {
            return myMainOutputFile;
        }
        throw new UnsupportedOperationException("getMainOutputFile()");
    }

    @Override
    @NonNull
    public Collection<? extends OutputFile> getOutputs() {
        return myOutputs;
    }

    @Override
    @NonNull
    public String getOutputType() {
        if (myOutputType != null) {
            return myOutputType;
        }
        throw new UnsupportedOperationException("getOutputType");
    }

    @Override
    @NonNull
    public Collection<String> getFilterTypes() {
        return myFilterTypes;
    }

    @Override
    @NonNull
    public Collection<FilterData> getFilters() {
        if (myFilters != null) {
            return myFilters;
        }
        throw new UnsupportedOperationException("getFilters");
    }

    @Override
    public int getVersionCode() {
        return myVersionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeVariantOutputImpl)) {
            return false;
        }
        IdeVariantOutputImpl output = (IdeVariantOutputImpl) o;
        return output.canEquals(this)
                && myVersionCode == output.myVersionCode
                && Objects.equals(myMainOutputFile, output.myMainOutputFile)
                && Objects.equals(myOutputs, output.myOutputs)
                && Objects.equals(myOutputType, output.myOutputType)
                && Objects.equals(myFilterTypes, output.myFilterTypes)
                && Objects.equals(myFilters, output.myFilters);
    }

    protected boolean canEquals(Object other) {
        return other instanceof IdeVariantOutputImpl;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    protected int calculateHashCode() {
        return Objects.hash(
                myMainOutputFile, myOutputs, myOutputType, myFilterTypes, myFilters, myVersionCode);
    }

    @Override
    public String toString() {
        return "myMainOutputFile="
                + myMainOutputFile
                + ", myOutputs="
                + myOutputs
                + ", myOutputType='"
                + myOutputType
                + '\''
                + ", myFilterTypes="
                + myFilterTypes
                + ", myFilters="
                + myFilters
                + ", myVersionCode="
                + myVersionCode;
    }
}
