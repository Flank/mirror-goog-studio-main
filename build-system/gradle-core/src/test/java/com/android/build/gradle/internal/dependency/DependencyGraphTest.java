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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.dependency.level2.DependencyNode.NodeType;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.MavenCoordinates;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

public class DependencyGraphTest {

    private final Map<Object, Dependency> dependencyMap = Maps.newHashMap();

    @Test
    public void testFlatten() throws Exception {
        /* test a simple case:
            AAR: project:library:aar:unspecified
            Path: :library
                JAR: project:jar:jar:unspecified
                Path: :jar
                    JAR: com.google.guava:guava:jar:18.0
                    Path: null
        */

        // start from the depth of the graph.
        Pair<JavaDependency, DependencyNode> guava = mockJavaDependency(
                new File("guava.jar"),
                new MavenCoordinatesImpl("com.google.guava", "guava", "18.0"),
                null);

        Pair<JavaDependency, DependencyNode> jarModule = mockJavaDependency(
                new File("jar.jar"),
                new MavenCoordinatesImpl("project", "jar", "unspecified"),
                ":jar",
                guava.getSecond());

        Pair<AndroidDependency, DependencyNode> libraryModule = mockAndroidLibrary(
                new File("library.jar"),
                new MavenCoordinatesImpl("project", "library", "unspecified"),
                ":library",
                jarModule.getSecond());

        DependencyGraph graph = new DependencyGraph(
                dependencyMap,
                ImmutableList.of(libraryModule.getSecond()),
                MutableDependencyDataMap.EMPTY);

        FlatDependencyContainer flatContainer = graph.flatten(null, null);

        assertThat(flatContainer.getAllDependencies()).containsExactly(
                libraryModule.getFirst(), jarModule.getFirst(), guava.getFirst()).inOrder();
        assertThat(flatContainer.getDirectDependencies()).containsExactly(
                libraryModule.getFirst()).inOrder();
    }

    @Test
    public void testFlattenWithTestedLib() throws Exception {
        /* test a simple case where the test artifact of a lib has dependency, and the
           tested lib has some too.

            lib:
              com.google.random:random:18.0 (jar, project: null)
            test:
              project:library:aar:unspecified (aar, project: :library)
                project:jar:jar:unspecified (jar, project :jar)
                   com.google.guava:guava:jar:18.0 (jar, project: null)
        */

        // start from the depth of the graph.
        Pair<JavaDependency, DependencyNode> guava = mockJavaDependency(
                new File("guava.jar"),
                new MavenCoordinatesImpl("com.google.guava", "guava", "18.0"),
                null);

        Pair<JavaDependency, DependencyNode> jarModule = mockJavaDependency(
                new File("jar.jar"),
                new MavenCoordinatesImpl("project", "jar", "unspecified"),
                ":jar",
                guava.getSecond());

        Pair<AndroidDependency, DependencyNode> libraryModule = mockAndroidLibrary(
                new File("library.jar"),
                new MavenCoordinatesImpl("project", "library", "unspecified"),
                ":library",
                jarModule.getSecond());

        // also have a dependency on the lib
        Pair<JavaDependency, DependencyNode> randomLib = mockJavaDependency(
                new File("random.jar"),
                new MavenCoordinatesImpl("com.google.random", "random", "18.0"),
                null);

        DependencyGraph graph = new DependencyGraph(
                dependencyMap,
                ImmutableList.of(libraryModule.getSecond(), randomLib.getSecond()),
                MutableDependencyDataMap.EMPTY);

        // now we can reset the map for the 2nd graph
        dependencyMap.clear();
        //add random lib to it too
        dependencyMap.put(randomLib.getFirst().getCoordinates(), randomLib.getFirst());

        Pair<AndroidDependency, DependencyNode> testedModule = mockAndroidLibrary(
                new File("tested.jar"),
                new MavenCoordinatesImpl("project", "tested", "unspecified"),
                ":tested");

        // when the graph is resolved, the dependencies of the lib show up directly in the tested
        // graph, but not the lib.
        DependencyGraph testedGraph = new DependencyGraph(
                dependencyMap,
                ImmutableList.of(randomLib.getSecond()),
                MutableDependencyDataMap.EMPTY);

        FlatDependencyContainer flatContainer = graph
                .flatten(testedModule.getFirst(), testedGraph.flatten(null, null).filterSkippedLibraries());

        assertThat(flatContainer.getAllDependencies())
                .containsExactly(testedModule.getFirst(), libraryModule.getFirst(),
                        jarModule.getFirst(), guava.getFirst(), randomLib.getFirst())
                .inOrder();
    }

    @Test
    public void testFlattenWithLocalJar() throws Exception {
        // create a local jar
        Pair<JavaDependency, DependencyNode> localJar = mockLocalJavaDependency(new File("local.jar"));

        // create a simple android lib with a dependency.
        Pair<JavaDependency, DependencyNode> jarModule = mockJavaDependency(
                new File("jar.jar"),
                new MavenCoordinatesImpl("project", "jar", "unspecified"),
                ":jar");

        Pair<AndroidDependency, DependencyNode> libraryModule = mockAndroidLibrary(
                new File("library.jar"),
                new MavenCoordinatesImpl("project", "library", "unspecified"),
                ":library",
                jarModule.getSecond());

        // create  graph
        DependencyGraph graph = new DependencyGraph(
                dependencyMap,
                ImmutableList.of(localJar.getSecond(), libraryModule.getSecond()),
                MutableDependencyDataMap.EMPTY);

        FlatDependencyContainer flatContainer = graph.flatten(null, null);

        // we want to test that the libraries contains exactly testedModule, and not
        // the local jar.
        assertThat(flatContainer.getAllDependencies())
                .containsExactly(localJar.getFirst(), libraryModule.getFirst(), jarModule.getFirst())
                .inOrder();
    }

