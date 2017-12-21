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
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

public final class DependenciesStub extends BaseStub implements Dependencies {
    @NonNull private final Collection<AndroidLibrary> myLibraries;
    @NonNull private final Collection<JavaLibrary> myJavaLibraries;
    @NonNull private final Collection<String> myProjects;

    public DependenciesStub() {
        this(
                Lists.newArrayList(),
                Lists.newArrayList(new JavaLibraryStub()),
                Lists.newArrayList("project1", "project2"));
    }

    public DependenciesStub(
            @NonNull Collection<AndroidLibrary> libraries,
            @NonNull Collection<JavaLibrary> javaLibraries,
            @NonNull Collection<String> projects) {
        myLibraries = libraries;
        myJavaLibraries = javaLibraries;
        myProjects = projects;
    }

    @Override
    @NonNull
    public Collection<AndroidLibrary> getLibraries() {
        return myLibraries;
    }

    @Override
    @NonNull
    public Collection<JavaLibrary> getJavaLibraries() {
        return myJavaLibraries;
    }

    @Override
    @NonNull
    public Collection<String> getProjects() {
        return myProjects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Dependencies)) {
            return false;
        }
        Dependencies stub = (Dependencies) o;
        return Objects.equals(getLibraries(), stub.getLibraries())
                && Objects.equals(getJavaLibraries(), stub.getJavaLibraries())
                && Objects.equals(getProjects(), stub.getProjects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLibraries(), getJavaLibraries(), getProjects());
    }

    public void addLibrary(@NonNull AndroidLibraryStub library) {
        myLibraries.add(library);
    }

    public void addJar(@NonNull File jar) {
        myJavaLibraries.add(new JavaLibraryStub(jar, Lists.newArrayList()));
    }

    public void addProject(@NonNull String project) {
        myProjects.add(project);
    }

    @Override
    public String toString() {
        return "DependenciesStub{"
                + "myLibraries="
                + myLibraries
                + ", myJavaLibraries="
                + myJavaLibraries
                + ", myProjects="
                + myProjects
                + "}";
    }
}
