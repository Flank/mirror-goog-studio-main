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
import com.android.builder.model.MavenCoordinates;
import java.util.Objects;

/** Creates a deep copy of a {@link MavenCoordinates}. */
public final class IdeMavenCoordinates extends IdeModel implements MavenCoordinates {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final String myGroupId;
    @NonNull private final String myArtifactId;
    @NonNull private final String myVersion;
    private final int myHashCode;

    public IdeMavenCoordinates(
            @NonNull MavenCoordinates coordinates, @NonNull ModelCache modelCache) {
        super(coordinates, modelCache);
        myGroupId = coordinates.getGroupId();
        myArtifactId = coordinates.getArtifactId();
        myVersion = coordinates.getVersion();

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
        throw new UnusedModelMethodException("getPackaging");
    }

    @Override
    @Nullable
    public String getClassifier() {
        throw new UnusedModelMethodException("getClassifier");
    }

    @Override
    @Nullable
    public String getVersionlessId() {
        throw new UnusedModelMethodException("getVersionlessId");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeMavenCoordinates)) {
            return false;
        }
        IdeMavenCoordinates that = (IdeMavenCoordinates) o;
        return Objects.equals(myGroupId, that.myGroupId)
                && Objects.equals(myArtifactId, that.myArtifactId)
                && Objects.equals(myVersion, that.myVersion);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myGroupId, myArtifactId, myVersion);
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
                + '}';
    }
}
