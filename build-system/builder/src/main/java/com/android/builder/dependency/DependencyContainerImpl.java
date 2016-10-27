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

package com.android.builder.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * implementation of DependencyContainer
 */
@Immutable
public class DependencyContainerImpl implements DependencyContainer {

    @NonNull
    private final ImmutableList<AndroidLibrary> mLibraryDependencies;

    @NonNull
    private final ImmutableList<AndroidAtom> mAtomDependencies;

    @NonNull
    private final ImmutableList<JavaLibrary> mJavaDependencies;

    @NonNull
    private final ImmutableList<JavaLibrary> mLocalJars;

    @Nullable
    private final AndroidAtom mBaseAtom;

    @NonNull
    private final DependenciesMutableData dependenciesMutableData;

    public DependencyContainerImpl(
            @NonNull DependenciesMutableData mutableDependencyContainer,
            @NonNull List<? extends AndroidLibrary> aars,
            @NonNull List<? extends AndroidAtom> atoms,
            @NonNull Collection<? extends JavaLibrary> jars,
            @NonNull Collection<? extends JavaLibrary> localJars) {
        dependenciesMutableData = mutableDependencyContainer;
        mLibraryDependencies = ImmutableList.copyOf(aars);
        mAtomDependencies = ImmutableList.copyOf(atoms);
        mJavaDependencies = ImmutableList.copyOf(jars);
        mLocalJars = ImmutableList.copyOf(localJars);

        if (!mAtomDependencies.isEmpty()) {
            List<? extends AndroidAtom> depAtoms = mAtomDependencies;
            List<? extends AndroidAtom> subDepAtoms = depAtoms.get(0).getAtomDependencies();
            while (!subDepAtoms.isEmpty()) {
                depAtoms = subDepAtoms;
                subDepAtoms = depAtoms.get(0).getAtomDependencies();
            }

            // TODO: Change this to a sync error.
            assert (depAtoms.size() == 1);
            mBaseAtom = depAtoms.get(0);
        } else {
            mBaseAtom = null;
        }
    }

    public static DependencyContainer getEmpty() {
        return new DependencyContainerImpl(
                DependenciesMutableData.EMPTY,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of());
    }

