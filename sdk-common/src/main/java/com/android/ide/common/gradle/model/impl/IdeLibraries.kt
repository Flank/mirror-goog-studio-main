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
package com.android.ide.common.gradle.model.impl

import com.android.builder.model.AndroidLibrary
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.gradle.model.IdeMavenCoordinates
import com.android.utils.FileUtils
import java.io.File

object IdeLibraries {
    /**
     * @param library Instance of level 1 Library.
     * @return The artifact address that can be used as unique identifier in global library map.
     */
    fun computeAddress(library: Library): String {
        // If the library is an android module dependency, use projectId:projectPath::variant as unique identifier.
        // MavenCoordinates cannot be used because it doesn't contain variant information, which results
        // in the same MavenCoordinates for different variants of the same module.
        try {
            if (library.project != null && library is AndroidLibrary) {
                return ((IdeModel.copyNewProperty({ library.getBuildId() }, "")).orEmpty()
                        + library.getProject()
                        + "::"
                        + library.projectVariant)
            }
        } catch (ex: UnsupportedOperationException) {
            // getProject() isn't available for pre-2.0 plugins. Proceed with MavenCoordinates.
            // Anyway pre-2.0 plugins don't have variant information for module dependency.
        }
        val coordinate: IdeMavenCoordinates = computeResolvedCoordinate(library, ModelCache())
        var artifactId = coordinate.artifactId
        if (artifactId.startsWith(":")) {
            artifactId = artifactId.substring(1)
        }
        artifactId = artifactId.replace(':', '.')
        var address = coordinate.groupId + ":" + artifactId + ":" + coordinate.version
        val classifier = coordinate.classifier
        if (classifier != null) {
            address = "$address:$classifier"
        }
        val packaging = coordinate.packaging
        address = "$address@$packaging"
        return address
    }

    /**
     * @param projectIdentifier Instance of ProjectIdentifier.
     * @return The artifact address that can be used as unique identifier in global library map.
     */
    fun computeAddress(projectIdentifier: Dependencies.ProjectIdentifier): String {
        return projectIdentifier.buildId + "@@" + projectIdentifier.projectPath
    }

    /** Indicates whether the given library is a module wrapping an AAR file.  */
    fun isLocalAarModule(
        androidLibrary: AndroidLibrary,
        buildFolderPaths: BuildFolderPaths
    ): Boolean {
        val projectPath = androidLibrary.project ?: return false
        val buildFolderPath = buildFolderPaths.findBuildFolderPath(
            projectPath,
            IdeModel.copyNewProperty({ androidLibrary.buildId }, null)
        )
        // If the aar bundle is inside of build directory, then it's a regular library module dependency, otherwise it's a wrapped aar module.
        return (buildFolderPath != null
                && !FileUtils.isFileInDirectory(androidLibrary.bundle, buildFolderPath))
    }

    fun computeResolvedCoordinate(
        library: Library,
        modelCache: ModelCache
    ): IdeMavenCoordinatesImpl {
        // Although getResolvedCoordinates is annotated with @NonNull, it can return null for plugin 1.5,
        // when the library dependency is from local jar.
        return if (library.resolvedCoordinates != null) {
            modelCache.computeIfAbsent(
                library.resolvedCoordinates,
                { coordinates: MavenCoordinates -> ModelCache.mavenCoordinatesFrom(coordinates) })
        } else {
            val jarFile: File =
                if (library is JavaLibrary) {
                    library.jarFile
                } else {
                    (library as AndroidLibrary).bundle
                }
            ModelCache.mavenCoordinatesFrom(jarFile)
        }
    }
}