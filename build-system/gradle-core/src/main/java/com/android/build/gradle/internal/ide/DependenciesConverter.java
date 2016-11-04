/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_JARS;
import static java.util.stream.Collectors.toList;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.dependency.level2.DependencyNode.NodeType;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.ide.common.caching.CreatingCache;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Serializable implementation of Dependencies for use in the model.
 */
@Immutable
final class DependenciesConverter  {

    private static class DependencyItemFactory {
        @NonNull
        private final DependencyContainer dependencyContainer;

        private DependencyItemFactory(
                @NonNull DependencyContainer dependencyContainer) {
            this.dependencyContainer = dependencyContainer;
        }

        <T extends Dependency> DependencyItem<T> create(T library) {
            return new DependencyItem<T>(library, this);
        }

        <T extends Dependency> DependencyItem<T> create(T library, Object other) {
            return new DependencyItem<T>(library, other, this);
        }
    }

    /**
     * An item that wraps a [Java|Android]Library and its mutable states.
     *
     * This includes all the data that really goes into the IDE model implementations of
     * [Java|Android]Library.
     *
     * This is used as the cache key of the CreatingCache that allow de-duplication of similar
     * instances.
     *
     * @param <T>
     */
    private static final class DependencyItem<T extends Dependency> {
        private final T dependency;
        private final DependencyItemFactory factory;
        // an optional object added to the item
        private final Object other;

        private DependencyItem(T dependency, DependencyItemFactory factory) {
            this(dependency, null, factory);
        }

        private DependencyItem(T dependency, Object other, DependencyItemFactory factory) {
            this.dependency = dependency;
            this.other = other;
            this.factory = factory;
        }

        boolean isSkipped() {
            return factory.dependencyContainer.isSkipped(dependency);
        }
        boolean isProvided() {
            return factory.dependencyContainer.isProvided(dependency);
        }

        <N> N getOther() {
            //noinspection unchecked
            return (N) other;
        }

        /**
         * Returns a new wrapper item using the same factory.
         */
        <U extends Dependency> DependencyItem<U> create(U dependency) {
            return factory.create(dependency);
        }

        <U extends Dependency> DependencyItem<U> create(U dependency, Object other) {
            return factory.create(dependency, other);
        }

        Map<Object, Dependency> getMap() {
            return factory.dependencyContainer.getDependencyMap();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DependencyItem<T> that = (DependencyItem<T>) o;
            return Objects.equals(dependency, that.dependency)
                    && Objects.equals(isSkipped(), that.isSkipped())
                    && Objects.equals(isProvided(), that.isProvided());
        }

        @Override
        public int hashCode() {
            return Objects.hash(dependency, isSkipped(), isProvided());
        }
    }

    private static final CreatingCache<DependencyItem<AtomDependency>, AndroidAtomImpl> sAtomCache =
            new CreatingCache<>(DependenciesConverter::createSerializableAndroidAtom);
    private static final CreatingCache<DependencyItem<AndroidDependency>, AndroidLibraryImpl> sLibCache
            = new CreatingCache<>(DependenciesConverter::createSerializableAndroidLibrary);
    private static final CreatingCache<DependencyItem<JavaDependency>, JavaLibraryImpl> sJarCache
            = new CreatingCache<>(DependenciesConverter::createSerializableJavaLibrary);


    public static void clearCaches() {
        sAtomCache.clear();
        sLibCache.clear();
        sJarCache.clear();
    }

    @NonNull
    private static final DependenciesImpl EMPTY = new DependenciesImpl(
            ImmutableList.of(),
            ImmutableList.of(),
            ImmutableList.of(),
            ImmutableList.of(),
            null);

    @NonNull
    static DependenciesImpl getEmpty() {
        return EMPTY;
    }

    @NonNull
    static DependenciesImpl cloneDependenciesForJavaArtifacts(@NonNull Dependencies dependencies) {
        if (dependencies == EMPTY) {
            return EMPTY;
        }

        List<AndroidAtom> atoms = Collections.emptyList();
        List<AndroidLibrary> libraries = Collections.emptyList();
        List<JavaLibrary> javaLibraries = Lists.newArrayList(dependencies.getJavaLibraries());
        List<String> projects = Collections.emptyList();

        return new DependenciesImpl(
                atoms,
                libraries,
                javaLibraries,
                projects,
                dependencies.getBaseAtom());
    }

    private DependencyContainer dependencyContainer;
    private DependencyItemFactory factory;

