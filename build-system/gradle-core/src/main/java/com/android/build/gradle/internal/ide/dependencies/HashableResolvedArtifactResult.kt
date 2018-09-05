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

package com.android.build.gradle.internal.ide.dependencies

import com.android.SdkConstants.EXT_AAR
import com.android.SdkConstants.EXT_JAR
import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.model.MavenCoordinates
import com.google.common.base.Objects
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.component.Artifact
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File
import java.util.regex.Pattern

/**
 * Wrapper around [ResolvedArtifactResult] so that we can making hashable (implements [equals] and
 * [hashCode]) so that it can be used as a [Map] key.
 *
 * We cannot directly use a data class as [ResolvedArtifactResult] does not itself implements
 * [equals] and [hashCode].
 */
class HashableResolvedArtifactResult(
    private val delegate: ResolvedArtifactResult,
    val dependencyType: DependencyType,
    val isWrappedModule: Boolean,
    /**
     * An optional sub-result that represents the bundle file, when the current result
     * represents an exploded aar
     */
    val bundleResult: ResolvedArtifactResult?,
    val buildMapping: ImmutableMap<String, String>
) : ResolvedArtifactResult {

    enum class DependencyType constructor(val extension: String) {
        JAVA(EXT_JAR),
        ANDROID(EXT_AAR)
    }

    override fun getFile(): File {
        return delegate.file
    }

    override fun getVariant(): ResolvedVariantResult {
        return delegate.variant
    }

    override fun getId(): ComponentArtifactIdentifier {
        return delegate.id
    }

    override fun getType(): Class<out Artifact> {
        return delegate.type
    }

    /**
     * Computes Maven Coordinate for a given artifact result.
     */
    fun computeMavenCoordinates(): MavenCoordinates {

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
                if (dependencyType == DependencyType.JAVA) {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as HashableResolvedArtifactResult

        // compare the properties of [ResolvedArtifactResult] instead of the instance itself
        // as it does not implement [equals]
        return (isWrappedModule == other.isWrappedModule
                && dependencyType == other.dependencyType
                && Objects.equal(file, other.file)
                && Objects.equal(id, other.id)
                && Objects.equal(type, other.type)
                && Objects.equal(buildMapping, other.buildMapping))
    }
    override fun hashCode(): Int {
        return java.util.Objects.hash(delegate, dependencyType, isWrappedModule, buildMapping)
    }

    override fun toString(): String {
        return id.toString()
    }

}
