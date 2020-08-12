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
import com.android.build.FilterData;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class IdeAndroidArtifactOutputImpl implements IdeAndroidArtifactOutput, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final Collection<FilterData> myFilters;
    private final int myVersionCode;
    @NonNull private final File myOutputFile;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeAndroidArtifactOutputImpl() {
        super();

        myFilters = Collections.emptyList();
        myVersionCode = 0;
        myOutputFile = new File("");

        myHashCode = 0;
    }

    public IdeAndroidArtifactOutputImpl(
            @NonNull Collection<FilterData> filters, int versionCode, @NonNull File outputFile) {
        myFilters = filters;
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
        return myOutputFile;
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
                && Objects.equals(myFilters, output.myFilters)
                && Objects.equals(myOutputFile, output.myOutputFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myFilters, myVersionCode, myOutputFile);
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
