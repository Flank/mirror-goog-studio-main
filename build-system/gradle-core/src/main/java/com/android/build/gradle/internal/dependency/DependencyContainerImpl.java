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
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.MavenCoordinates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of DependencyContainer
 */
public class DependencyContainerImpl implements DependencyContainer {

    @NonNull
    private final ImmutableMap<Object, Dependency> dependencyMap;
    @NonNull
    private final ImmutableList<DependencyNode> dependencies;
    @NonNull
    private final MutableDependencyDataMap mutableDependencyDataMap;
    @NonNull
    private final ImmutableList<Dependency> allDependencies;
    @NonNull
    private final ImmutableList<Dependency> directDependencies;
    @Nullable
    private final AtomDependency baseAtom;

    private ImmutableList<Dependency> allPackagedDependencies;
    private ImmutableList<JavaDependency> allJavaDependencies;
    private ImmutableList<AndroidDependency> allAndroidDependencies;
    private ImmutableList<AtomDependency> allAtomDependencies;
    private ImmutableList<JavaDependency> directJavaDependencies;
    private ImmutableList<JavaDependency> directLocalJavaDependencies;
    private ImmutableList<AndroidDependency> directAndroidDependencies;
    private ImmutableList<AtomDependency> directAtomDependencies;

    DependencyContainerImpl(
            @NonNull Map<Object, Dependency> dependencyMap,
            @NonNull List<DependencyNode> dependencies,
            @NonNull MutableDependencyDataMap mutableDependencyDataMap,
            @NonNull List<Dependency> allDependencies,
            @NonNull List<Dependency> directDependencies,
            @Nullable AtomDependency baseAtom) {
        this.dependencyMap = ImmutableMap.copyOf(dependencyMap);
        this.dependencies = ImmutableList.copyOf(dependencies);
        // TODO replace this with a copy of the data instead (simple map) to ensure immutability
        this.mutableDependencyDataMap = mutableDependencyDataMap;
        this.allDependencies = ImmutableList.copyOf(allDependencies);
        this.directDependencies = ImmutableList.copyOf(directDependencies);
        this.baseAtom = baseAtom;
    }

    private static final DependencyContainerImpl EMPTY = new DependencyContainerImpl(
            ImmutableMap.of(),
            ImmutableList.of(),
            MutableDependencyDataMap.EMPTY,
            ImmutableList.of(),
            ImmutableList.of(),
            null);

    public static DependencyContainer empty() {
        return EMPTY;
    }

    @NonNull
    @Override
    public ImmutableMap<Object, Dependency> getDependencyMap() {
        return dependencyMap;
    }

    @NonNull
    @Override
    public ImmutableList<DependencyNode> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isSkipped(@NonNull Dependency dependency) {
        return mutableDependencyDataMap.isSkipped(dependency);
    }

    @Override
    public boolean isProvided(@NonNull Dependency dependency) {
        return mutableDependencyDataMap.isProvided(dependency);
    }

    @NonNull
    public List<String> getProvidedList() {
        return mutableDependencyDataMap.getProvidedList();
    }

    @NonNull
    public List<String> getSkippedList() {
        return mutableDependencyDataMap.getSkippedList();
    }

    @NonNull
    @Override
    public ImmutableList<Dependency> getAllDependencies() {
        return allDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<Dependency> getAllPackagedDependencies() {
        if (allPackagedDependencies == null) {
            allPackagedDependencies = ImmutableList
                    .copyOf(allDependencies.stream()
                            .filter(it -> !(it instanceof AtomDependency))
                            .collect(Collectors.toCollection(
                                    (Supplier<Collection<Dependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }

        return allPackagedDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<JavaDependency> getAllJavaDependencies() {
        if (allJavaDependencies == null) {
            allJavaDependencies = ImmutableList.copyOf(allDependencies.stream()
                    .filter(it -> it instanceof JavaDependency)
                    .map(dependency -> (JavaDependency) dependency)
                    .collect(Collectors.toCollection(
                            (Supplier<Collection<JavaDependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }
        return allJavaDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<AndroidDependency> getAllAndroidDependencies() {
        if (allAndroidDependencies == null) {
            allAndroidDependencies = ImmutableList.copyOf(allDependencies.stream()
                    .filter(it -> it instanceof AndroidDependency)
                    .map(dependency -> (AndroidDependency) dependency)
                    .collect(Collectors.toCollection(
                            (Supplier<Collection<AndroidDependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }
        return allAndroidDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<AtomDependency> getAllAtomDependencies() {
        if (allAtomDependencies == null) {
            allAtomDependencies = ImmutableList.copyOf(allDependencies.stream()
                    .filter(it -> it instanceof AtomDependency)
                    .map(dependency -> (AtomDependency) dependency)
                    .collect(Collectors.toCollection(
                            (Supplier<Collection<AtomDependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }
        return allAtomDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<JavaDependency> getDirectJavaDependencies() {
        if (directJavaDependencies == null) {
            directJavaDependencies = ImmutableList.copyOf(directDependencies.stream()
                    .filter(it -> it instanceof JavaDependency)
                    .map(dependency -> (JavaDependency) dependency)
                    .collect(Collectors.toCollection(
                            (Supplier<Collection<JavaDependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }
        return directJavaDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<JavaDependency> getDirectLocalJavaDependencies() {
        if (directLocalJavaDependencies == null) {
            directLocalJavaDependencies = ImmutableList.copyOf(getDirectJavaDependencies().stream()
                    .filter(JavaDependency::isLocal)
                    .collect(Collectors.toCollection(
                            (Supplier<Collection<JavaDependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }
        return directLocalJavaDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<AndroidDependency> getDirectAndroidDependencies() {
        if (directAndroidDependencies == null) {
            directAndroidDependencies = ImmutableList.copyOf(directDependencies.stream()
                    .filter(it -> it instanceof AndroidDependency)
                    .map(dependency -> (AndroidDependency) dependency)
                    .collect(Collectors.toCollection(
                            (Supplier<Collection<AndroidDependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }
        return directAndroidDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<AtomDependency> getDirectAtomDependencies() {
        if (directAtomDependencies == null) {
            directAtomDependencies = ImmutableList.copyOf(directDependencies.stream()
                    .filter(it -> it instanceof AtomDependency)
                    .map(dependency -> (AtomDependency) dependency)
                    .collect(Collectors.toCollection(
                            (Supplier<Collection<AtomDependency>>) () -> new ArrayList<>(allDependencies.size()))));
        }
        return directAtomDependencies;
    }

    @Nullable @Override
    public AtomDependency getBaseAtom() {
        return baseAtom;
    }
}
