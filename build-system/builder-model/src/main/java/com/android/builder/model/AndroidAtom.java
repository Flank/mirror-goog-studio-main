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

package com.android.builder.model;

import com.android.annotations.NonNull;

import java.io.File;
import java.util.List;

/**
 * Represents an Android Atombundle dependency, its content and its own dependencies.
 * The atombundle file contains the atom, the atom metadata and other files necessary to build the
 * iapk and the atoms that depend on it.
 */
public interface AndroidAtom extends AndroidBundle {

    /**
     * Returns the list direct atom dependencies of this dependency.
     * The order is important.
     */
    @NonNull
    List<? extends AndroidAtom> getAtomDependencies();

    /**
     * Returns the atom folder location.
     */
    @NonNull
    File getAtomFolder();

    /**
     * Returns the atom metadata file.
     */
    @NonNull
    File getAtomMetadataFile();

    /**
     * Returns the resource package file.
     */
    @NonNull
    File getResourcePackageFile();
}
