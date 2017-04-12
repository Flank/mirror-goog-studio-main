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

package com.android.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.Collection;

/**
 * A set of dependencies for an {@link AndroidArtifact}.
 */
public interface Dependencies {

    /**
     * The list of Android atom dependencies.
     *
     * <p>The list contains direct dependencies only, which themselves contain their transitive
     * dependencies.
     *
     * <p>This is only valid for versions 2.3 and 2.4. On version 2.5+ this is empty.
     *
     * <p>Atoms can not be external dependencies, so {@link Library#getProject()} never returns
     * null.
     *
     * @return the list of atoms.
     * @since 2.3
     */
    @NonNull
    @Deprecated
    Collection<AndroidAtom> getAtoms();

    /**
     * The list of Android library dependencies.
     *
     * The list contains direct dependencies only, which themselves contain their transitive
     * dependencies.
     *
     * On version &lt; 2.2, only the Android transitive dependencies are included.
     * On version 2.2+, both Java and Android transitive dependencies are included.
     *
     * This includes both modules and external dependencies. They can be differentiated with
     * {@link Library#getProject()}.
     *
     * @return the list of libraries.
     */
    @NonNull
    Collection<AndroidLibrary> getLibraries();

    /**
     * The list of Java library dependencies.
     *
     * On version &lt; 2.2, this includes only the external dependencies (both remote and local), and
     * all dependencies are represented directly in the list, flattened from the normal dependency
     * graph.
     *
     * On version 2.2+, this includes both modules and external dependencies, which can be
     * differentiated with {@link Library#getProject()}. Also, the collection
     * contains only the direct dependencies, which themselves contain their transitive
     * dependencies.
     *
     * @return the list of Java library dependencies.
     */
    @NonNull
    Collection<JavaLibrary> getJavaLibraries();

    /**
     * The list of project dependencies. This is only for non Android module dependencies (which
     * right now is Java-only modules).
     *
     * This is only valid for version &lt; 2.2. On version 2.2+ this list is empty.
     *
     * @return the list of projects.
     *
     * @see #getJavaLibraries()
     */
    @NonNull
    @Deprecated
    Collection<String> getProjects();

    /**
     * Returns the base atom, if applicable.
     *
     * <p>This is only valid for versions 2.3 and 2.4. On version 2.5+ this is null.
     *
     * @return the base atom. Or null if this artifact is the base atom or there is no base atom,
     *     this will be the case when the module is not part of an instant app.
     * @since 2.3
     */
    @Nullable
    @Deprecated
    AndroidAtom getBaseAtom();
}
