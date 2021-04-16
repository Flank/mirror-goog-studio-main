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

package com.android.build.gradle.internal.ide.v2

import com.android.SdkConstants
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.ide.dependencies.LOCAL_AAR_GROUPID
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.ide.common.caching.CreatingCache
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File
import java.lang.IllegalArgumentException

/**
 * Build Service used to aggregate all instances of [Library], across all sub-projects, during sync.
 */
abstract class GlobalLibraryBuildService: BuildService<GlobalLibraryBuildService.Parameters>, AutoCloseable {

    interface Parameters: BuildServiceParameters {
        val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>
    }

    class RegistrationAction(
        project: Project,
        private val mavenCoordinatesCache: Provider<MavenCoordinatesCacheBuildService>
    ) : ServiceRegistrationAction<GlobalLibraryBuildService, Parameters>(
        project,
        GlobalLibraryBuildService::class.java
    ) {
        override fun configure(parameters: Parameters) {
            parameters.mavenCoordinatesCache.set(mavenCoordinatesCache)
        }
    }

    // do not query directly. Use [getLibrary]
    private val libraryCache = mutableMapOf<ResolvedArtifact, Library>()
    /**
     * Returns a [Library] instance matching the provided a [ResolvedArtifact].
     */
    internal fun getLibrary(artifact: ResolvedArtifact): Library =
            synchronized(libraryInfoCache) {
                libraryCache.computeIfAbsent(artifact) {
                    createLibrary(it)
                }
            }

    internal fun createModel(): GlobalLibraryMap {
        return GlobalLibraryMapImpl(libraryCache.values.associateBy { it.key })
    }

    private val stringCache = mutableMapOf<String, String>()

    private fun cacheString(string: String): String {
        synchronized(stringCache) {
            return stringCache.putIfAbsent(string, string) ?: string
        }
    }

    // do not query directly. Use [getProjectInfo]
    private val projectInfoCache = mutableMapOf<ResolvedVariantResult, ProjectInfoImpl>()
    private fun getProjectInfo(variant: ResolvedVariantResult): ProjectInfoImpl =
            synchronized(projectInfoCache) {
                projectInfoCache.computeIfAbsent(variant) {
                    val component = it.owner as ProjectComponentIdentifier

                    ProjectInfoImpl(
                        getAttributeMap(it),
                        getCapabilityList(it),
                        cacheString(component.build.name),
                        cacheString(component.projectPath)
                    )
                }
            }

    // do not query directly. Use [getLibraryCache]
    private val libraryInfoCache = mutableMapOf<ResolvedVariantResult, LibraryInfoImpl>()
    // do not query directly. Use [getLibraryCache]
    private val libraryInfoForLocalJarsCache = mutableMapOf<File, LibraryInfoImpl>()
    private fun getLibraryInfo(artifact: ResolvedArtifact): LibraryInfoImpl =
            // we have to handle differently the case of external libraries which can be represented
            // uniquely by their ResolvedVariantResult and local jars which must
            // be represented, in theory, by a mix of path and variants (for the attributes).
            // In practice the attributes aren't needed really since there's no way to have a
            // local jar be variant aware. So we can take a shortcut and only consider the file
            // itself and skip the attributes. (there is already no capabilities for local jars)
            when (val component = artifact.variant.owner) {
                is ModuleComponentIdentifier -> {
                    synchronized(libraryInfoCache) {
                        // simply query for the variant.
                        libraryInfoCache.computeIfAbsent(artifact.variant) {
                            LibraryInfoImpl(
                                getAttributeMap(it),
                                getCapabilityList(it),
                                cacheString(component.group),
                                cacheString(component.module),
                                cacheString(component.version)
                            )
                        }
                    }
                }
                is OpaqueComponentArtifactIdentifier -> {
                    synchronized(libraryInfoForLocalJarsCache) {
                        libraryInfoForLocalJarsCache.computeIfAbsent(artifact.artifactFile) {
                            LibraryInfoImpl(
                                attributes = mapOf(),
                                capabilities = listOf(),
                                group = cacheString(LOCAL_AAR_GROUPID),
                                name = cacheString(it.absolutePath),
                                version = cacheString("unspecified")
                            )
                        }
                    }
                }
                else -> {
                    throw IllegalArgumentException("${artifact.variant.owner} is not supported for LibraryInfo")
                }
            }

    /**
     * a [CreatingCache] that computes, and cache the list of jars in a extracted AAR folder
     */
    private val jarFromExtractedAarCache = CreatingCache<File, List<File>> {
        val localJarRoot = FileUtils.join(it, SdkConstants.FD_JARS, SdkConstants.FD_AAR_LIBS)

        if (!localJarRoot.isDirectory) {
            ImmutableList.of()
        } else {
            val jarFiles = localJarRoot.listFiles { _, name -> name.endsWith(SdkConstants.DOT_JAR) }
            if (!jarFiles.isNullOrEmpty()) {
                // Sort by name, rather than relying on the file system iteration order
                ImmutableList.copyOf(jarFiles.sortedBy(File::getName))
            } else ImmutableList.of()
        }
    }

    override fun close() {
        libraryCache.clear()
        jarFromExtractedAarCache.clear()
        projectInfoCache.clear()
        libraryInfoCache.clear()
        stringCache.clear()
    }

