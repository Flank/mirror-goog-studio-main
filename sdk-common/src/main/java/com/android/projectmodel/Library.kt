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
 * Represents a dependency on an external android library. External libraries are folders containing
 * some combination of prebuilt classes, resources, and manifest file. This is named [AarLibrary]
 * since that is the standard way of packaging Android libraries, but the actual dependency is
 * on the folder where the AAR would have been extracted by the build system rather than the AAR
 * file itself. If the library came from an AAR file, the build system would extract it somewhere
 * and then provide an instance of [AarLibrary] describing the contents and location of the
 * extracted folder.
 */
data class AarLibrary(
    override val address: String,

    /**
     * Path to the .aar file on the filesystem, if known and one exists.
     *
     * The IDE doesn't work with AAR files and instead relies on the build system to extract
     * necessary files to disk. Location of the original AAR files is not always known, and some
     * [AarLibrary] instances point to folders that never came from an AAR. In such cases, this
     * attribute is null.
     */
    val location: PathString? = null,

    /**
     * Location of the manifest file for this library. This manifest contains sufficient information
     * to understand the library and its contents are intended to be merged into any application
     * that uses the library.
     *
     * Not all libraries include a manifest. For example, some libraries may only contain class
     * files and do not require any manifest content to be merged into applications that use them.
     * Other libraries may not include their own manifest but may have documentation for content
     * that the developer is expected to include inline in their app before using the library.
     * In either case, if the library does not include a manifest that the build system is expected
     * to merge into consuming apps, this attribute will be null.
     */
    val manifestFile: PathString? = null,

    /**
     * Location of any manifest file that can be used to understand the contents of this
     * [AarLibrary]. In the case of libraries that include their own manifest, this will always
     * point to [manifestFile]. In the case of libraries that don't include their own manifest,
     * this may point to any manifest file that contains sufficient information to understand
     * the contents of the library.
     *
     * For example, if the library contains resources this manifest is expected to contain the
     * correct package name for those resources. The build system may point this file to the
     * merged manifest for any application that is known to use the library, and it may
     * contain extra content that is unrelated to the library.
     *
     * If no manifest is needed to understand the library, this will be null. This manifest must
     * not be merged into the application's manifest. If the [AarLibrary] fills in this field but
     * not [manifestFile], it the needed content will already have been inlined in the
     * application's manifest.
     */
    val representativeManifestFile: PathString? = manifestFile,

    /**
     * Path to .jar file containing the library classes or null if this library does not include
     * any java classes.
     */
    val classesJar: PathString? = null,

    /**
     * Paths to jars that were packaged inside the AAR and are dependencies of it.
     *
     * This used by Gradle when a library depends on a local jar file that has no Maven coordinates,
     * so needs to be packaged together with the AAR to be accessible to client apps.
     */
    val dependencyJars: List<PathString> = emptyList(),

    /**
     * Path to the folder containing unzipped, plain-text, non-namespaced resources. Or null
     * for libraries that contain no resources.
     */
    val resFolder: PathString? = null,

    /**
     * Path to the symbol file (`R.txt`) containing information necessary to generate the
     * non-namespaced R class for this library. Null if no such file exists.
     */
    val symbolFile: PathString? = null,

    /**
     * Path to the aapt static library (`res.apk`) containing namespaced resources in proto format.
     *
     * This is only known for "AARv2" files, built from namespaced sources.
     */
    val resApkFile: PathString? = null
) : Library() {
    /**
     * Constructs a new [AarLibrary] with the given address and all other values set to their defaults. Intended to
     * simplify construction from Java.
     */
    constructor(address: String) : this(address, null)

    override fun toString(): String = printProperties(this, AarLibrary(""))

    /**
     * Returns a copy of the receiver with the given representative manifest file. Intended to simplify construction from Java.
     */
    fun withRepresentativeManifestFile(path: PathString?) = copy(representativeManifestFile = path)

    /**
     * Returns a copy of the receiver with the given manifest file. Intended to simplify construction from Java.
     */
    fun withManifestFile(path: PathString?) = copy(
        manifestFile = path,
        representativeManifestFile = if (manifestFile == representativeManifestFile) path else representativeManifestFile
    )

    /**
     * Returns a copy of the receiver with the given classes jar. Intended to simplify construction from Java.
     */
    fun withClassesJar(path: PathString?) = copy(classesJar = path)

    /**
     * Returns a copy of the receiver with the given res folder. Intended to simplify construction from Java.
     */
    fun withResFolder(path: PathString?) = copy(resFolder = path)

    /**
     * Returns a copy of the receiver with the given location. Intended to simplify construction from Java.
     */
    fun withLocation(path: PathString?) = copy(location = path)

    /**
     * Returns a copy of the receiver with the given symbol file. Intended to simplify construction from Java.
     */
    fun withSymbolFile(path: PathString?) = copy(symbolFile = path)
}

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
