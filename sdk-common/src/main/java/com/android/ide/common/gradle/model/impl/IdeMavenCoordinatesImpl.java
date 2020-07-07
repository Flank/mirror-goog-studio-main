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
import com.android.ide.common.gradle.model.IdeMavenCoordinates;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a `MavenCoordinates`. */
public final class IdeMavenCoordinatesImpl implements IdeMavenCoordinates, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final String myGroupId;
    @NonNull private final String myArtifactId;
    @NonNull private final String myVersion;
    @NonNull private final String myPacking;
    @Nullable private final String myClassifier;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeMavenCoordinatesImpl() {
        myGroupId = "";
        myArtifactId = "";
        myVersion = "";
        myPacking = "";
        myClassifier = null;

        myHashCode = 0;
    }

    public IdeMavenCoordinatesImpl(
            @NotNull String groupId,
            @NotNull String artifactId,
            @NotNull String version,
            @NotNull String packaging,
            @Nullable String classifier) {
        myGroupId = groupId;
        myArtifactId = artifactId;
        myVersion = version;
        myPacking = packaging;
        myClassifier = classifier;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getGroupId() {
        return myGroupId;
    }

    @Override
    @NonNull
    public String getArtifactId() {
        return myArtifactId;
    }

    @Override
    @NonNull
    public String getVersion() {
        return myVersion;
    }

    @Override
    @NonNull
    public String getPackaging() {
        return myPacking;
    }

    @Override
    @Nullable
    public String getClassifier() {
        return myClassifier;
    }

    @Override
    @NonNull
    public String getVersionlessId() {
        throw new UnusedModelMethodException("getVersionlessId");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeMavenCoordinatesImpl)) {
            return false;
        }
        IdeMavenCoordinatesImpl that = (IdeMavenCoordinatesImpl) o;
        return Objects.equals(myGroupId, that.myGroupId)
                && Objects.equals(myArtifactId, that.myArtifactId)
                && Objects.equals(myVersion, that.myVersion)
                && Objects.equals(myPacking, that.myPacking)
                && Objects.equals(myClassifier, that.myClassifier);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myGroupId, myArtifactId, myVersion, myPacking, myClassifier);
    }

    @Override
    public String toString() {
        return "IdeMavenCoordinates{"
                + "myGroupId='"
                + myGroupId
                + '\''
                + ", myArtifactId='"
                + myArtifactId
                + '\''
                + ", myVersion='"
                + myVersion
                + '\''
                + ", myPacking='"
                + myPacking
                + '\''
                + ", myClassifier='"
                + myClassifier
                + '\''
                + '}';
    }
}