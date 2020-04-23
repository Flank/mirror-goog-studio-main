/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.model

import java.io.File

interface LmLibrary : Comparable<LmLibrary> {
    /** List of jar files in the library. Never empty. */
    val jarFiles: List<File>
    val project: String?
    val requestedCoordinates: LmMavenName?
    val resolvedCoordinates: LmMavenName
    val provided: Boolean
    val skipped: Boolean
    val dependencies: List<LmLibrary>

    override fun compareTo(other: LmLibrary): Int {
        return resolvedCoordinates.compareTo(other.resolvedCoordinates)
    }

    fun addJars(list: MutableList<File>, skipProvided: Boolean) {
        if (skipped) {
            return
        }
        if (skipProvided && provided) {
            return
        }

        for (jar in jarFiles) {
            if (!list.contains(jar)) {
                if (jar.exists()) {
                    list.add(jar)
                }
            }
        }
    }
}

interface LmAndroidLibrary : LmLibrary {
    val manifest: File
    val folder: File
    val resFolder: File
    val assetsFolder: File
    val lintJar: File
    val publicResources: File
    val symbolFile: File
    val externalAnnotations: File
    val projectId: String?
    val proguardRules: File
}

interface LmJavaLibrary : LmLibrary

/**
 * NOTE: This is here temporarily; in a follow-up CL this will switch over to
 * a mechanism similar to builder-model L2's dependency graph. We're leaving things
 * this way for now to keep the initial CL similar to the current behavior of lint.
 */
interface LmDependencies {
    // However, we do need nested dependencies in order to show relationship in dependency
    // chain; see for example BlacklistedDeps.
    // We also often want to look at flattened list; since I compute it here, maybe store it too.
    val direct: List<LmLibrary>
    val all: Collection<LmLibrary>
}

open class DefaultLmLibrary(
    val jarFiles: List<File>,
    val project: String?,
    val requestedCoordinates: LmMavenName?,
    val resolvedCoordinates: LmMavenName,
    val provided: Boolean,
    val skipped: Boolean,
    val dependencies: List<LmLibrary>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return resolvedCoordinates == (other as? LmAndroidLibrary)?.resolvedCoordinates
    }

    override fun hashCode(): Int {
        return resolvedCoordinates.hashCode()
    }
}

class DefaultLmAndroidLibrary(
    jarFiles: List<File>,
    override val manifest: File,
    override val folder: File,
    override val resFolder: File,
    override val assetsFolder: File,
    override val lintJar: File,
    override val publicResources: File,
    override val symbolFile: File,
    override val externalAnnotations: File,
    override val projectId: String?,
    override val proguardRules: File,
    project: String?,
    provided: Boolean,
    skipped: Boolean,
    requestedCoordinates: LmMavenName?,
    resolvedCoordinates: LmMavenName,
    dependencies: List<LmLibrary>
) : DefaultLmLibrary(
    jarFiles = jarFiles,
    project = project,
    requestedCoordinates = requestedCoordinates,
    resolvedCoordinates = resolvedCoordinates,
    provided = provided,
    skipped = skipped,
    dependencies = dependencies
), LmAndroidLibrary {
    override fun toString(): String = "AndroidLibrary(${project ?: resolvedCoordinates})"
}

class DefaultLmJavaLibrary(
    jarFiles: List<File>,
    project: String?,
    requestedCoordinates: LmMavenName?,
    resolvedCoordinates: LmMavenName,
    provided: Boolean,
    skipped: Boolean,
    dependencies: List<LmLibrary>
) : DefaultLmLibrary(
    jarFiles = jarFiles,
    project = project,
    requestedCoordinates = requestedCoordinates,
    resolvedCoordinates = resolvedCoordinates,
    provided = provided,
    skipped = skipped,
    dependencies = dependencies
), LmJavaLibrary {
    override fun toString(): String = "JavaLibrary(${project ?: resolvedCoordinates})"
}

class DefaultLmDependencies(
    // However, we do need nested dependencies in order to show relationship in dependency
    // chain; see for example BlacklistedDeps.
    // We also often want to look at flattened list; since I compute it here, maybe store it too.
    override val direct: List<LmLibrary>,
    override val all: Collection<LmLibrary>
) : LmDependencies
