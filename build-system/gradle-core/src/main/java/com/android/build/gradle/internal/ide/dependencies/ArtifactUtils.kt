/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ArtifactUtils")
package com.android.build.gradle.internal.ide.dependencies

import com.android.build.api.attributes.VariantAttr
import com.android.build.gradle.internal.ide.ArtifactDependencyGraph
import com.android.build.gradle.internal.ide.ArtifactDependencyGraph.HashableResolvedArtifactResult
import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File
import java.util.regex.Pattern

private const val LOCAL_AAR_GROUPID = "__local_aars__"

fun ResolvedArtifactResult.getVariantName(): String? {
    val variantAttr = variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)
    return variantAttr?.name
}

fun HashableResolvedArtifactResult.getMavenCoordinates(): MavenCoordinates {
    return mavenCoordinatesCache.get(this) ?: throw RuntimeException("Failed to compute maven coordinates for $this")
}

/**
 * Computes a uniq address to use in the level 4 model
 */
fun HashableResolvedArtifactResult.computeModelAddress(): String = when (id.componentIdentifier) {
    is ProjectComponentIdentifier -> {
        val projectId = id.componentIdentifier as ProjectComponentIdentifier

        StringBuilder(100)
            .append(projectId.getBuildId(buildMapping))
            .append("@@")
            .append(projectId.projectPath)
            .also { sb ->
                getVariantName()?.let{ sb.append("::").append(it) }
            }
            .toString().intern()
    }
    is ModuleComponentIdentifier, is OpaqueComponentArtifactIdentifier -> {
        this.getMavenCoordinates().toString().intern()
    }
    else -> {
        throw RuntimeException(
            "Don't know how to handle ComponentIdentifier '"
                    + id.getDisplayName()
                    + "'of type "
                    + id.javaClass)

    }
}

fun clearCaches() {
    mavenCoordinatesCache.clear()
}

/**
 * Computes Maven Coordinate for a given artifact result.
 */
private fun HashableResolvedArtifactResult.computeMavenCoordinates(): MavenCoordinates {

    val id = id.componentIdentifier

    val artifactFile = file
    val fileName = artifactFile.name
    val extension = dependencyType.extension

    return when (id) {
        is ModuleComponentIdentifier -> {
            val module = id.module
            val version = id.version
            var classifier: String? = null

            if (!file.isDirectory) {
                // attempts to compute classifier based on the filename.
                val pattern = "^$module-$version-(.+)\\.$extension$"

                val p = Pattern.compile(pattern)
                val m = p.matcher(fileName)
                if (m.matches()) {
                    classifier = m.group(1)
                }
            }

            MavenCoordinatesImpl(id.group, module, version, extension, classifier)
        }

        is ProjectComponentIdentifier -> {
            MavenCoordinatesImpl("artifacts", id.projectPath, "unspecified")
        }

        is OpaqueComponentArtifactIdentifier -> {
            // We have a file based dependency
            if (dependencyType == ArtifactDependencyGraph.DependencyType.JAVA) {
                getMavenCoordForLocalFile(
                    artifactFile
                )
            } else {
                // local aar?
                assert(artifactFile.isDirectory)
                getMavenCoordForLocalFile(
                    artifactFile
                )
            }
        }

         else -> {
             throw RuntimeException(
                 "Don't know how to compute maven coordinate for artifact '"
                         + id.displayName
                         + "' with component identifier of type '"
                         + id.javaClass
                         + "'."
             )
         }
    }
}

fun getMavenCoordForLocalFile(artifactFile: File): MavenCoordinatesImpl {
    return MavenCoordinatesImpl(LOCAL_AAR_GROUPID, artifactFile.path, "unspecified")
}

private val mavenCoordinatesCache =
    CreatingCache<HashableResolvedArtifactResult, MavenCoordinates>(
        CreatingCache.ValueFactory<HashableResolvedArtifactResult, MavenCoordinates> {
            it.computeMavenCoordinates()
        })
