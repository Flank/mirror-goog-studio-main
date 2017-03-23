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

package com.android.java.model.impl;

import com.android.annotations.NonNull;
import com.android.java.model.ArtifactModel;
import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of the {@link ArtifactModel} object.
 */
public final class ArtifactModelImpl implements ArtifactModel, Serializable {

    private static final long serialVersionUID = 1L;
    @NonNull private final String myName;
    @NonNull private final Map<String, Set<File>> myArtifactsByConfiguration;

    public ArtifactModelImpl(
            @NonNull String name,
            @NonNull Map<String, Set<File>> artifactsByConfiguration) {
        this.myName = name;
        this.myArtifactsByConfiguration = artifactsByConfiguration;
    }

    @NonNull
    @Override
    public String getName() {
        return myName;
    }

    @NonNull
    @Override
    public Map<String, Set<File>> getArtifactsByConfiguration() {
        return myArtifactsByConfiguration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArtifactModelImpl that = (ArtifactModelImpl) o;
        return Objects.equals(myName, that.myName)
                && Objects.equals(myArtifactsByConfiguration, that.myArtifactsByConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                myName,
                myArtifactsByConfiguration);
    }
}
