/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.model.MavenCoordinates;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A temporary flattened representation of a dependency graph.
 *
 * This is still mutable with {@link #getMutableDependencyDataMap()}.
 */
final class FlatDependencyContainer {

    @NonNull
    private final DependencyGraph dependencyGraph;
    @NonNull
    private final ImmutableList<Dependency> allDependencies;
    @NonNull
    private final ImmutableList<Dependency> directDependencies;
    @Nullable
    private final AtomDependency baseAtom;
    @NonNull
    private final MutableDependencyDataMap mutableDependencyDataMap;

    FlatDependencyContainer(
            @NonNull DependencyGraph dependencyGraph,
            @NonNull List<Dependency> allDependencies,
            @NonNull List<Dependency> directDependencies,
            @Nullable AtomDependency baseAtom,
            @NonNull MutableDependencyDataMap mutableDependencyDataMap) {
        this.dependencyGraph = dependencyGraph;
        this.allDependencies = ImmutableList.copyOf(allDependencies);
        this.directDependencies = ImmutableList.copyOf(directDependencies);
        this.baseAtom = baseAtom;
        this.mutableDependencyDataMap = mutableDependencyDataMap;
    }

    /**
     * Returns all dependencies in an ordered sets.
     */
    @NonNull
    public ImmutableList<Dependency> getAllDependencies() {
        return allDependencies;
    }

    @NonNull @VisibleForTesting
    ImmutableList<Dependency> getDirectDependencies() {
        return directDependencies;
    }

    /**
     * Returns the container for all mutable data related to dependencies in this container context.
     *
     * @return the dependencies mutable data.
     */
    @NonNull
    public MutableDependencyDataMap getMutableDependencyDataMap() {
        return mutableDependencyDataMap;
    }

    /**
     * Returns a version of this container where the skipped libraries have been removed.
     *
     * This shares the {@link MutableDependencyDataMap} instance of the original
     * {@link DependencyGraph} instance.
     *
     * @return the filtered container.
     */
    @NonNull
    DependencyContainer filterSkippedLibraries() {
        return new DependencyContainerImpl(
                dependencyGraph.getDependencyMap(),
                dependencyGraph.getDependencies(),
                mutableDependencyDataMap,
                allDependencies.stream().filter(dependency -> !mutableDependencyDataMap.isSkipped(dependency))
                        .collect(ImmutableCollectors.toImmutableList()),
                directDependencies.stream().filter(dependency -> !mutableDependencyDataMap.isSkipped(dependency))
                        .collect(ImmutableCollectors.toImmutableList()),
                baseAtom);
    }
}
