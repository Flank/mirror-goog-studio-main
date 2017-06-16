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
import com.android.builder.model.level2.Library;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

public class IdeLevel2DependenciesImpl implements IdeLevel2Dependencies, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final Collection<Library> myAndroidLibraries;
    @NonNull private final Collection<Library> myJavaLibraries;
    @NonNull private final Collection<Library> myModuleDependencies;
    private final int myHashCode;

    IdeLevel2DependenciesImpl(
            @NonNull ImmutableList<Library> androidLibraries,
            @NonNull ImmutableList<Library> javaLibraries,
            @NonNull ImmutableList<Library> moduleDependencies) {
        myAndroidLibraries = androidLibraries;
        myJavaLibraries = javaLibraries;
        myModuleDependencies = moduleDependencies;
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public Collection<Library> getAndroidLibraries() {
        return myAndroidLibraries;
    }

    @Override
    @NonNull
    public Collection<Library> getJavaLibraries() {
        return myJavaLibraries;
    }

    @Override
    @NonNull
    public Collection<Library> getModuleDependencies() {
        return myModuleDependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeLevel2DependenciesImpl)) {
            return false;
        }
        IdeLevel2DependenciesImpl item = (IdeLevel2DependenciesImpl) o;
        return Objects.equals(myAndroidLibraries, item.myAndroidLibraries)
                && Objects.equals(myJavaLibraries, item.myJavaLibraries)
                && Objects.equals(myModuleDependencies, item.myModuleDependencies);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myAndroidLibraries, myJavaLibraries, myModuleDependencies);
    }

    @Override
    public String toString() {
        return "IdeLevel2DependenciesImpl{"
                + "myAndroidLibraries="
                + myAndroidLibraries
                + ", myJavaLibraries="
                + myJavaLibraries
                + ", myModuleDependencies="
                + myModuleDependencies
                + '}';
    }
}
