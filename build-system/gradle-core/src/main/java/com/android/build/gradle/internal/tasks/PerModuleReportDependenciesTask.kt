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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library
import com.android.tools.build.libraries.metadata.LibraryDependencies
import com.android.tools.build.libraries.metadata.MavenLibrary
import com.android.tools.build.libraries.metadata.ModuleDependencies
import com.google.protobuf.ByteString
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/** Task that publishes the app dependencies proto for each app or dynamic feature module. */
abstract class PerModuleReportDependenciesTask : NonIncrementalTask() {

    private lateinit var runtimeClasspathArtifacts: ArtifactCollection

    @get:Internal
    abstract val runtimeClasspathName: Property<String>

    // Don't use @Classpath here as @Classpath ignores some of the contents whereas the output of
    // this task contains the hashes of the entire contents.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val runtimeClasspathFiles: FileCollection by lazy { runtimeClasspathArtifacts.artifactFiles }

    @get:OutputFile
    abstract val dependencyReport: RegularFileProperty

    @get:Input
    abstract val moduleName: Property<String>

    private fun getFileDigest(file: File): ByteString {
        return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
    }

    override fun doTaskAction() {
        // FIXME: Project usage during taskAction breaks Configuration Caching (b/162074215)
        //   Blocking issue in Gradle: (https://github.com/gradle/gradle/issues/12871)
        val runtimeClasspath = project.configurations.getByName(runtimeClasspathName.get())

        // FIXME: stop resolving classpath manually (b/159989590)
        val artifacts = runtimeClasspath.incoming.artifactView { config ->
            config.componentFilter { id -> id !is ProjectComponentIdentifier }
        }.artifacts
        val componentDigestMap = artifacts.groupBy(
            keySelector = { artifact -> artifact.id.componentIdentifier },
            valueTransform = { artifact -> artifact.file }
        )
            .mapValues { (id, fileList) ->
                val fileSet = fileList.toSet()
                if (fileSet.size > 1)
                    logger.warn("Component ${id.displayName} maps to multiple files")
                getFileDigest(fileSet.first())
            }

        // Declare local classes used for intermediate data representations
        class InternalGraphNode(
            val id: ComponentIdentifier,
            val neighborIdList: List<ComponentIdentifier>,
            // val repoName: String?, //TODO uncomment and populate in upcoming CL
        )
        class InternalLibraryMetadata(id: ComponentIdentifier, val sha256: ByteString?) {
            val mavenLibrary: MavenLibraryData? = (id as? ModuleComponentIdentifier)?.let {
                MavenLibraryData(
                    groupId = it.group,
                    artifactId = it.module,
                    version = it.version,
                )
            }
            inner class MavenLibraryData(
                val groupId: String,
                val artifactId: String,
                val version: String,
            )
        }
        class InternalLibraryDependenciesMetadata(
            val libIndex: Int,
            val libDepIndices: List<Int>,
        )


        // Build the graph representation, partitioning into "Project" and "Library" nodes
        //   and removing all edges (dependencies) that point to Projects
        val resolutionResult = runtimeClasspath.incoming.resolutionResult
        val (projectNodes, libraryNodesPartialList) = resolutionResult.allComponents.asSequence().map { component ->
            InternalGraphNode(
                id = component.id,
                neighborIdList = component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .map { it.selected.id }
                    .filter { it !is ProjectComponentIdentifier }
            )
        }.distinctBy { it.id }.partition { it.id is ProjectComponentIdentifier }

        // Calculate the list of IDs which correspond to an Artifact, but not to a Node in the graph
        //   This includes direct dependencies on files (like Jar files)
        val unusedIds = componentDigestMap.keys.minus(libraryNodesPartialList.map { it.id })

        // Add a node for each unused ID
        val libraryNodes = libraryNodesPartialList +
                unusedIds.asSequence().map { unusedId -> InternalGraphNode(unusedId, listOf()) }

        // Build an intermediate sequence of InternalLibraryMetadata objects
        val intermediateLibraries = libraryNodes.asSequence().map { node ->
            InternalLibraryMetadata(node.id, componentDigestMap[node.id])
        }

        // Build a map of ComponentId->Index
        val idToIndexMap = libraryNodes.associateIndexed { index, it -> it.id to index}
        // Build an intermediate sequence of InternalLibraryDependenciesMetadata objects
        val intermediateLibraryDependencies = libraryNodes.asSequence().map { node ->
            InternalLibraryDependenciesMetadata(
                libIndex = idToIndexMap[node.id]!!,
                libDepIndices = node.neighborIdList.mapNotNull { id -> idToIndexMap[id] }
            )
        }.filter { it.libDepIndices.isNotEmpty() }

        // Compute the set of "direct dependencies" for the ModuleDependencies proto
        val directDepIndices =
            projectNodes
                .asSequence()
                .flatMap { it.neighborIdList.asSequence() } // all Libraries upon which a Project depends directly
                .plus(unusedIds) // all Libraries not accounted for in the original graph
                .mapNotNull { id -> idToIndexMap[id] }
                .toSet()


        // Convert internal object representations to Protobufs
        val libraryProtos = intermediateLibraries.map { libObj ->
            Library.newBuilder().apply {
                if (libObj.mavenLibrary != null)
                    mavenLibrary = MavenLibrary.newBuilder().apply {
                        groupId = libObj.mavenLibrary.groupId
                        artifactId = libObj.mavenLibrary.artifactId
                        version = libObj.mavenLibrary.version
                    }.build()
                if (libObj.sha256 != null) {
                    digests = Library.Digests.newBuilder().setSha256(libObj.sha256).build()
                }
            }.build()
        }
        val libDepProtos = intermediateLibraryDependencies.map { libDepObj ->
            LibraryDependencies.newBuilder().apply {
                libraryIndex = libDepObj.libIndex
                addAllLibraryDepIndex(libDepObj.libDepIndices)
            }.build()
        }
        val moduleDependenciesProto = ModuleDependencies.newBuilder().apply {
            moduleName = this@PerModuleReportDependenciesTask.moduleName.get()
            addAllDependencyIndex(directDepIndices)
        }.build()
        val appDependenciesProto = AppDependencies.newBuilder().apply {
            libraryProtos.forEach { addLibrary(it) }
            libDepProtos.forEach { addLibraryDependencies(it) }
            addModuleDependencies(moduleDependenciesProto)
        }.build()

        // Write the final Protobuf to a file
        FileOutputStream(dependencyReport.get().asFile).use { appDependenciesProto.writeTo(it) }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<PerModuleReportDependenciesTask, ApkCreationConfig>(
        creationConfig
    ) {
        override val name: String = computeTaskName("collect", "Dependencies")
        override val type: Class<PerModuleReportDependenciesTask> = PerModuleReportDependenciesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PerModuleReportDependenciesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PerModuleReportDependenciesTask::dependencyReport
            ).withName("dependencies.pb").on(InternalArtifactType.METADATA_LIBRARY_DEPENDENCIES_REPORT)
        }

        override fun configure(
            task: PerModuleReportDependenciesTask
        ) {
            super.configure(task)
            task.runtimeClasspathName.set(creationConfig.variantDependencies.runtimeClasspath.name)
            task.runtimeClasspathArtifacts = creationConfig.variantDependencies.getArtifactCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.EXTERNAL,
                // Query for JAR instead of PROCESSED_JAR as this task works with unprocessed (a/j)ars
                AndroidArtifacts.ArtifactType.AAR_OR_JAR
            )


            if (creationConfig is DynamicFeatureCreationConfig) {
                task.moduleName.setDisallowChanges(creationConfig.featureName)
            } else {
                task.moduleName.setDisallowChanges("base")
            }
        }
    }
}

// Wrapper for [Iterable.associate] with semantics like [Iterable.mapIndexed]
private inline fun <T, K, V> Iterable<T>.associateIndexed(transform: (index: Int, T) -> Pair<K, V>): Map<K, V> {
    var index = 0
    return associate { transform(index++, it) }
}
