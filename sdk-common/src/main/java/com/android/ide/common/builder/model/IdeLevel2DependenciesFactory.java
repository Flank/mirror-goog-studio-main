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

import static com.android.builder.model.level2.Library.LIBRARY_ANDROID;
import static com.android.builder.model.level2.Library.LIBRARY_JAVA;
import static com.android.builder.model.level2.Library.LIBRARY_MODULE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.tooling.model.UnsupportedMethodException;

/** Create {@link IdeLevel2Dependencies} from {@link BaseArtifact}. */
public class IdeLevel2DependenciesFactory {
    @NonNull private final Map<String, Library> myMap = new HashMap<>();

    @NonNull
    private static Collection<? extends JavaLibrary> getJavaDependencies(
            AndroidLibrary androidLibrary) {
        try {
            return androidLibrary.getJavaDependencies();
        } catch (UnsupportedMethodException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Create {@link IdeLevel2Dependencies} from {@link BaseArtifact}.
     *
     * @param artifact Instance of {@link BaseArtifact} returned from Android plugin.
     * @param modelVersion Version of Android plugin.
     * @return New instance of {@link IdeLevel2Dependencies}.
     */
    public IdeLevel2Dependencies create(
            @NonNull BaseArtifact artifact, @Nullable GradleVersion modelVersion) {
        // Create a fresh model cache for this class, since current instance is based on dependencyGraphs or dependencies, which
        // have been copied in the constructor of IdeBaseArtifact.
        ModelCache modelCache = new ModelCache();
        if (modelVersion != null
                && modelVersion.getMajor() >= 3
                && artifact.getDependencies().getLibraries().isEmpty()) {
            return createFromDependencyGraphs(artifact.getDependencyGraphs());
        }
        return createFromDependencies(artifact.getDependencies(), modelCache);
    }

    /** Call this method on 3.0+ models. */
    @VisibleForTesting
    @NonNull
    IdeLevel2DependenciesImpl createFromDependencyGraphs(@NonNull DependencyGraphs graphs) {
        return createInstance(
                graphs.getCompileDependencies()
                        .stream()
                        .map(GraphItem::getArtifactAddress)
                        .collect(Collectors.toList()));
    }

    /** Call this method on pre-3.0 models. */
    @NonNull
    private IdeLevel2DependenciesImpl createFromDependencies(
            @NonNull Dependencies dependencies, @NonNull ModelCache modelCache) {
        Set<String> visited = new HashSet<>();
        populateAndroidLibraries(dependencies.getLibraries(), visited, modelCache);
        populateJavaLibraries(dependencies.getJavaLibraries(), visited, modelCache);
        for (String projectPath : dependencies.getProjects()) {
            if (!visited.contains(projectPath)) {
                visited.add(projectPath);
                myMap.computeIfAbsent(
                        projectPath, k -> IdeLevel2LibraryFactory.create(projectPath, modelCache));
            }
        }
        return createInstance(visited);
    }

    private void populateAndroidLibraries(
            @NonNull Collection<? extends AndroidLibrary> androidLibraries,
            @NonNull Set<String> visited,
            @NonNull ModelCache modelCache) {
        for (AndroidLibrary androidLibrary : androidLibraries) {
            String address = IdeLevel2LibraryFactory.computeAddress(androidLibrary);
            if (!visited.contains(address)) {
                visited.add(address);
                myMap.computeIfAbsent(
                        address, k -> IdeLevel2LibraryFactory.create(androidLibrary, modelCache));
                populateAndroidLibraries(
                        androidLibrary.getLibraryDependencies(), visited, modelCache);
                populateJavaLibraries(getJavaDependencies(androidLibrary), visited, modelCache);
            }
        }
    }

    private void populateJavaLibraries(
            @NonNull Collection<? extends JavaLibrary> javaLibraries,
            @NonNull Set<String> visited,
            @NonNull ModelCache modelCache) {
        for (JavaLibrary javaLibrary : javaLibraries) {
            String address = IdeLevel2LibraryFactory.computeAddress(javaLibrary);
            if (!visited.contains(address)) {
                visited.add(address);
                myMap.computeIfAbsent(
                        address, k -> IdeLevel2LibraryFactory.create(javaLibrary, modelCache));
                populateJavaLibraries(javaLibrary.getDependencies(), visited, modelCache);
            }
        }
    }

    @NonNull
    private IdeLevel2DependenciesImpl createInstance(
            @NonNull Collection<String> artifactAddresses) {
        ImmutableList.Builder<Library> androidLibraries = ImmutableList.builder();
        ImmutableList.Builder<Library> javaLibraries = ImmutableList.builder();
        ImmutableList.Builder<Library> moduleDependencies = ImmutableList.builder();

        for (String address : artifactAddresses) {
            Library library = myMap.get(address);
            assert library != null;
            switch (library.getType()) {
                case LIBRARY_ANDROID:
                    androidLibraries.add(library);
                    break;
                case LIBRARY_JAVA:
                    javaLibraries.add(library);
                    break;
                case LIBRARY_MODULE:
                    moduleDependencies.add(library);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Unknown library type " + library.getType());
            }
        }
        return new IdeLevel2DependenciesImpl(
                androidLibraries.build(), javaLibraries.build(), moduleDependencies.build());
    }

    /**
     * Populate global library map from {@link GlobalLibraryMap} by making a deep copy.
     *
     * @param globalLibraryMap GlobalLibraryMap model returned from Android Plugin.
     */
    public void setupGlobalLibraryMap(@NonNull GlobalLibraryMap globalLibraryMap) {
        ModelCache modelCache = new ModelCache();
        for (Library library : globalLibraryMap.getLibraries().values()) {
            myMap.computeIfAbsent(
                    library.getArtifactAddress(),
                    k -> IdeLevel2LibraryFactory.create(library, modelCache));
        }
    }
}
