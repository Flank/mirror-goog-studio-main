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
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs;
import com.android.build.gradle.internal.dependency.DependencyContainerImpl;
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl;
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs;
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl;
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl;
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.caching.CreatingCache;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
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
    private static final DependencyGraphs EMPTY = new EmptyDependencyGraphs();

    @NonNull
    public static DependencyGraphs getEmpty() {
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
    public static DependencyGraphs cloneGraphForJavaArtifacts(@NonNull DependencyGraphs dependencyGraphs) {
        if (dependencyGraphs == EMPTY) {
            return EMPTY;
        }

        ConfigurationDependencyGraphs graph = (ConfigurationDependencyGraphs) dependencyGraphs;

        // add the dependency instances to the global map
        for (Dependency dependency : graph.getDependencyObjects()) {
            sLibraryCache.get(dependency);
        }

        return new SimpleDependencyGraphsImpl(dependencyGraphs.getCompileDependencies());
    }

    @NonNull
    public static DependencyGraphs cloneGraph(
            @NonNull DependencyContainer compileContainer,
            @NonNull DependencyContainer packageContainer,
            @NonNull VariantConfiguration variantConfiguration,
            @NonNull AndroidBuilder androidBuilder) {
        // first go through the compile graph and convert it.
        List<DependencyNode> nodes = compileContainer.getDependencies();
        List<GraphItem> compileItems = Lists.newArrayListWithCapacity(nodes.size());
        for (DependencyNode node : nodes) {
            compileItems.add(sGraphItemCache.get(node));
        }

        // then go through the compile Dependencies instances and convert them
        for (Dependency dependency : compileContainer.getAllDependencies()) {
            sLibraryCache.get(dependency);
        }

        // handle the case where we have renderscript support enable, in which case we need
        // to add this jar
        Pair<Dependency, DependencyNode> renderscriptDependency =
                handleRenderscriptSupport(variantConfiguration, androidBuilder);
        if (renderscriptDependency != null) {
            sLibraryCache.get(renderscriptDependency.getFirst());
            compileItems.add(sGraphItemCache.get(renderscriptDependency.getSecond()));
        }

        // if the package is the empty container, then return a simple one.
        if (packageContainer == DependencyContainerImpl.empty()) {

            return new SimpleDependencyGraphsImpl(compileItems);
        }

        // else do the same on the package container
        nodes = packageContainer.getDependencies();
        List<GraphItem> packageItems = Lists.newArrayListWithCapacity(nodes.size());
        for (DependencyNode node : nodes) {
            packageItems.add(sGraphItemCache.get(node));
        }

        // then go through the package Dependencies instances and convert them
        // if we have skipped items, we have to get all the map values to include the
        // skipped items.
        List<String> skippedList = (((DependencyContainerImpl) packageContainer)).getSkippedList();
        Collection<Dependency> packageDependencies = skippedList.isEmpty()
                ? packageContainer.getAllDependencies()
                : packageContainer.getDependencyMap().values();

        // We have to use the map rather than the flat list in order
        for (Dependency dependency : packageDependencies) {
            sLibraryCache.get(dependency);
        }

        if (renderscriptDependency != null) {
            packageItems.add(sGraphItemCache.get(renderscriptDependency.getSecond()));
        }

        return new FullDependencyGraphsImpl(
                compileItems,
                packageItems,
                (((DependencyContainerImpl) compileContainer)).getProvidedList(),
                skippedList);
    }

    private static Library instantiateLibrary(@NonNull Dependency dependency) {
        Library library;
        if (dependency.getProjectPath() != null) {
            library = new ModuleLibraryImpl(dependency);

        } else if (dependency instanceof AndroidDependency) {
            AndroidDependency androidDependency = (AndroidDependency) dependency;
            library =
                    new AndroidLibraryImpl(
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
}
