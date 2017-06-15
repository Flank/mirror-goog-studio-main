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
package com.android.ide.common.builder.model.stubs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.builder.model.UnusedModelMethodException;

public class LibraryStub extends BaseStub implements Library {
    @NonNull private final MavenCoordinates myResolvedCoordinates;
    @Nullable private final String myProject;
    @Nullable private final String myName;
    private final boolean myProvided;

    public LibraryStub() {
        this(new MavenCoordinatesStub(), "project", "name", true);
    }

    public LibraryStub(
            @NonNull MavenCoordinates coordinates,
            @Nullable String project,
            @Nullable String name,
            boolean provided) {
        myResolvedCoordinates = coordinates;
        myProject = project;
        myName = name;
        myProvided = provided;
    }

    @Override
    @Nullable
    public MavenCoordinates getRequestedCoordinates() {
        throw new UnusedModelMethodException("getRequestedCoordinates");
    }

    @Override
    @NonNull
    public MavenCoordinates getResolvedCoordinates() {
        return myResolvedCoordinates;
    }

    @Override
    @Nullable
    public String getProject() {
        return myProject;
    }

    @Override
    @Nullable
    public String getName() {
        return myName;
    }

    @Override
    public boolean isSkipped() {
        throw new UnusedModelMethodException("isSkipped");
    }

    @Override
    public boolean isProvided() {
        return myProvided;
    }

    @Override
    public String toString() {
        return "LibraryStub{"
                + "myResolvedCoordinates="
                + myResolvedCoordinates
                + ", myProject='"
                + myProject
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myProvided="
                + myProvided
                + "}";
    }
}
