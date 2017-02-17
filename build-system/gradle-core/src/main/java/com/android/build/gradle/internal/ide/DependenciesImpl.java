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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** */
public class DependenciesImpl implements Dependencies, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull private final List<AndroidAtom> atoms;
    @NonNull private final List<AndroidLibrary> libraries;
    @NonNull private final List<JavaLibrary> javaLibraries;
    @NonNull private final List<String> projects;
    @Nullable private final AndroidAtom baseAtom;

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
        return Objects.equals(atoms, that.atoms)
                && Objects.equals(libraries, that.libraries)
                && Objects.equals(javaLibraries, that.javaLibraries)
                && Objects.equals(projects, that.projects)
                && Objects.equals(baseAtom, that.baseAtom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(atoms, libraries, javaLibraries, projects, baseAtom);
    }
}
