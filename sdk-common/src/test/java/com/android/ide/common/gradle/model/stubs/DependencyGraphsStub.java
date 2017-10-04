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
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;

public final class DependencyGraphsStub extends BaseStub implements DependencyGraphs {
    @NonNull private final List<GraphItem> myCompileDependencies;
    @NonNull private final List<GraphItem> myPackageDependencies;
    @NonNull private final List<String> myProvidedLibraries;
    @NonNull private final List<String> mySkippedLibraries;

    public DependencyGraphsStub() {
        this(
                Lists.newArrayList(),
                Lists.newArrayList(new GraphItemStub()),
                Lists.newArrayList("provided"),
                Lists.newArrayList("skipped"));
    }

    public DependencyGraphsStub(
            @NonNull List<GraphItem> compileDependencies,
            @NonNull List<GraphItem> packageDependencies,
            @NonNull List<String> providedLibraries,
            @NonNull List<String> skippedLibraries) {
        myCompileDependencies = compileDependencies;
        myPackageDependencies = packageDependencies;
        myProvidedLibraries = providedLibraries;
        mySkippedLibraries = skippedLibraries;
    }

    @Override
    @NonNull
    public List<GraphItem> getCompileDependencies() {
        return myCompileDependencies;
    }

    @Override
    @NonNull
    public List<GraphItem> getPackageDependencies() {
        return myPackageDependencies;
    }

    @Override
    @NonNull
    public List<String> getProvidedLibraries() {
        return myProvidedLibraries;
    }

    @Override
    @NonNull
    public List<String> getSkippedLibraries() {
        return mySkippedLibraries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyGraphs)) {
            return false;
        }
        DependencyGraphs stub = (DependencyGraphs) o;
        return Objects.equals(getCompileDependencies(), stub.getCompileDependencies())
                && Objects.equals(getPackageDependencies(), stub.getPackageDependencies())
                && Objects.equals(getProvidedLibraries(), stub.getProvidedLibraries())
                && Objects.equals(getSkippedLibraries(), stub.getSkippedLibraries());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getCompileDependencies(),
                getPackageDependencies(),
                getProvidedLibraries(),
                getSkippedLibraries());
    }

    @Override
    public String toString() {
        return "DependencyGraphsStub{"
                + "myCompileDependencies="
                + myCompileDependencies
                + ", myPackageDependencies="
                + myPackageDependencies
                + ", myProvidedLibraries="
                + myProvidedLibraries
                + ", mySkippedLibraries="
                + mySkippedLibraries
                + "}";
    }
}
