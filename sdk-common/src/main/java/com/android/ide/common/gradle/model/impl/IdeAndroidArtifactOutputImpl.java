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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class IdeAndroidArtifactOutputImpl implements IdeAndroidArtifactOutput, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final Collection<String> myFilterTypes;
    @NonNull private final Collection<FilterData> myFilters;
    @Nullable private final String myOutputType;
    private final int myVersionCode;
    @Nullable private final File myOutputFile;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeAndroidArtifactOutputImpl() {
        super();

        myFilterTypes = Collections.emptyList();
        myFilters = Collections.emptyList();
        myOutputType = null;
        myVersionCode = 0;
        myOutputFile = null;

        myHashCode = 0;
    }

    public IdeAndroidArtifactOutputImpl(
            @NotNull List<String> filterTypes,
            @NonNull Collection<FilterData> filters,
            @Nullable String outputType,
            int versionCode,
            @Nullable File outputFile) {
        myFilterTypes = filterTypes;
        myFilters = filters;
        myOutputType = outputType;
        myVersionCode = versionCode;
        // Even though getOutputFile is not new, the class hierarchies in builder-model have changed
        // a lot (e.g. new interfaces have been
        // created, and existing methods have been moved around to new interfaces) making Gradle
        // think that this is a new method.
        // When using the plugin v2.4 or older, we fall back to calling
        // getMainOutputFile().getOutputFile(), which is the older plugins
        // do.
        myOutputFile = outputFile;

        myHashCode = calculateHashCode();
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
        return myFilters;
    }

    @Override
    public int getVersionCode() {
        return myVersionCode;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        if (myOutputFile != null) {
            return myOutputFile;
        }
        throw new UnsupportedOperationException("getOutputFile");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeAndroidArtifactOutputImpl)) {
            return false;
        }
        IdeAndroidArtifactOutputImpl output = (IdeAndroidArtifactOutputImpl) o;
        return myVersionCode == output.myVersionCode
                && Objects.equals(myOutputType, output.myOutputType)
                && Objects.equals(myFilterTypes, output.myFilterTypes)
                && Objects.equals(myFilters, output.myFilters)
                && Objects.equals(myOutputFile, output.myOutputFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myOutputType, myFilterTypes, myFilters, myVersionCode, myOutputFile);
    }

    @Override
    public String toString() {
        return "IdeAndroidArtifactOutput{"
                + super.toString()
                + ", myOutputFile="
                + myOutputFile
                + "}";
    }
}
