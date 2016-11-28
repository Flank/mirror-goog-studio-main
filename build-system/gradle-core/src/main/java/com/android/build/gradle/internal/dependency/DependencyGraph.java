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
import com.android.builder.dependency.level2.DependencyNode.NodeType;
import com.android.builder.dependency.level2.JavaDependency;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The representation of a dependency graph.
 *
 * It contains a graph which uses {@link DependencyNode} for its node, accessed through
 * {@link #getDependencies()}.
 *
 * Each node refers to a {@link Dependency} instance stored in the map accessed via
 * {@link #getDependencyMap()}.
 *
 * Both nodes and dependency instances are immutable and likely shared across other graphs, possibly
 * of other variants and projects.
 *
 * The Mutable state of the dependency node is stored separately via
 * {@link MutableDependencyDataMap}. This is to allow greater reusability of the instances and nodes
 * described above.
 */
public class DependencyGraph {

    @NonNull
    private final ImmutableMap<Object, Dependency> dependencyMap;
    @NonNull
    private final ImmutableList<DependencyNode> dependencies;
    @NonNull
    private final MutableDependencyDataMap mutableDependencyDataMap;

    public DependencyGraph(
            @NonNull Map<Object, Dependency> dependencyMap,
            @NonNull List<DependencyNode> dependencies,
            @NonNull MutableDependencyDataMap mutableDependencyDataMap) {
        this.dependencyMap = ImmutableMap.copyOf(dependencyMap);
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.mutableDependencyDataMap = mutableDependencyDataMap;
    }

    @NonNull
    private static DependencyGraph EMPTY = new DependencyGraph(
            ImmutableMap.of(),
            ImmutableList.of(),
            MutableDependencyDataMap.EMPTY);

    @NonNull
    public static DependencyGraph getEmpty() {
        return EMPTY;
    }

    @NonNull
    public ImmutableMap<Object, Dependency> getDependencyMap() {
        return dependencyMap;
    }

    /**
     * Returns a list of top level dependencies.
     *
     * @return a non null (but possibly empty) list.
     */
    @NonNull
    public ImmutableList<DependencyNode> getDependencies() {
        return dependencies;
    }

    @NonNull
    public MutableDependencyDataMap getMutableDependencyDataMap() {
        return mutableDependencyDataMap;
    }

    @NonNull
    public List<Dependency> flatten(@NonNull Predicate<DependencyNode> filter) {
        Set<Dependency> flatDependencies = new LinkedHashSet<>();
        computeFlatList(dependencies, flatDependencies, filter, null);

        return Lists.reverse(new ArrayList<>(flatDependencies));
    }

    /**
     * Returns a version of this container where the graph is flattened into direct dependencies.
     *
     * This also adds (if applicable) the tested library and its transitive dependencies.
     *
     * @param testedLibrary         the tested aar
     * @param testedDependencyGraph the container of the tested aar
     * @return the flattened container.
     */
    @NonNull
    public FlatDependencyContainer flatten(
            @Nullable AndroidDependency testedLibrary,
            @Nullable DependencyContainer testedDependencyGraph) {
                /*
        The handling of test for aars is a bit special due to how the dependencies are setup.
        Because we cannot have the test app depend directly on the generated aar, we have a weird
        setup:
        - The configuration for the test extends the configuration of the aar
        - The VariantConfiguration manually adds the AndroidLibrary representing the aar.

        So instead of having:
            test
            +- espresso
            +- aar
               +- guava
        We have:
            test
            +- espresso
            +- guava
            +- aar

        We also have a problem with local jars. Because of the configuration extension, they show
        up in both configuration objects so we have to remove the duplicated ones, and use the ones
        coming through the aar.
        We could more easily take the one from the configuration and drop the one inside the aar but
        it wouldn't work. The ones in the aar are slightly different (during java res merging, the
        res move from the local jars to the main classes.jar), so we really need those rather
        than the original ones.
         */

        final List<JavaDependency> testedLocalJars = testedDependencyGraph != null
                ? testedDependencyGraph.getDirectLocalJavaDependencies() : null;

        Set<Dependency> flatDependencies = new LinkedHashSet<>();

        computeFlatList(dependencies, flatDependencies, null, testedLocalJars);

        // add the tested libs after since it'll be added at the beginning of the list once it is reversed, see below.
        if (testedLibrary != null) {
            if (!flatDependencies.contains(testedLibrary)) {
                flatDependencies.add(testedLibrary);
            }
        }

        // now handle direct dependencies
        Set<Dependency> directDependencies = new LinkedHashSet<>();
        // loop in reverse order again
        for (int i = dependencies.size() - 1; i >= 0;  i--) {
            DependencyNode node = dependencies.get(i);
            Dependency dependency = dependencyMap.get(node.getAddress());
            assert dependency != null;

            addDependencyToSet(dependency, node, directDependencies, testedLocalJars);
        }

        /*
         * reverse the flatAndroidLibs and flatAndroidAtoms collections since the graph is visited
         * in reverse order.
         */
        return new FlatDependencyContainer(
                this,
                Lists.reverse(new ArrayList<>(flatDependencies)),
                Lists.reverse(new ArrayList<>(directDependencies)),
                getBaseAtom(),
                mutableDependencyDataMap);
    }

    @Nullable
    private AtomDependency getBaseAtom() {
        // search for the base atom. This is normally the single atom from which all other
        // depends.
        // first collect the direct level atom dependencies, since only atom can depend on atoms,
        // looking at other types on the direct dependencies is not needed.
        List<DependencyNode> atomList = dependencies.stream()
                .filter(node -> node.getNodeType() == NodeType.ATOM)
                .collect(Collectors.toList());
        if (atomList.isEmpty()) {
            return null;
        }

        List<AtomDependency> baseAtoms = Lists.newArrayList();
        collectAtomLeaves(atomList, baseAtoms);

        // TODO: Change this to a sync error.
        assert baseAtoms.size() == 1;
        return baseAtoms.get(0);
    }

    private void collectAtomLeaves(@NonNull Collection<DependencyNode> nodes,
            @NonNull List<AtomDependency> outLeaves) {
        for (DependencyNode node : nodes) {
            // collect the atom-only dependencies
            List<DependencyNode> atomList = node.getDependencies().stream()
                    .filter(n -> n.getNodeType() == NodeType.ATOM)
                    .collect(Collectors.toList());

            if (atomList.isEmpty()) {
                outLeaves.add((AtomDependency) dependencyMap.get(node.getAddress()));
            } else {
                collectAtomLeaves(atomList, outLeaves);
            }
        }
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a flat list of all the direct and indirect dependencies in the reverse order of what
     * we need. This will get reverse later.
     *
     * @param dependencyNodes     the dependency nodes to flatten.
     * @param outFlatDependencies where to store all the dependencies
     */
    private void computeFlatList(
            @NonNull ImmutableList<DependencyNode> dependencyNodes,
            @NonNull Set<Dependency> outFlatDependencies,
            @Nullable Predicate<DependencyNode> filter,
            @Nullable List<JavaDependency> testedLocalJars) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        // (behind both higher level libraries).
        // For instance:
        //        A
        //       / \
        //      B   C
        //       \ /
        //        D
        //
        // Must give: A B C D
        // So that both B and C override D (and B overrides C)
        for (int i = dependencyNodes.size() - 1; i >= 0;  i--) {
            DependencyNode node = dependencyNodes.get(i);

            // check the filter first
            if (filter != null && !filter.test(node)) {
                continue;
            }

            Dependency dependency = dependencyMap.get(node.getAddress());
            assert dependency != null;
            // if the dependency is already in there, then there's no need to go through it
            // and its children again.
            if (outFlatDependencies.contains(dependency)) {
                continue;
            }

            // flatten the dependencies for those libraries
            // never pass the tested local jars as this is guaranteed to be null beyond the
            // direct dependencies.
            computeFlatList(
                    node.getDependencies(),
                    outFlatDependencies,
                    filter,
                    null);

            // and add the current one (if needed) in back, the list will get reversed and it
            // will get moved to the front (higher priority)
            addDependencyToSet(dependency, node, outFlatDependencies, testedLocalJars);
        }
    }

    private static void addDependencyToSet(
            @NonNull Dependency dependency,
            @NonNull DependencyNode node,
            @NonNull Set<Dependency> outFlatDependencies,
            @Nullable List<JavaDependency> testedLocalJars) {
        //noinspection SuspiciousMethodCalls
        if (testedLocalJars == null || node.getNodeType() != NodeType.JAVA
                || !testedLocalJars.contains(dependency)) {
            outFlatDependencies.add(dependency);
        }
    }
}