    @NonNull
    @Override
    public ImmutableList<AndroidLibrary> getAndroidDependencies() {
        return mLibraryDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<AndroidAtom> getAtomDependencies() {
        return mAtomDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<JavaLibrary> getJarDependencies() {
        return mJavaDependencies;
    }

    @NonNull
    @Override
    public ImmutableList<JavaLibrary> getLocalDependencies() {
        return mLocalJars;
    }

    @Nullable
    @Override
    public AndroidAtom getBaseAtom() {
        return mBaseAtom;
    }

    @NonNull
    @Override
    public DependencyContainer flatten(
            @Nullable AndroidLibrary testedLibrary,
            @Nullable DependencyContainer testedDependencyContainer) {
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

        Set<AndroidLibrary> flatAndroidLibs = new LinkedHashSet<>();
        Set<AndroidAtom> flatAndroidAtoms = new LinkedHashSet<>();
        Set<JavaLibrary> flatJavaLibs = new LinkedHashSet<>();

        computeFlatAtomList(dependenciesMutableData, mAtomDependencies, flatAndroidAtoms, flatAndroidLibs, flatJavaLibs);
        computeFlatLibraryList(dependenciesMutableData, mLibraryDependencies, flatAndroidLibs, flatJavaLibs);

        // add the tested libs after since it'll be added at the beginning of the list once it is reversed, see below.
        if (testedLibrary != null) {
            computeFlatLibraryList(dependenciesMutableData, testedLibrary, flatAndroidLibs, flatJavaLibs);
        }

        computeFlatJarList(dependenciesMutableData, mJavaDependencies, flatJavaLibs);

        // handle the local jars. Remove the duplicated ones from mLocalJars.
        // They will actually show up through the testedLibrary's local jars.
        List<JavaLibrary> localJars = mLocalJars;
        if (testedDependencyContainer != null && testedLibrary != null) {
            Collection<JavaLibrary> testedLocalJars = testedDependencyContainer.getLocalDependencies();

            localJars = Lists.newArrayListWithExpectedSize(mLocalJars.size());
            for (JavaLibrary javaLibrary : mLocalJars) {
                if (!testedLocalJars.contains(javaLibrary)) {
                    localJars.add(javaLibrary);
                }
            }
        }

        /**
         * reverse the flatAndroidLibs and flatAndroidAtoms collections since the graph is visited
         * in reverse order.
         */
        return new DependencyContainerImpl(
                dependenciesMutableData,
                Lists.reverse(new ArrayList<>(flatAndroidLibs)),
                Lists.reverse(new ArrayList<>(flatAndroidAtoms)),
                flatJavaLibs,
                localJars);

    }

    @Override
    public DependenciesMutableData getDependenciesMutableData() {
        return dependenciesMutableData;
    }

    /**
     * Resolves a given list of atoms, finds out if they depend on atoms or
     * other libraries, and returns a flat list of all the direct and indirect
     * dependencies in the proper order (first is higher priority when calling
     * aapt).
     *
     * @param androidAtoms the atoms to resolve.
     * @param outFlatAndroidAtoms where to store all the android atoms.
     * @param outFlatAndroidLibs where to store all the android libraries.
     * @param outFlatJavaLibs where to store all the java libraries.
     */
    private static void computeFlatAtomList(
            @NonNull DependenciesMutableData dependenciesMutableData,
            @NonNull List<? extends AndroidAtom> androidAtoms,
            @NonNull Set<AndroidAtom> outFlatAndroidAtoms,
            @NonNull Set<AndroidLibrary> outFlatAndroidLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {
        for (int i = androidAtoms.size() - 1  ; i >= 0 ; i--) {
            computeFlatAtomList(
                    dependenciesMutableData,
                    androidAtoms.get(i),
                    outFlatAndroidAtoms,
                    outFlatAndroidLibs,
                    outFlatJavaLibs);
        }
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a flat list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     *
     * @param androidLibs the libraries to resolve.
     * @param outFlatAndroidLibs where to store all the android libraries.
     * @param outFlatJavaLibs where to store all the java libraries
     */
    private static void computeFlatLibraryList(
            @NonNull DependenciesMutableData dependenciesMutableData,
            @NonNull List<? extends AndroidLibrary> androidLibs,
            @NonNull Set<AndroidLibrary> outFlatAndroidLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {
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
        for (int i = androidLibs.size() - 1  ; i >= 0 ; i--) {
            computeFlatLibraryList(
                    dependenciesMutableData,
                    androidLibs.get(i),
                    outFlatAndroidLibs,
                    outFlatJavaLibs);
        }
    }

    private static void computeFlatAtomList(
            @NonNull DependenciesMutableData dependenciesMutableData,
            @NonNull AndroidAtom androidAtom,
            @NonNull Set<AndroidAtom> outFlatAndroidAtoms,
            @NonNull Set<AndroidLibrary> outFlatAndroidLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {
        // resolve the dependencies for those atoms
        computeFlatAtomList(
                dependenciesMutableData,
                androidAtom.getAtomDependencies(),
                outFlatAndroidAtoms,
                outFlatAndroidLibs,
                outFlatJavaLibs);

        // resolve the dependencies for those libraries
        computeFlatLibraryList(
                dependenciesMutableData,
                androidAtom.getLibraryDependencies(),
                outFlatAndroidLibs,
                outFlatJavaLibs);

        computeFlatJarList(dependenciesMutableData, androidAtom.getJavaDependencies(), outFlatJavaLibs);

        // and add the current atom (if needed) last, the lists will be reversed and it will
        // get moved in the front (higher priority)
        if (!outFlatAndroidAtoms.contains(androidAtom)) {
            outFlatAndroidAtoms.add(androidAtom);
        }
    }

    private static void computeFlatLibraryList(
            @NonNull DependenciesMutableData dependenciesMutableData,
            @NonNull AndroidLibrary androidLibrary,
            @NonNull Set<AndroidLibrary> outFlatAndroidLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {
        // resolve the dependencies for those libraries
        computeFlatLibraryList(
                dependenciesMutableData,
                androidLibrary.getLibraryDependencies(),
                outFlatAndroidLibs,
                outFlatJavaLibs);

        computeFlatJarList(dependenciesMutableData, androidLibrary.getJavaDependencies(), outFlatJavaLibs);

        // and add the current one (if needed) in back, the list will get reversed and it
        // will get moved to the front (higher priority)
        if (!dependenciesMutableData.isSkipped(androidLibrary)
                && !outFlatAndroidLibs.contains(androidLibrary)) {
            outFlatAndroidLibs.add(androidLibrary);
        }
    }

    private static void computeFlatJarList(
            @NonNull DependenciesMutableData dependenciesMutableData,
            @NonNull Collection<? extends JavaLibrary> javaLibs,
            @NonNull Set<JavaLibrary> outFlatJavaLibs) {

        for (JavaLibrary javaLib : javaLibs) {
            if (!dependenciesMutableData.isSkipped(javaLib)) {
                outFlatJavaLibs.add(javaLib);
            }
            computeFlatJarList(dependenciesMutableData, javaLib.getDependencies(), outFlatJavaLibs);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mLibraryDependencies", mLibraryDependencies)
                .add("mAtomDependencies", mAtomDependencies)
                .add("mJavaDependencies", mJavaDependencies)
                .add("mLocalJars", mLocalJars)
                .toString();
    }
}
