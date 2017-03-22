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
import com.android.java.model.JavaLibrary;
import com.android.java.model.SourceSet;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of the {@link SourceSet} interface.
 */
public final class SourceSetImpl implements SourceSet, Serializable {

    @NonNull private final String myName;
    @NonNull private final Collection<File> mySourceDirectories;
    @NonNull private final Collection<File> myResourcesDirectories;
    @NonNull private final File myClassesOutputDirectory;
    @NonNull private final File myResourcesOutputDirectory;
    @NonNull private final Collection<JavaLibrary> myCompileClasspathDependencies;

    public SourceSetImpl(@NonNull String name, @NonNull Collection<File> sourceDirectories,
            @NonNull Collection<File> resourcesDirectories,
            @NonNull File classesOutputDirectory,
            @NonNull File resourcesOutputDirectory,
            @NonNull Collection<JavaLibrary> compileClasspathDependencies) {
        myName = name;
        mySourceDirectories = sourceDirectories;
        myResourcesDirectories = resourcesDirectories;
        myClassesOutputDirectory = classesOutputDirectory;
        myResourcesOutputDirectory = resourcesOutputDirectory;
        myCompileClasspathDependencies = compileClasspathDependencies;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public Collection<File> getSourceDirectories() {
        return mySourceDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getResourcesDirectories() {
        return myResourcesDirectories;
    }

    @Override
    @NonNull
    public File getClassesOutputDirectory() {
        return myClassesOutputDirectory;
    }

    @Override
    @NonNull
    public File getResourcesOutputDirectory() {
        return myResourcesOutputDirectory;
    }

    @Override
    @NonNull
    public Collection<JavaLibrary> getCompileClasspathDependencies() {
        return myCompileClasspathDependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourceSetImpl that = (SourceSetImpl) o;
        return Objects.equals(myName, that.myName)
                && Objects.equals(mySourceDirectories, that.mySourceDirectories)
                && Objects.equals(myResourcesDirectories, that.myResourcesDirectories)
                && Objects.equals(myClassesOutputDirectory, that.myClassesOutputDirectory)
                && Objects.equals(myResourcesOutputDirectory, that.myResourcesOutputDirectory)
                && Objects
                .equals(myCompileClasspathDependencies, that.myCompileClasspathDependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                myName,
                mySourceDirectories,
                myResourcesDirectories,
                myClassesOutputDirectory,
                myResourcesOutputDirectory,
                myCompileClasspathDependencies);
    }
}