    /**
     * Handles an artifact.
     *
     * This optionally returns the model item that represents the artifact in case something needs
     * use the return
     */
    private fun createLibrary(
        artifact: ResolvedArtifact,
    ) : Library {
        val id = artifact.componentIdentifier

        return if (id !is ProjectComponentIdentifier || artifact.isWrappedModule) {
            val libraryInfo = getLibraryInfo(artifact)
            if (artifact.dependencyType === ResolvedArtifact.DependencyType.ANDROID) {
                val folder = artifact.extractedFolder
                        ?: throw RuntimeException("Null extracted folder for artifact: $artifact")

                val apiJar = FileUtils.join(folder, SdkConstants.FN_API_JAR)
                val runtimeJar = FileUtils.join(
                    folder,
                    SdkConstants.FD_JARS,
                    SdkConstants.FN_CLASSES_JAR
                )

                val runtimeJarFiles = listOf(runtimeJar) + (jarFromExtractedAarCache.get(folder) ?: listOf())
                LibraryImpl(
                    key = cacheString(libraryInfo.computeKey()),
                    type = LibraryType.ANDROID_LIBRARY,
                    libraryInfo = libraryInfo,
                    manifest = File(folder, SdkConstants.FN_ANDROID_MANIFEST_XML),
                    compileJarFiles = if (apiJar.isFile) listOf(apiJar) else runtimeJarFiles,
                    runtimeJarFiles = runtimeJarFiles,
                    resFolder = File(folder, SdkConstants.FD_RES),
                    resStaticLibrary = File(folder, SdkConstants.FN_RESOURCE_STATIC_LIBRARY),
                    assetsFolder = File(folder, SdkConstants.FD_ASSETS),
                    jniFolder = File(folder, SdkConstants.FD_JNI),
                    aidlFolder = File(folder, SdkConstants.FD_AIDL),
                    renderscriptFolder = File(folder, SdkConstants.FD_RENDERSCRIPT),
                    proguardRules = File(folder, SdkConstants.FN_PROGUARD_TXT),
                    externalAnnotations = File(folder, SdkConstants.FN_ANNOTATIONS_ZIP),
                    publicResources = File(folder, SdkConstants.FN_PUBLIC_TXT),
                    symbolFile = File(folder, SdkConstants.FN_RESOURCE_TEXT),

                    lintJar = FileUtils.join(folder, SdkConstants.FD_JARS, SdkConstants.FN_LINT_JAR),
                    artifact = artifact.artifactFile,

                    // not needed for this dependency type
                    projectInfo = null,
                )
            } else {
                LibraryImpl(
                    key = cacheString(libraryInfo.computeKey()),
                    type = LibraryType.JAVA_LIBRARY,
                    libraryInfo = libraryInfo,
                    artifact = artifact.artifactFile,

                    // not needed for this dependency type
                    projectInfo = null,
                    manifest = null,
                    compileJarFiles = null,
                    runtimeJarFiles = null,
                    resFolder = null,
                    resStaticLibrary = null,
                    assetsFolder = null,
                    jniFolder = null,
                    aidlFolder = null,
                    renderscriptFolder = null,
                    proguardRules = null,
                    lintJar = null,
                    externalAnnotations = null,
                    publicResources = null,
                    symbolFile = null
                )
            }
        } else {
            val projectInfo = getProjectInfo(artifact.variant)

            if (artifact.dependencyType === ResolvedArtifact.DependencyType.ANDROID) {
                LibraryImpl(
                    key = cacheString(projectInfo.computeKey()),
                    type = LibraryType.PROJECT,
                    projectInfo = projectInfo,

                    lintJar = null, // FIXME?

                    // not needed for this dependency type
                    libraryInfo = null,
                    artifact = null,
                    manifest = null,
                    compileJarFiles = null,
                    runtimeJarFiles = null,
                    resFolder = null,
                    resStaticLibrary = null,
                    assetsFolder = null,
                    jniFolder = null,
                    aidlFolder = null,
                    renderscriptFolder = null,
                    proguardRules = null,
                    externalAnnotations = null,
                    publicResources = null,
                    symbolFile = null
                )
            } else {
                LibraryImpl(
                    key = cacheString(projectInfo.computeKey()),
                    type = LibraryType.PROJECT,
                    projectInfo = projectInfo,

                    // not needed for this dependency type
                    libraryInfo = null,
                    artifact = null,
                    manifest = null,
                    compileJarFiles = null,
                    runtimeJarFiles = null,
                    resFolder = null,
                    resStaticLibrary = null,
                    assetsFolder = null,
                    jniFolder = null,
                    aidlFolder = null,
                    renderscriptFolder = null,
                    proguardRules = null,
                    externalAnnotations = null,
                    publicResources = null,
                    lintJar = null,
                    symbolFile = null
                )
            }
        }
    }

    private fun getAttributeMap(variant: ResolvedVariantResult): Map<String, String> =
            variant.attributes.keySet().mapNotNull { key ->
                val attr = variant.attributes.getAttribute(key)
                attr?.let { cacheString(key.name) to cacheString(it.toString()) }
            }.toMap()

    private fun getCapabilityList(variant: ResolvedVariantResult): List<String> =
            variant.capabilities.map { cacheString("${it.group}:${it.name}:${it.version}") }
}

