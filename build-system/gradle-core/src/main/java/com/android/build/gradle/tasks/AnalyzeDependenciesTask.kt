/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import com.google.gson.GsonBuilder
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.LinkedList
import java.util.zip.ZipFile

// TODO: Make incremental
abstract class AnalyzeDependenciesTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var variantArtifact: Provider<FileCollection>
        private set

    private lateinit var externalArtifactCollection: ArtifactCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val externalArtifacts: FileCollection by lazy { externalArtifactCollection.artifactFiles }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    // Don't need to be marked as input as they are represented in externalArtifacts
    private var apiDirectDependenciesConfiguration: Configuration? = null
    private lateinit var allDirectDependencies: Collection<Dependency>

    override fun doTaskAction() {
        val variantDepsHolder = VariantDependenciesHolder(
            allDirectDependencies,
            apiDirectDependenciesConfiguration?.allDependencies)
        val variantClassHolder = VariantClassesHolder(variantArtifact.get())
        val classFinder = ClassFinder(externalArtifactCollection)

        val depsUsageFinder =
            DependencyUsageFinder(classFinder, variantClassHolder, variantDepsHolder)
        val graphAnalyzer = DependencyGraphAnalyzer(
            variantName,
            externalArtifactCollection,
            classFinder,
            depsUsageFinder)

        val reporter = DependencyUsageReporter(
            variantClassHolder,
            variantDepsHolder,
            classFinder,
            depsUsageFinder,
            graphAnalyzer)

        reporter.writeUnusedDependencies(
            File(
                outputDirectory.asFile.get(),
                "dependenciesReport.json"))

        reporter.writeMisconfiguredDependencies(
            File(
                outputDirectory.asFile.get(),
                "apiToImplementation.json"))
    }

    class VariantDependenciesHolder(
        _directAllDependencies: Collection<Dependency>,
        _directApiDependencies: Collection<Dependency>?) {

        val all = getDependenciesIds(_directAllDependencies)
        val api = getDependenciesIds(_directApiDependencies)

        private fun getDependenciesIds(dependencies: Collection<Dependency>?) =
            dependencies?.mapNotNull { buildDependencyId(it) }?.toSet() ?: emptySet()

        private fun buildDependencyId(dependency: Dependency): String? {
            if (dependency.group == null) {
                return null
            }

            var id = "${dependency.group}:${dependency.name}"
            if (dependency.version != null) {
                id += ":${dependency.version}"
            }

            return id
        }
    }

    class VariantClassesHolder(private val variantArtifact: FileCollection) {

        private enum class CLASS_TYPE { ALL, PRIVATE }

        private val analyzer = DependenciesAnalyzer()

        private val classesByType: Map<CLASS_TYPE, Set<String>> by lazy {
            val classesUsedInVariant = mutableSetOf<String>()
            val classesExposedByPublicApis = mutableSetOf<String>()

            variantArtifact.files.forEach { file ->
                file.walk().forEach { classFile ->
                    val name = classFile.name
                    if (classFile.isFile && name.endsWith(SdkConstants.DOT_CLASS)) {
                        classesUsedInVariant.addAll(
                            analyzer.findAllDependencies(classFile.inputStream()))
                        classesExposedByPublicApis.addAll(
                            analyzer.findPublicDependencies(classFile.inputStream()))
                    }
                }
            }

            mapOf(
                CLASS_TYPE.ALL to classesUsedInVariant,
                CLASS_TYPE.PRIVATE to classesUsedInVariant.minus(classesExposedByPublicApis))
        }

        /** Returns classes used inside our variant code. */
        fun getUsedClasses() = classesByType[CLASS_TYPE.ALL] ?: emptySet()

        /** Returns classes not exposed in any public method/fields/etc in our variant code. */
        fun getPrivateClasses() = classesByType[CLASS_TYPE.PRIVATE] ?: emptySet()
    }

    /** Finds where a class is coming from. */
    class ClassFinder(private val externalArtifactCollection: ArtifactCollection) {

        private val classToDependency: Map<String, String> by lazy {
            val map = mutableMapOf<String, String>()
            externalArtifactCollection
                .filter { it.file.name.endsWith(SdkConstants.DOT_JAR) }
                .forEach { artifact ->
                    val classNamesInJar = getClassFilesInJar(artifact.file)
                    classNamesInJar.forEach { artifactClass ->
                        map[artifactClass] = artifact.id.componentIdentifier.displayName
                    }
                }
            map
        }

        /** Returns the dependency that contains {@code className} or null if we can't find it. */
        fun find(className: String) = classToDependency[className]

        fun findClassesInDependency(dependencyId: String) =
            classToDependency.filterValues { it == dependencyId }.keys

        private fun getClassFilesInJar(jarFile: File): List<String> {
            val classes = mutableListOf<String>()

            val zipFile = ZipFile(jarFile)

            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                    classes.add(entry.name)
                }
            }

            return classes
        }
    }

    /** Finds used/unused dependencies in our variant. */
    class DependencyUsageFinder(
        private val classFinder: ClassFinder,
        private val variantClasses: VariantClassesHolder,
        private val variantDependencies: VariantDependenciesHolder) {

        /** All the dependencies required across our code base. */
        val requiredDependencies: Set<String> =
            variantClasses.getUsedClasses().mapNotNull { classFinder.find(it) }.toSet()

        /** Dependencies we direct declare and are being used. */
        val usedDirectDependencies: Set<String> =
            variantDependencies.all.intersect(requiredDependencies)

        /** Dependencies we direct declare and are not being used. */
        val unusedDirectDependencies: Set<String> =
            variantDependencies.all.minus(requiredDependencies)

    }

    /** Find required dependencies that are being included indirectly and would be unreachable if
     *  we remove unused direct dependencies. */
    class DependencyGraphAnalyzer(
        private val variantName: String,
        private val externalArtifactCollection: ArtifactCollection,
        private val classFinder: ClassFinder,
        private val depsUsageFinder: DependencyUsageFinder) {

        private val inferredGraph = computeInferredDependencyGraph(classFinder)

        fun findIndirectRequiredDependencies(): Set<String> {
            return depsUsageFinder.requiredDependencies.minus(findAccessibleDependencies())
        }

        private fun findAccessibleDependencies(): Set<String> {
            val visited = mutableSetOf<String>()
            val queue = LinkedList<String>()
            queue.push(variantName)

            while (!queue.isEmpty()) {
                val module = queue.pop()
                visited.add(module)
                inferredGraph[module]?.forEach {
                    if (!visited.contains(it)) {
                        queue.push(it)
                    }
                }
            }

            return visited
        }

        private fun computeInferredDependencyGraph(classFinder: ClassFinder)
                : Map<String, Set<String>> {
            val graph = mutableMapOf<String, Set<String>>()

            // Add APP's direct dependencies to the graph
            graph[variantName] = depsUsageFinder.usedDirectDependencies

            // Build the dependency graph from all transitive artifacts
            externalArtifactCollection
                .filter { it.file.name.endsWith(SdkConstants.DOT_JAR) }
                .forEach { artifact ->
                    val libraryId = artifact.id.componentIdentifier.displayName

                    graph[libraryId] = classFinder.findClassesInDependency(libraryId)
                        .mapNotNull { classFinder.find(it) }
                        .toSet()
                }

            return graph
        }
    }

    class DependencyUsageReporter(
        private val variantClasses: VariantClassesHolder,
        private val variantDependencies: VariantDependenciesHolder,
        private val classFinder: ClassFinder,
        private val depsUsageFinder: DependencyUsageFinder,
        private val graphAnalyzer: DependencyGraphAnalyzer) {

        fun writeUnusedDependencies(destinationFile: File) {
            val report = mapOf(
                "remove" to depsUsageFinder.unusedDirectDependencies,
                "add" to graphAnalyzer.findIndirectRequiredDependencies())

            writeToFile(report, destinationFile)
        }

        fun writeMisconfiguredDependencies(destinationFile: File) {
            val misconfiguredDependencies = variantClasses.getPrivateClasses()
                .mapNotNull { classFinder.find(it) }
                .filter { variantDependencies.api.contains(it) }

            writeToFile(misconfiguredDependencies, destinationFile)
        }

        private fun writeToFile(output: Any, destinationFile: File) {
            val gson = GsonBuilder().setPrettyPrinting().create()
            FileUtils.writeToFile(destinationFile, gson.toJson(output))
            println(destinationFile.path)
        }
    }

    class CreationAction(val scope: VariantScope) :
        VariantTaskCreationAction<AnalyzeDependenciesTask>(scope) {

        override val name: String
            get() = scope.getTaskName("analyze", "Dependencies")
        override val type: Class<AnalyzeDependenciesTask>
            get() = AnalyzeDependenciesTask::class.java

        override fun configure(task: AnalyzeDependenciesTask) {
            super.configure(task)

            task.variantArtifact = scope.artifacts
                .getFinalProductAsFileCollection(AnchorOutputType.ALL_CLASSES)

            task.externalArtifactCollection = scope
                .getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.CLASSES)

            task.apiDirectDependenciesConfiguration = scope
                .variantDependencies
                .getElements(AndroidArtifacts.PublishedConfigType.API_ELEMENTS)

            task.allDirectDependencies = scope
                .variantDependencies
                .incomingRuntimeDependencies
        }

        override fun handleProvider(taskProvider: TaskProvider<out AnalyzeDependenciesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.ANALYZE_DEPENDENCIES_REPORT,
                operationType = BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider = taskProvider,
                productProvider = AnalyzeDependenciesTask::outputDirectory,
                fileName = "analyzeDependencies")
        }
    }

}