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

package com.android.build.gradle.internal.ide;

import static com.android.builder.dependency.level2.DependencyNode.NodeType.JAVA;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dependency.ConfigurationLibraryGraph;
import com.android.build.gradle.internal.dependency.DependencyContainerImpl;
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl;
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.LibraryGraph;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.caching.CreatingCache;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class DependenciesLevel2Converter {

    private static final CreatingCache<Dependency, Library>
            sLibraryCache = new CreatingCache<>(DependenciesLevel2Converter::instantiateLibrary);
    private static final CreatingCache<DependencyNode, GraphItemImpl>
            sGraphItemCache = new CreatingCache<>(DependenciesLevel2Converter::createCachedGraphItem);
    private static final CreatingCache<Pair<File, String>, Pair<Dependency, DependencyNode>>
            sRSCache = new CreatingCache<>(DependenciesLevel2Converter::createRenderscriptSupport);

    public static void clearCaches() {
        sLibraryCache.clear();
        sGraphItemCache.clear();
        sRSCache.clear();
    }

    @NonNull
    private static final LibraryGraphImpl
            EMPTY = new LibraryGraphImpl(
                    ImmutableList.of(), ImmutableList.of(), ImmutableList.of());

    @NonNull
    public static LibraryGraphImpl getEmpty() {
        return EMPTY;
    }

    public static Map<String, Library> getGlobalLibMap() {
        List<Library> values = sLibraryCache.values();
        Map<String, Library> map = Maps.newHashMapWithExpectedSize(values.size());
        for (Library library : values) {
            map.put(library.getArtifactAddress(), library);
        }
        return map;
    }

    @NonNull
    static LibraryGraphImpl cloneGraphForJavaArtifacts(@NonNull LibraryGraph libraryGraph) {
        if (libraryGraph == EMPTY) {
            return EMPTY;
        }

        ConfigurationLibraryGraph graph = (ConfigurationLibraryGraph) libraryGraph;

        // add the dependency instances to the global map
        for (Dependency dependency : graph.getDependencyObjects()) {
            sLibraryCache.get(dependency);
        }

        return new LibraryGraphImpl(
                libraryGraph.getDependencies(),
                libraryGraph.getProvidedLibraries(),
                libraryGraph.getSkippedLibraries());
    }

    @NonNull
    public static LibraryGraph cloneGraph(
            @NonNull DependencyContainer dependencyContainer,
            @NonNull VariantConfiguration variantConfiguration,
            @NonNull AndroidBuilder androidBuilder) {
        DependencyContainerImpl container = (DependencyContainerImpl) dependencyContainer;
        List<DependencyNode> nodes = dependencyContainer.getDependencies();

        // first go through the graph and convert it.
        List<GraphItem> items = Lists.newArrayListWithCapacity(nodes.size());
        for (DependencyNode node : nodes) {
            items.add(sGraphItemCache.get(node));
        }

        final List<String> skippedList = container.getSkippedList();

        // now go through the Dependency, through the flat list and convert those
        Collection<Dependency> dependencies = dependencyContainer.getAllDependencies();
        if (!skippedList.isEmpty()) {
            // if there are skipped info, then this is the package container, and we need to
            // include the skipped packages, so we're taking the map values.
            dependencies = dependencyContainer.getDependencyMap().values();
        }
        for (Dependency dependency : dependencies) {
            sLibraryCache.get(dependency);
        }

        // handle the case where we have renderscript support enable, in which case we need
        // to add this jar
        Pair<Dependency, DependencyNode> renderscriptDependency =
                handleRenderscriptSupport(variantConfiguration, androidBuilder);
        if (renderscriptDependency != null) {
            sLibraryCache.get(renderscriptDependency.getFirst());
            items.add(sGraphItemCache.get(renderscriptDependency.getSecond()));
        }

        // return the graph
        return new LibraryGraphImpl(items, container.getProvidedList(), skippedList);
    }

    private static Library instantiateLibrary(@NonNull Dependency dependency) {
        Library library;
        if (dependency.getProjectPath() != null) {
            library = new ModuleLibraryImpl(dependency);

        } else if (dependency instanceof AndroidDependency) {
            AndroidDependency androidDependency = (AndroidDependency) dependency;
            library = new AndroidLibraryImpl(
                    androidDependency,
                    DependenciesConverter.findLocalJar(androidDependency));

        } else if (dependency instanceof JavaDependency) {
            library = new JavaLibraryImpl((JavaDependency) dependency);

        } else {
            throw new RuntimeException("unknown Dependency instance");
        }

        return library;
    }

    /**
     * Call back for the cache to create the graph item. Do not call directly.
     * @param dependencyNode the dependency node to be converted into the GraphItem
     * @return the GraphItem
     */
    @NonNull
    private static GraphItemImpl createCachedGraphItem(@NonNull DependencyNode dependencyNode) {
        // clone the dependencies
        List<DependencyNode> children = dependencyNode.getDependencies();
        List<GraphItem> clonedChildren = Lists.newArrayListWithCapacity(children.size());

        for (DependencyNode child : children) {
            clonedChildren.add(sGraphItemCache.get(child));
        }

        return new GraphItemImpl(dependencyNode.getAddress().toString(), clonedChildren);
    }

    private static Pair<Dependency, DependencyNode> createRenderscriptSupport(
            @NonNull Pair<File, String> pair) {
        Dependency dependency = new JavaDependency(
                pair.getFirst(),
                new MavenCoordinatesImpl("com.android.support", "renderscript", pair.getSecond()),
                "renderscript-" + pair.getSecond(),
                null /*projectPath*/);

        return Pair.of(
                dependency,
                new DependencyNode(dependency.getAddress(), JAVA, ImmutableList.of(), null));
    }

    private static Pair<Dependency, DependencyNode> handleRenderscriptSupport(
            @NonNull VariantConfiguration variantConfiguration,
            @NonNull AndroidBuilder androidBuilder) {
        if (variantConfiguration.getRenderscriptSupportModeEnabled()) {
            File supportJar = androidBuilder.getRenderScriptSupportJar();
            if (supportJar != null) {
                return sRSCache.get(Pair.of(
                        supportJar,
                        androidBuilder.getTargetInfo().getBuildTools().getRevision().toString()));
            }
        }

        return null;
    }

    private static final class LibraryGraphImpl implements LibraryGraph, Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull
        private final List<GraphItem> items;
        @NonNull
        private final List<String> providedLibraries;
        @NonNull
        private final List<String> skippedLibraries;

        public LibraryGraphImpl(
                @NonNull List<GraphItem> items,
                @NonNull List<String> providedLibraries,
                @NonNull List<String> skippedLibraries) {
            this.items = items;
            this.providedLibraries = ImmutableList.copyOf(providedLibraries);
            this.skippedLibraries = ImmutableList.copyOf(skippedLibraries);
        }

        @NonNull
        @Override
        public List<GraphItem> getDependencies() {
            return items;
        }

        @NonNull
        @Override
        public List<String> getProvidedLibraries() {
            return providedLibraries;
        }

        @NonNull
        @Override
        public List<String> getSkippedLibraries() {
            return skippedLibraries;
        }
    }
}