    @Test
    public void testFlattenWithTestedLocalJar() throws Exception {
        // create a local jar
        Pair<JavaDependency, DependencyNode> localJar = mockLocalJavaDependency(new File("local.jar"));

        // the tested lib
        File testedJarFile = new File("tested.jar");
        AndroidDependency testedModule = AndroidDependency
                .createExplodedAarLibrary(
                        testedJarFile,
                        new MavenCoordinatesImpl("project", "tested", "unspecified"),
                        null,
                        "",
                        new File("exploded-aar", testedJarFile.getName()));

        // create both graph both of them contains the local jar
        DependencyGraph graph = new DependencyGraph(
                dependencyMap,
                ImmutableList.of(localJar.getSecond()),
                MutableDependencyDataMap.EMPTY);

        DependencyGraph testedGraph = new DependencyGraph(
                dependencyMap,
                ImmutableList.of(localJar.getSecond()),
                MutableDependencyDataMap.EMPTY);

        final FlatDependencyContainer flatten = testedGraph.flatten(null, null);
        FlatDependencyContainer flatContainer = graph.flatten(testedModule, flatten.filterSkippedLibraries());

        // we want to test that the libraries contains exactly testedModule, and not
        // the local jar.
        assertThat(flatContainer.getAllDependencies()).containsExactly(testedModule);
    }

    @Test
    public void testWithJavaLibShowingUpTwice() throws Exception {
        /* test a simple case:
            AAR: project:library:aar:unspecified
            Path: :library
                JAR: project:jar:jar:unspecified
                Path: :jar
            AAR: project:library2:aar:unspecified
            Path: :library
                JAR: project:jar:jar:unspecified
                Path: :jar

        */

        // start from the depth of the graph.
        Pair<JavaDependency, DependencyNode> jarModule = mockJavaDependency(
                new File("jar.jar"),
                new MavenCoordinatesImpl("project", "jar", "unspecified"),
                ":jar");

        Pair<AndroidDependency, DependencyNode> libraryModule = mockAndroidLibrary(
                new File("library.jar"),
                new MavenCoordinatesImpl("project", "library", "unspecified"),
                ":library",
                jarModule.getSecond());

        Pair<AndroidDependency, DependencyNode> libraryModule2 = mockAndroidLibrary(
                new File("library2.jar"),
                new MavenCoordinatesImpl("project", "library2", "unspecified"),
                ":library2",
                jarModule.getSecond());

        DependencyGraph graph = new DependencyGraph(
                dependencyMap,
                ImmutableList.of(libraryModule.getSecond(), libraryModule2.getSecond()),
                MutableDependencyDataMap.EMPTY);

        FlatDependencyContainer flatContainer = graph.flatten(null, null);

        assertThat(flatContainer.getAllDependencies()).containsExactly(
                libraryModule.getFirst(), libraryModule2.getFirst(), jarModule.getFirst())
                .inOrder();
        assertThat(flatContainer.getDirectDependencies()).containsExactly(
                libraryModule.getFirst(), libraryModule2.getFirst()).inOrder();
    }

    @NonNull
    private Pair<JavaDependency, DependencyNode> mockJavaDependency(
            @NonNull File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @Nullable String path,
            @NonNull DependencyNode... dependencies) {
        JavaDependency javaDep = new JavaDependency(artifactFile, coordinates, "", path);
        return Pair.of(javaDep, getDependencyNode(javaDep, dependencies));
    }

    @NonNull
    private Pair<JavaDependency, DependencyNode> mockLocalJavaDependency(
            @NonNull File artifactFile) {
        JavaDependency javaDep = new JavaDependency(artifactFile);
        return Pair.of(javaDep, getDependencyNode(javaDep));
    }

    @NonNull
    private Pair<AndroidDependency, DependencyNode> mockAndroidLibrary(
            @NonNull File jarFile,
            @NonNull MavenCoordinates coordinates,
            @Nullable String gradlePath,
            @NonNull DependencyNode... dependencies) {

        AndroidDependency androidDep = AndroidDependency.createExplodedAarLibrary(
                jarFile,
                coordinates,
                jarFile.getName(), // name
                gradlePath,
                new File("exploded-aar", jarFile.getName()));

        return Pair.of(androidDep, getDependencyNode(androidDep, dependencies));
    }

    @NonNull
    private DependencyNode getDependencyNode(
            @NonNull Dependency root,
            @NonNull DependencyNode... dependencies) {
        Object address = root.getAddress();
        dependencyMap.put(address, root);

        NodeType nodeType = root instanceof AndroidDependency ? NodeType.ANDROID
                : (root instanceof JavaDependency ? NodeType.JAVA : NodeType.ATOM);

        return new DependencyNode(
                address,
                nodeType,
                Arrays.asList(dependencies),
                null);
    }
}