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

package com.android.builder.dependency.level2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.MavenCoordinates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * An immutable representation of the dependencies of a variant.
 *
 * It contains both the graph of dependencies, as {@link DependencyNode} instances, and the flat
 * (and sometimes filtered) representation of the dependencies, as {@link Dependency} instances.
 *
 * If the container represents the PACKAGE scope, then skipped packages have been removed from
 * the flat representations, this is to avoid having to filter them manually all the time.
 *
 * For the COMPILE scope, provided elements are still present in the flat representations since
 * they are actually needed.
 *
 * The graph is untouched with provided/skipped elements still present.
 *
 * Each dependency can be queried from {@link #isProvided(Dependency)} and
 * {@link #isSkipped(Dependency)}. This is queried on {@link Dependency} rather than the nodes,
 * because the information is for all usage of a given dependency, no matter how many time
 * it shows up in the graph.
 */
@Immutable
public interface DependencyContainer {

    @NonNull
    ImmutableMap<Object, Dependency> getDependencyMap();

    /**
     * Returns a list of top level dependencies.
     *
     * @return a non null (but possibly empty) list.
     */
    @NonNull
    ImmutableList<DependencyNode> getDependencies();

    /**
     * Returns whether a given dependency is skipped
     * @param dependency the dependency
     */
    boolean isSkipped(@NonNull Dependency dependency);

    /**
     * Returns whether a given dependency is provided
     * @param dependency the dependency
     */
    boolean isProvided(@NonNull Dependency dependency);

    /**
     * Returns all dependencies in an ordered sets.
     */
    @NonNull
    ImmutableList<Dependency> getAllDependencies();

    /**
     * Returns all packaged dependencies in an ordered sets.
     *
     * This typically excludes the atoms.
     */
    @NonNull
    ImmutableList<Dependency> getAllPackagedDependencies();
    /**
     * Returns a list of only the Java libraries.
     *
     * This is a filtered view of {@link #getAllDependencies()}
     */
    @NonNull
    ImmutableList<JavaDependency> getAllJavaDependencies();

    /**
     * Returns a list of only the Android libraries.
     *
     * This is a filtered view of {@link #getAllDependencies()}
     */
    @NonNull
    ImmutableList<AndroidDependency> getAllAndroidDependencies();

    /**
     * Returns a list of only the Atom dependencies
     *
     * This is a filtered view of {@link #getAllDependencies()}
     */
    @NonNull
    ImmutableList<AtomDependency> getAllAtomDependencies();

    /**
     * Returns a list of only the direct Java libraries.
     *
     * This is a filtered view of {@link #getAllDependencies()}
     */
    @NonNull
    ImmutableList<JavaDependency> getDirectJavaDependencies();

    /**
     * Returns a list of only the direct local Java libraries
     *
     * This is a filtered view of {@link #getAllDependencies()}
     */
    @NonNull
    ImmutableList<JavaDependency> getDirectLocalJavaDependencies();

    /**
     * Returns a list of only the direct Android libraries.
     *
     * This is a filtered view of {@link #getAllDependencies()}
     */
    @NonNull
    ImmutableList<AndroidDependency> getDirectAndroidDependencies();

    /**
     * Returns a list of only the direct Atom dependencies
     *
     * This is a filtered view of {@link #getAllDependencies()}
     */
    @NonNull
    ImmutableList<AtomDependency> getDirectAtomDependencies();

    /**
     * Returns the base atom dependency, if present.
     *
     * @return the base atom dependency, or null if not present.
     */
    @Nullable
    AtomDependency getBaseAtom();
}