    @NonNull
    DependenciesImpl cloneDependencies(
            @NonNull DependencyContainer dependencyContainer,
            @NonNull VariantConfiguration variantConfiguration,
            @NonNull AndroidBuilder androidBuilder) {
        this.dependencyContainer = dependencyContainer;
        factory = new DependencyItemFactory(dependencyContainer);

        // in level 0 or 1, both JavaLibrary and AndroidAtom are passed flat
        // with no transitive dependencies.
        // Only AndroidLibrary contain their own children.
        // And Java-only sub-project are just passed as a list of String (gradlePath)

        // collect the java sub-project. We can just get the list of direct JavaDependencies,
        // and filter by projectPath
        List<JavaDependency> directJavaDeps = dependencyContainer.getDirectJavaDependencies();
        List<String> clonedProjects = directJavaDeps.stream()
                .filter(javaDependency -> javaDependency.getProjectPath() != null)
                .map(JavaDependency::getProjectPath)
                .collect(toList());

        // for the JavaLibraries that are not projects, we return a flat list, but we need
        // to gather it from the graph, to find all the transitive dependencies.
        List<DependencyNode> dependencyNodes = dependencyContainer.getDependencies();
        Set<JavaLibrary> clonedJavaLibraries = Sets.newLinkedHashSet();
        // clone recursively, but skip the sub-projects
        cloneJavaLibraries(dependencyNodes, clonedJavaLibraries);

        // collect the flat atom lists
        List<AtomDependency> atomDependencies = dependencyContainer.getAllAtomDependencies();
        List<AndroidAtom> clonedAndroidAtoms = clonedAndroidAtoms(atomDependencies);

        // collect the android dependencies. We have to use the graph for this.
        List<AndroidLibrary> clonedAndroidLibraries = cloneAndroidLibraries(dependencyNodes);

        if (variantConfiguration.getRenderscriptSupportModeEnabled()) {
            File supportJar = androidBuilder.getRenderScriptSupportJar();
            if (supportJar != null) {
                clonedJavaLibraries.add(new JavaLibraryImpl(
                        supportJar,
                        null /*project*/,
                        ImmutableList.of(),
                        null,
                        new MavenCoordinatesImpl(
                                "com.android.support",
                                "renderscript",
                                androidBuilder.getTargetInfo().getBuildTools().getRevision().toString()),
                        false, /*isSkipped*/
                        false /*isProvided*/));
            }
        }

        // Finally, find the base atom, if present.
        AndroidAtom baseAtom = null;
        if (dependencyContainer.getBaseAtom() != null) {
            baseAtom = sAtomCache.get(factory.create(dependencyContainer.getBaseAtom()));
        }

        return new DependenciesImpl(
                clonedAndroidAtoms,
                clonedAndroidLibraries,
                new ArrayList<>(clonedJavaLibraries),
                clonedProjects,
                baseAtom);
    }

    private void cloneJavaLibraries(
            @NonNull List<DependencyNode> nodes,
            @NonNull Set<JavaLibrary> outLibraries) {

        for (DependencyNode node : nodes) {
            Dependency dependency = dependencyContainer.getDependencyMap().get(node.getAddress());
            if (dependency.getProjectPath() != null) {
                continue;
            }

            switch (node.getNodeType()) {
                case JAVA:
                    JavaDependency javaDep = (JavaDependency) dependency;
                    outLibraries.add(sJarCache.get(factory.create(javaDep)));
                    // intended fall-through
                case ANDROID:
                    // dont convert but go recursively for potential java dependencies.
                    cloneJavaLibraries(node.getDependencies(), outLibraries);
                    break;
                case ATOM:
                    // do nothing
                    break;
            }
        }
    }

    private List<AndroidAtom> clonedAndroidAtoms(
            @NonNull List<AtomDependency> atomDependencies) {
        return atomDependencies.stream()
                .map((Function<AtomDependency, AndroidAtom>) dep -> sAtomCache.get(factory.create(dep)))
                .collect(toList());
    }

    @NonNull
    private List<AndroidLibrary> cloneAndroidLibraries(
            @NonNull List<DependencyNode> nodes) {

        List<AndroidLibrary> results = Lists.newArrayListWithCapacity(nodes.size());

        for (DependencyNode node : nodes) {
            if (node.getNodeType() != NodeType.ANDROID) {
                continue;
            }

            AndroidDependency androidDependency = (AndroidDependency)
                    dependencyContainer.getDependencyMap().get(node.getAddress());

            AndroidLibrary lib = sLibCache.get(factory.create(androidDependency, node));
            if (lib != null) {
                results.add(lib);
            }
        }

        // resize to right size
        return Lists.newArrayList(results);
    }

    @NonNull
    private static AndroidAtomImpl createSerializableAndroidAtom(
            @NonNull DependencyItem<AtomDependency> dependencyItem) {

        return new AndroidAtomImpl(dependencyItem.dependency);
    }

