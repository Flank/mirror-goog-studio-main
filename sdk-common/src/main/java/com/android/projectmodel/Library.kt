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
package com.android.projectmodel

import com.android.ide.common.util.PathString

/**
 * Represents a dependency for a [Variant].
 */
sealed class Library {
    /**
     * Globally unique identifier for this library, assigned by the build system.
     */
    abstract val address: String
}

/**
 * Represents a dependency on an .AAR file.
 */
data class AarLibrary(
    override val address: String,

    /**
     * Path to the .aar file on the filesystem, if known.
     *
     * The IDE doesn't work with whole AAR files and instead relies on the build system to extract
     * necessary files to disk. Location of the original AAR files is not always known.
     */
    val location: PathString?,

    /**
     * Location of the manifest.
     */
    val manifestFile: PathString,

    /**
     * Path to .jar file containing the library classes.
     */
    val classesJar: PathString,

    /**
     * Paths to jars that were packaged inside the AAR and are dependencies of it.
     *
     * This used by Gradle when a library depends on a local jar file that has no Maven coordinates,
     * so needs to be packaged together with the AAR to be accessible to client apps.
     */
    val dependencyJars: Collection<PathString>,

    /**
     * Path to the folder containing unzipped, plain-text, non-namespaced resources.
     */
    val resFolder: PathString,

    /**
     * Path to the symbol file (`R.txt`) containing information necessary to generate the
     * non-namespaced R class for this library.
     */
    val symbolFile: PathString,

    /**
     * Path to the aapt static library (`res.apk`) containing namespaced resources in proto format.
     *
     * This is only known for "AARv2" files, built from namespaced sources.
     */
    val resApkFile: PathString?
) : Library()

/**
 * Represents a dependency on a Java artifact (either a .jar file or a folder containing .class files).
 */
data class JavaLibrary(
    override val address: String,
    /**
     * Path to .jar file containing the library classes.
     */
    val classesJar: PathString
) : Library()

/**
 * Represents a dependency on another [AndroidProject].
 */
data class ProjectLibrary(
    override val address: String,
    /**
     * Name of the project (matches the project's [AndroidProject.name] attribute).
     */
    val projectName: String,
    /**
     * Variant of the project being depended upon (matches the variant's [Variant.name] attribute).
     */
    val variant: String
) : Library()
