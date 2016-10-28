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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.dependency.DependenciesMutableData;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.ide.common.caching.CreatingCache;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Serializable implementation of Dependencies for use in the model.
 */
@Immutable
final class DependenciesImpl implements Dependencies, Serializable {

    private static class DependencyItemFactory {
        private final DependenciesMutableData dependenciesMutableData;

        private DependencyItemFactory(DependenciesMutableData dependenciesMutableData) {
            this.dependenciesMutableData = dependenciesMutableData;
        }

        <T extends Library> DependencyItem<T> create(T library) {
            return new DependencyItem<T>(library, this);
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
    private static final class DependencyItem<T extends Library> {
        private final T library;
        private final DependencyItemFactory factory;

        private DependencyItem(T library, DependencyItemFactory factory) {
            this.library = library;
            this.factory = factory;
        }

        boolean isSkipped() {
            return factory.dependenciesMutableData.isSkipped(library);
        }


        /**
         * Returns a new wrapper item using the same factory.
         */
        <U extends Library> DependencyItem<U> create(U dependency) {
            return factory.create(dependency);
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DependencyItem<T> that = (DependencyItem<T>) o;
            return Objects.equals(library, that.library)
                    && Objects.equals(isSkipped(), that.isSkipped());
        }

        @Override public int hashCode() {
            return Objects.hash(library, isSkipped());
        }
    }
    private static final long serialVersionUID = 1L;

    private static final CreatingCache<AndroidAtom, AndroidAtomImpl> sAtomCache =
            new CreatingCache<>(DependenciesImpl::getAndroidAtomValue);
    private static final CreatingCache<DependencyItem<AndroidLibrary>, AndroidLibraryImpl> sLibCache
            = new CreatingCache<>(DependenciesImpl::getAndroidLibraryValue);
    private static final CreatingCache<DependencyItem<JavaLibrary>, JavaLibraryImpl> sJarCache
            = new CreatingCache<>(DependenciesImpl::getJavaLibraryValue);

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

    private static int sModelLevel = AndroidProject.MODEL_LEVEL_0_ORIGNAL;

    public static void setModelLevel(int modelLevel) {
        sModelLevel = modelLevel;
    }

    public static void clearCaches() {
        sAtomCache.clear();
        sLibCache.clear();
        sJarCache.clear();
    }

    @NonNull
    static DependenciesImpl getEmpty() {
        return new DependenciesImpl(
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                null);
    }

    @NonNull
    static DependenciesImpl cloneDependenciesForJavaArtifacts(@NonNull Dependencies dependencies) {
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

    @NonNull
    static DependenciesImpl cloneDependencies(
            @NonNull DependencyContainer dependencies,
            @NonNull VariantConfiguration variantConfiguration,
            @NonNull AndroidBuilder androidBuilder) {
        List<AndroidAtom> clonedAndroidAtoms;
        List<AndroidLibrary> clonedAndroidLibraries;
        List<JavaLibrary> clonedJavaLibraries;
        List<String> clonedProjects = Lists.newArrayList();

        List<AndroidAtom> androidAtoms = dependencies.getAtomDependencies();
        clonedAndroidAtoms = Lists.newArrayListWithCapacity(androidAtoms.size());

        List<AndroidLibrary> androidLibraries = dependencies.getAndroidDependencies();
        clonedAndroidLibraries = Lists.newArrayListWithCapacity(androidLibraries.size());

        List<JavaLibrary> javaLibraries = dependencies.getJarDependencies();
        List<JavaLibrary> localJavaLibraries = dependencies.getLocalDependencies();

        clonedJavaLibraries = Lists.newArrayListWithExpectedSize(javaLibraries.size() + localJavaLibraries.size());

        for (AndroidAtom atomImpl : androidAtoms) {
            AndroidAtom clonedAtom = sAtomCache.get(getAndroidAtomKey(atomImpl));
            if (clonedAtom != null) {
                clonedAndroidAtoms.add(clonedAtom);
            }
        }

        DependencyItemFactory depItemFactory = new DependencyItemFactory(
                dependencies.getDependenciesMutableData());

        for (AndroidLibrary libImpl : androidLibraries) {
            DependencyItem<AndroidLibrary> libImplDepItem = depItemFactory.create(libImpl);
            AndroidLibrary clonedLib = sLibCache.get(
                    libImplDepItem.create(getAndroidLibraryKey(libImplDepItem)));
            if (clonedLib != null) {
                clonedAndroidLibraries.add(clonedLib);
            }
        }

        // if we are in compatibility mode, we need to look through the android libraries,
        // for the java dependencies, and put them in the cloned java libraries list.
        if (sModelLevel < AndroidProject.MODEL_LEVEL_2_DEP_GRAPH) {
            for (AndroidLibrary androidLibrary : androidLibraries) {
                if (androidLibrary.getProject() == null) {
                    handleFlatClonedAndroidLib(depItemFactory,
                            androidLibrary, clonedJavaLibraries, clonedProjects);
                }
            }
        }

        for (JavaLibrary javaLibrary : javaLibraries) {
            if (sModelLevel < AndroidProject.MODEL_LEVEL_2_DEP_GRAPH) {
                handleFlatClonedJavaLib(
                        depItemFactory, javaLibrary, clonedJavaLibraries, clonedProjects);
            } else {
                // just convert the library using the cache. It'll recursively
                // handle the dependencies.
                DependencyItem<JavaLibrary> javaLibraryDepItem =depItemFactory.create(javaLibrary);
                JavaLibraryImpl clonedJavaLibrary = sJarCache.get(
                        javaLibraryDepItem.create(getJavaLibraryKey(javaLibraryDepItem)));
                if (clonedJavaLibrary != null) {
                    clonedJavaLibraries.add(clonedJavaLibrary);
                }
            }
        }

        for (JavaLibrary localJavaLibrary : localJavaLibraries) {
            clonedJavaLibraries.add(
                    new JavaLibraryImpl(
                            localJavaLibrary.getJarFile(),
                            null /*project*/,
                            ImmutableList.of(),
                            localJavaLibrary.getRequestedCoordinates(),
                            localJavaLibrary.getResolvedCoordinates(),
                            dependencies.getDependenciesMutableData().isSkipped(localJavaLibrary),
                            localJavaLibrary.isProvided()));
        }

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
        if (dependencies.getBaseAtom() != null)
            baseAtom = sAtomCache.get(getAndroidAtomKey(dependencies.getBaseAtom()));

        return new DependenciesImpl(
                clonedAndroidAtoms,
                clonedAndroidLibraries,
                clonedJavaLibraries,
                clonedProjects,
                baseAtom);
    }

    private static void handleFlatClonedAndroidLib(
            @NonNull DependencyItemFactory dependencyItemFactory,
            @NonNull AndroidLibrary androidLibrary,
            @NonNull List<JavaLibrary> clonedJavaLibraries,
            @NonNull List<String> clonedProjects) {
        // only handled the java dependencies.
        for (JavaLibrary javaDependency : androidLibrary.getJavaDependencies()) {
            handleFlatClonedJavaLib(dependencyItemFactory, javaDependency, clonedJavaLibraries, clonedProjects);
        }

        // then recursively go through the android children
        for (AndroidLibrary androidDependency : androidLibrary.getLibraryDependencies()) {
            handleFlatClonedAndroidLib(dependencyItemFactory, androidDependency, clonedJavaLibraries, clonedProjects);
        }
    }

    private static void handleFlatClonedJavaLib(
            @NonNull DependencyItemFactory dependencyItemFactory,
            @NonNull JavaLibrary javaLibrary,
            @NonNull List<JavaLibrary> clonedJavaLibraries,
            @NonNull List<String> clonedProjects) {
        boolean customArtifact = javaLibrary.getResolvedCoordinates().getClassifier() != null;

        if (!customArtifact && javaLibrary.getProject() != null) {
            clonedProjects.add(javaLibrary.getProject().intern());
        } else {
            DependencyItem<JavaLibrary> javaLibraryDepItem =
                    dependencyItemFactory.create(javaLibrary);
            JavaLibrary clonedJavaLib = sJarCache.get(
                    dependencyItemFactory.create(getJavaLibraryKey(javaLibraryDepItem)));
            if (clonedJavaLib != null && !clonedJavaLibraries.contains(clonedJavaLib)) {
                clonedJavaLibraries.add(clonedJavaLib);
            }
            // then recursively go through the rest.
            for (JavaLibrary javaDependency : javaLibrary.getDependencies()) {
                handleFlatClonedJavaLib(
                        dependencyItemFactory, javaDependency, clonedJavaLibraries, clonedProjects);
            }
        }
    }

    private DependenciesImpl(
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

    @NonNull
    private static AndroidAtom getAndroidAtomKey(@NonNull AndroidAtom androidAtom) {
        boolean fullCopy = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        if (fullCopy) {
            // in a full copy case, we let the cache create the serializable instance
            // and the original instance can be the key
            return androidAtom;
        }

        // else in a partial copy case, we need to first make a copy without the removed
        // stuff and use that as a key
        // It's ok because it's a fairly shallow copy anyway.
        return createSerializableAndroidAtom(androidAtom);
    }

    @NonNull
    private static AndroidAtomImpl getAndroidAtomValue(@NonNull AndroidAtom androidAtom) {
        boolean fullCopy = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        if (fullCopy) {
            // in a full copy case, the value is the serializable copy of the key.
            return createSerializableAndroidAtom(androidAtom);
        }

        // in a non full copy case, the key is already serializable so we can return it directly.
        assert androidAtom instanceof AndroidAtomImpl;
        return (AndroidAtomImpl) androidAtom;
    }

    @NonNull
    private static AndroidAtomImpl createSerializableAndroidAtom(
            @NonNull AndroidAtom atomDependency) {
        boolean newDepModel = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        List<JavaLibrary> clonedJavaLibraries = ImmutableList.of();
        List<AndroidLibrary> clonedLibDeps = ImmutableList.of();
        List<AndroidAtom> clonedAtomDeps = ImmutableList.of();

        DependencyItemFactory depItemFactory = new DependencyItemFactory(
                DependenciesMutableData.EMPTY);

        if (newDepModel) {
            List<? extends AndroidAtom> atomDeps = atomDependency.getAtomDependencies();
            clonedAtomDeps = Lists.newArrayListWithCapacity(atomDeps.size());
            for (AndroidAtom childAtom : atomDeps) {
                AndroidAtom clonedAtom = sAtomCache.get(getAndroidAtomKey(childAtom));
                if (clonedAtom != null) {
                    clonedAtomDeps.add(clonedAtom);
                }
            }

            List<? extends AndroidLibrary> libDeps = atomDependency.getLibraryDependencies();
            clonedLibDeps = Lists.newArrayListWithCapacity(libDeps.size());
            for (AndroidLibrary childLib : libDeps) {
                DependencyItem<AndroidLibrary> childLibDepItem = depItemFactory.create(childLib);
                AndroidLibrary clonedLib = sLibCache.get(depItemFactory.create(
                        getAndroidLibraryKey(childLibDepItem)));
                if (clonedLib != null) {
                    clonedLibDeps.add(clonedLib);
                }
            }

            Collection<? extends JavaLibrary> jarDeps = atomDependency.getJavaDependencies();
            clonedJavaLibraries = Lists.newArrayListWithCapacity(jarDeps.size());
            for (JavaLibrary javaLibrary : jarDeps) {
                DependencyItem<JavaLibrary> javaLibraryDepItem = depItemFactory.create(javaLibrary);
                JavaLibraryImpl clonedJar = sJarCache.get(
                        depItemFactory.create(getJavaLibraryKey(javaLibraryDepItem)));
                if (clonedJar != null) {
                    clonedJavaLibraries.add(clonedJar);
                }
            }
        }

        return new AndroidAtomImpl(
                atomDependency,
                clonedAtomDeps,
                clonedLibDeps,
                clonedJavaLibraries);
    }

    @NonNull
    private static AndroidLibrary getAndroidLibraryKey(
            @NonNull DependencyItem<AndroidLibrary> dependencyItem) {
        boolean fullCopy = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        if (fullCopy) {
            // in a full copy case, we let the cache create the serializable instance
            // and the original instance can be the key
            return dependencyItem.library;
        }

        // else in a partial copy case, we need to first make a copy without the removed
        // stuff and use that as a key
        // It's ok because it's a fairly shallow copy anyway.
        return createSerializableAndroidLibrary(dependencyItem);
    }

    @NonNull
    private static AndroidLibraryImpl getAndroidLibraryValue(
            DependencyItem<AndroidLibrary> androidLibraryData) {
        boolean fullCopy = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        if (fullCopy) {
            // in a full copy case, the value is the serializable copy of the key.
            return createSerializableAndroidLibrary(androidLibraryData);
        }

        // in a non full copy case, the key is already serializable so we can return it directly.
        assert androidLibraryData.library instanceof AndroidLibraryImpl;
        return (AndroidLibraryImpl) androidLibraryData.library;
    }

    @NonNull
    private static AndroidLibraryImpl createSerializableAndroidLibrary(
            @NonNull DependencyItem<AndroidLibrary> dependencyItem) {
        boolean newDepModel = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        List<JavaLibrary> clonedJavaLibraries = ImmutableList.of();
        List<AndroidLibrary> clonedDeps = ImmutableList.of();
        AndroidLibrary libraryDependency = dependencyItem.library;

        if (newDepModel || libraryDependency.getProject() == null) {
            List<? extends AndroidLibrary> deps = libraryDependency.getLibraryDependencies();
            clonedDeps = Lists.newArrayListWithCapacity(deps.size());
            for (AndroidLibrary child : deps) {
                DependencyItem<AndroidLibrary> childDepItem = dependencyItem.create(child);
                AndroidLibrary clonedLib = sLibCache.get(
                        childDepItem.create(getAndroidLibraryKey(childDepItem)));
                if (clonedLib != null) {
                    clonedDeps.add(clonedLib);
                }
            }

            // get the clones of the Java libraries, only in level2. In level1, this is passed
            // as direct dependencies since we don't care about order.
            if (newDepModel) {
                Collection<? extends JavaLibrary> jarDeps = libraryDependency.getJavaDependencies();
                clonedJavaLibraries = Lists.newArrayListWithCapacity(jarDeps.size());
                for (JavaLibrary javaLibrary : jarDeps) {
                    DependencyItem<JavaLibrary> javaLibDepItem = dependencyItem.create(javaLibrary);
                    JavaLibraryImpl clonedJar = sJarCache.get(
                            javaLibDepItem.create(getJavaLibraryKey(javaLibDepItem)));
                    if (clonedJar != null) {
                        clonedJavaLibraries.add(clonedJar);
                    }
                }
            }
        }

        // compute local jar even if the bundle isn't exploded.
        Collection<File> localJarOverride = findLocalJar(libraryDependency);

        return new AndroidLibraryImpl(
                libraryDependency,
                dependencyItem.isSkipped(),
                clonedDeps,
                clonedJavaLibraries,
                localJarOverride);
    }

    @NonNull
    private static JavaLibrary getJavaLibraryKey(
            @NonNull DependencyItem<JavaLibrary> dependencyItem) {
        boolean fullCopy = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        if (fullCopy) {
            // in a full copy case, we let the cache create the serializable instance
            // and the original instance can be the key
            return dependencyItem.library;
        }

        // else in a partial copy case, we need to first make a copy without the removed
        // stuff and use that as a key
        // It's ok because it's a fairly shallow copy anyway.
        return createSerializableJavaLibrary(dependencyItem);
    }

    @NonNull
    private static JavaLibraryImpl getJavaLibraryValue(
            @NonNull DependencyItem<JavaLibrary> dependencyItem) {
        boolean fullCopy = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;

        if (fullCopy) {
            // in a full copy case, the value is the serializable copy of the key.
            return createSerializableJavaLibrary(dependencyItem);
        }

        // in a non full copy case, the key is already serializable so we can return it directly.
        assert dependencyItem.library instanceof JavaLibraryImpl;
        return (JavaLibraryImpl) dependencyItem.library;
    }


    @NonNull
    private static JavaLibraryImpl createSerializableJavaLibrary(
            @NonNull DependencyItem<JavaLibrary> dependencyItem) {
        boolean fullCopy = sModelLevel >= AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;
        List<JavaLibrary> clonedDependencies = ImmutableList.of();
        JavaLibrary javaLibrary = dependencyItem.library;

        if (fullCopy) {
            List<? extends JavaLibrary> javaDependencies =
                    dependencyItem.library.getDependencies();
            clonedDependencies = Lists.newArrayListWithCapacity(javaDependencies.size());

            for (JavaLibrary javaDependency : javaDependencies) {
                DependencyItem<JavaLibrary> javaDependencyItem =
                        dependencyItem.create(javaDependency);
                JavaLibraryImpl clonedJar = sJarCache.get(
                        javaDependencyItem.create(getJavaLibraryKey(javaDependencyItem)));
                if (clonedJar != null) {
                    clonedDependencies.add(clonedJar);
                }
            }
        }

        return new JavaLibraryImpl(
                javaLibrary.getJarFile(),
                javaLibrary.getProject(),
                clonedDependencies,
                javaLibrary.getRequestedCoordinates(),
                javaLibrary.getResolvedCoordinates(),
                dependencyItem.isSkipped(),
                javaLibrary.isProvided());
    }

    /**
     * Finds the local jar for an aar.
     *
     * Since the model can be queried before the aar are exploded, we attempt to get them
     * from inside the aar.
     *
     * @param library the library.
     * @return its local jars.
     */
    @NonNull
    private static Collection<File> findLocalJar(@NonNull AndroidLibrary library) {
        // if the library is exploded, just use the normal method.
        File explodedFolder = library.getFolder();
        if (explodedFolder.isDirectory()) {
            return library.getLocalJars();
        }

        // if the aar file is present, search inside it for jar files under libs/
        File aarFile = library.getBundle();
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
