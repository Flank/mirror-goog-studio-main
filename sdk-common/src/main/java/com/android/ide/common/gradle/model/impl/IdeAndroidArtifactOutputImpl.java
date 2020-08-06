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
import com.android.builder.model.AndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of an {@link AndroidArtifactOutput}. */
public final class IdeAndroidArtifactOutputImpl extends IdeVariantOutputImpl
        implements IdeAndroidArtifactOutput {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @Nullable private final File myOutputFile;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeAndroidArtifactOutputImpl() {
        super();

        myOutputFile = null;

        myHashCode = 0;
    }

    public IdeAndroidArtifactOutputImpl(
            @NotNull List<IdeOutputFileImpl> outputs,
            @NotNull List<String> filterTypes,
            @Nullable Collection<FilterData> filters,
            @Nullable IdeOutputFileImpl mainOutputFile,
            @Nullable String outputType,
            int versionCode,
            @Nullable File outputFile) {
        super(outputs, filterTypes, filters, mainOutputFile, outputType, versionCode);
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
        if (!super.equals(o)) {
            return false;
        }
        IdeAndroidArtifactOutputImpl output = (IdeAndroidArtifactOutputImpl) o;
        return output.canEquals(this)
                && Objects.equals(myOutputFile, output.myOutputFile);
    }

    @Override
    protected boolean canEquals(Object other) {
        return other instanceof IdeAndroidArtifactOutputImpl;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(super.calculateHashCode(), myOutputFile);
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