    @NonNull
    private static AndroidLibraryImpl createSerializableAndroidLibrary(
            @NonNull DependencyItem<AndroidDependency> dependencyItem) {

        AndroidDependency androidDependency = dependencyItem.dependency;
        DependencyNode dependencyNode = dependencyItem.getOther();

        List<DependencyNode> deps = dependencyNode.getDependencies();
        List<AndroidLibrary> clonedDeps = Lists.newArrayListWithCapacity(deps.size());
        for (DependencyNode childNode : deps) {
            if (childNode.getNodeType() != NodeType.ANDROID) {
                continue;
            }

            AndroidDependency childDependency = (AndroidDependency)
                    dependencyItem.getMap().get(childNode.getAddress());

            DependencyItem<AndroidDependency> childDepItem = dependencyItem
                    .create(childDependency, childNode);

            AndroidLibrary clonedLib = sLibCache.get(childDepItem);
            if (clonedLib != null) {
                clonedDeps.add(clonedLib);
            }
        }

        // compute local jar even if the bundle isn't exploded.
        Collection<File> localJarOverride = findLocalJar(androidDependency);

        return new AndroidLibraryImpl(
                androidDependency,
                dependencyItem.isProvided(),
                dependencyItem.isSkipped(),
                clonedDeps,
                ImmutableList.of(),
                localJarOverride);
    }

    @NonNull
    private static JavaLibraryImpl createSerializableJavaLibrary(
            @NonNull DependencyItem<JavaDependency> dependencyItem) {
        JavaDependency javaDependency = dependencyItem.dependency;
        return new JavaLibraryImpl(
                javaDependency.getArtifactFile(),
                javaDependency.getProjectPath(),
                ImmutableList.of(),
                null,
                javaDependency.getCoordinates(),
                dependencyItem.isSkipped(),
                dependencyItem.isProvided());
    }

    /**
     * Finds the local jar for an aar.
     *
     * Since the model can be queried before the aar are exploded, we attempt to get them
     * from inside the aar.
     *
     * @param dependency the library.
     * @return its local jars.
     */
    @NonNull
    static Collection<File> findLocalJar(@NonNull AndroidDependency dependency) {
        // if the library is exploded, just use the normal method.
        File explodedFolder = dependency.getExtractedFolder();
        if (explodedFolder.isDirectory()) {
            return dependency.getLocalJars();
        }

        // if the aar file is present, search inside it for jar files under libs/
        File aarFile = dependency.getArtifactFile();
        if (aarFile.isFile()) {
            List<File> jarList = Lists.newArrayList();

            File jarsFolder = new File(explodedFolder, FD_JARS);

            ZipFile zipFile = null;
            try {
                //noinspection IOResourceOpenedButNotSafelyClosed
                zipFile = new ZipFile(aarFile);

                for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
                    ZipEntry zipEntry = e.nextElement();
                    String name = zipEntry.getName();
                    if (name.startsWith("libs/") && name.endsWith(DOT_JAR)) {
                        jarList.add(new File(jarsFolder, name.replace('/', File.separatorChar)));
                    }
                }

                return jarList;
            } catch (FileNotFoundException ignored) {
                // should not happen since we check ahead of time
            } catch (IOException e) {
                // we'll return an empty list below
            } finally {
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    static final class DependenciesImpl implements Dependencies, Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull
        private final List<AndroidAtom> atoms;
        @NonNull
        private final List<AndroidLibrary> libraries;
        @NonNull
        private final List<JavaLibrary> javaLibraries;
        @NonNull
        private final List<String> projects;
        @Nullable
        private final AndroidAtom baseAtom;

        DependenciesImpl(
                @NonNull List<AndroidAtom> atoms,
                @NonNull List<AndroidLibrary> libraries,
                @NonNull List<JavaLibrary> javaLibraries,
                @NonNull List<String> projects,
                @Nullable AndroidAtom baseAtom) {
            this.atoms = atoms;
            this.libraries = libraries;
            this.javaLibraries = javaLibraries;
            this.projects = projects;
            this.baseAtom = baseAtom;
        }

        @NonNull
        @Override
        public Collection<AndroidAtom> getAtoms() {
            return atoms;
        }

        @NonNull
        @Override
        public Collection<AndroidLibrary> getLibraries() {
            return libraries;
        }

        @NonNull
        @Override
        public Collection<JavaLibrary> getJavaLibraries() {
            return javaLibraries;
        }

        @NonNull
        @Override
        public List<String> getProjects() {
            return projects;
        }

        @Nullable
        @Override
        public AndroidAtom getBaseAtom() {
            return baseAtom;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("libraries", libraries)
                    .add("javaLibraries", javaLibraries)
                    .add("projects", projects)
                    .add("atoms", atoms)
                    .add("baseAtom", baseAtom)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DependenciesImpl that = (DependenciesImpl) o;
            return Objects.equals(atoms, that.atoms) &&
                    Objects.equals(libraries, that.libraries) &&
                    Objects.equals(javaLibraries, that.javaLibraries) &&
                    Objects.equals(projects, that.projects) &&
                    Objects.equals(baseAtom, that.baseAtom);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    atoms, libraries, javaLibraries, projects, baseAtom);
        }

    }
}
