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

package com.android.build.gradle.internal.res.namespaced

import android.databinding.tool.util.Preconditions
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.sdklib.IAndroidTarget
import com.android.tools.build.apkzlib.zip.StoredEntryType
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask

/**
 * Rewrites the non-namespaced AAR dependencies of this module to be namespaced.
 *
 * 1. Build a model of where the resources of non-namespaced AARs have come from.
 * 2. Rewrites the classes.jar to be namespaced
 * 3. Rewrites the manifest to use namespaced resource references
 * 4. Rewrites the resources themselves to use namespaced references, and compiles
 *    them in to a static library.
 */
@CacheableTask
open class AutoNamespaceDependenciesTask : AndroidBuilderTask() {

    lateinit var rFiles: ArtifactCollection private set
    lateinit var nonNamespacedManifests: ArtifactCollection private set
    lateinit var jarFiles: ArtifactCollection private set
    lateinit var dependencies: ResolvableDependencies private set
    lateinit var externalNotNamespacedResources: ArtifactCollection private set
    lateinit var externalResStaticLibraries: ArtifactCollection private set

    @InputFiles fun getRDefFiles(): FileCollection = rFiles.artifactFiles
    @InputFiles fun getManifestsFiles(): FileCollection = nonNamespacedManifests.artifactFiles
    @InputFiles fun getClassesJarFiles(): FileCollection = jarFiles.artifactFiles
    @InputFiles
    fun getNonNamespacedResourcesFiles(): FileCollection =
        externalNotNamespacedResources.artifactFiles

    @InputFiles
    fun getStaticLibraryDependenciesFiles(): FileCollection =
        externalResStaticLibraries.artifactFiles

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    @VisibleForTesting internal var log: Logger? = null

    /**
     * Reading the R files and building symbol tables is costly, and is wasteful to repeat,
     * hence, use a LoadingCache.
     */
    private var symbolTablesCache: LoadingCache<File, SymbolTable> = CacheBuilder.newBuilder()
        .build(
            object : CacheLoader<File, SymbolTable>() {
                override fun load(rDefFile: File): SymbolTable {
                    return SymbolIo.readRDef(rDefFile.toPath())
                }
            }
        )

    @get:OutputDirectory
    lateinit var outputStaticLibraries: File
        private set
    @get:OutputFile lateinit var outputClassesJar: File private set
    @get:OutputFile lateinit var outputRClassesJar: File private set
    @get:OutputDirectory lateinit var outputRewrittenManifests: File private set

    lateinit var intermediateDirectory: File private set

    @TaskAction
    fun taskAction() = autoNamespaceDependencies()

    private fun autoNamespaceDependencies(
        forkJoinPool: ForkJoinPool = sharedForkJoinPool,
        aapt2FromMaven: FileCollection = this.aapt2FromMaven,
        dependencies: ResolvableDependencies = this.dependencies,
        rFiles: ArtifactCollection = this.rFiles,
        jarFiles: ArtifactCollection = this.jarFiles,
        manifests: ArtifactCollection = this.nonNamespacedManifests,
        notNamespacedResources: ArtifactCollection = this.externalNotNamespacedResources,
        staticLibraryDependencies: ArtifactCollection = this.externalResStaticLibraries,
        intermediateDirectory: File = this.intermediateDirectory,
        outputStaticLibraries: File = this.outputStaticLibraries,
        outputClassesJar: File = this.outputClassesJar,
        outputRClassesJar: File = this.outputRClassesJar,
        outputManifests: File = this.outputRewrittenManifests
    ) {

        try {
            val graph = DependenciesGraph.create(
                dependencies,
                ImmutableMap.of(
                    ArtifactType.DEFINED_ONLY_SYMBOL_LIST, rFiles.toMap(),
                    ArtifactType.NON_NAMESPACED_CLASSES, jarFiles.toMap(),
                    ArtifactType.NON_NAMESPACED_MANIFEST, manifests.toMap(),
                    ArtifactType.ANDROID_RES, notNamespacedResources.toMap(),
                    ArtifactType.RES_STATIC_LIBRARY, staticLibraryDependencies
                        .toMap()
                )
            )

            val rewrittenResources = File(intermediateDirectory, "namespaced_res")
            val rewrittenClasses = File(intermediateDirectory, "namespaced_classes")
            val rewrittenRClasses = File(intermediateDirectory, "namespaced_r_classes")

            val rewrittenResourcesMap = namespaceDependencies(
                graph = graph,
                forkJoinPool = forkJoinPool,
                outputRewrittenClasses = rewrittenClasses,
                outputRClasses = rewrittenRClasses,
                outputManifests = outputManifests,
                outputResourcesDir = rewrittenResources
            )

            // Jar all the classes into two JAR files - one for namespaced classes, one for R classes.
            jarOutputs(outputClassesJar, rewrittenClasses)
            jarOutputs(outputRClassesJar, rewrittenRClasses)

            val aapt2ServiceKey = registerAaptService(aapt2FromMaven, logger = iLogger)

            val outputCompiledResources = File(intermediateDirectory, "compiled_namespaced_res")
            // compile the rewritten resources
            val compileMap =
                compile(
                    rewrittenResourcesMap = rewrittenResourcesMap,
                    aapt2ServiceKey = aapt2ServiceKey,
                    forkJoinPool = forkJoinPool,
                    outputDirectory = outputCompiledResources
                )

            // then link them in to static libraries.
            val nonNamespacedDependenciesLinker = NonNamespacedDependenciesLinker(
                graph = graph,
                compiled = compileMap,
                outputStaticLibrariesDirectory = outputStaticLibraries,
                intermediateDirectory = intermediateDirectory,
                pool = forkJoinPool,
                aapt2ServiceKey = aapt2ServiceKey,
                androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR)
            )
            nonNamespacedDependenciesLinker.link()
        } finally {
            symbolTablesCache.invalidateAll()
        }
    }

    @VisibleForTesting
    internal fun namespaceDependencies(
        graph: DependenciesGraph,
        forkJoinPool: ForkJoinPool,
        outputRewrittenClasses: File,
        outputRClasses: File,
        outputManifests: File,
        outputResourcesDir: File
    ): Map<DependenciesGraph.Node, File> {
        FileUtils.cleanOutputDir(outputRewrittenClasses)
        FileUtils.cleanOutputDir(outputRClasses)
        FileUtils.cleanOutputDir(outputManifests)
        FileUtils.cleanOutputDir(outputResourcesDir)


        // The rewriting works per node, since for rewriting a library the only files from its
        // dependencies we need are their R-def.txt files, which were already generated by the
        // [LibraryDefinedSymbolTableTransform].
        // TODO: do this all as one action to interleave work.
        val rewrittenResources = ImmutableMap.builder<DependenciesGraph.Node, File>()

        val tasks = mutableListOf<ForkJoinTask<*>>()
        for (dependency in graph.allNodes) {
            val outputResources = if (dependency.getFile(ArtifactType.ANDROID_RES) != null) {
                File(
                    outputResourcesDir,
                    dependency.sanitizedName
                )
            } else {
                null
            }
            outputResources?.apply { rewrittenResources.put(dependency, outputResources) }
            tasks.add(forkJoinPool.submit {
                namespaceDependency(
                    dependency,
                    outputRewrittenClasses, outputRClasses, outputManifests, outputResources
                )
            })
        }
        for (task in tasks) {
            task.get()
        }
        tasks.clear()
        return rewrittenResources.build()
    }

    private fun jarOutputs(outputJar: File, inputDirectory: File) {
        ZFile(outputJar).use { jar ->
            Files.walk(inputDirectory.toPath()).use { paths ->
                paths.filter { p -> p.toFile().isFile}.forEach { it ->
                    ZFile(it.toFile()).use { classesJar ->
                        classesJar.entries().forEach { entry ->
                            val name = entry.centralDirectoryHeader.name
                            if (entry.type == StoredEntryType.FILE && name.endsWith(".class")) {
                                jar.add(name, entry.open())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun namespaceDependency(
        dependency: DependenciesGraph.Node,
        outputClassesDirectory: File,
        outputRClassesDirectory: File,
        outputManifests: File,
        outputResourcesDirectory: File?
    ) {
        val inputClasses = dependency.getFiles(ArtifactType.NON_NAMESPACED_CLASSES)
        val manifest = dependency.getFile(ArtifactType.NON_NAMESPACED_MANIFEST)
        val resources = dependency.getFile(ArtifactType.ANDROID_RES)

        // Only convert external nodes and non-namespaced libraries. Already namespaced libraries
        // and JAR files can be present in the graph, but they will not contain the
        // NON_NAMESPACED_CLASSES artifacts. Only try to rewrite non-namespaced libraries' classes.
        if (dependency.id !is ProjectComponentIdentifier && inputClasses != null) {
            Preconditions.checkNotNull(
                    manifest,
                    "Manifest missing for library $dependency")

            // The rewriting algorithm uses ordered symbol tables, with this library's table at the
            // top of the list. It looks up resources starting from the top of the list, trying to
            // find where the references resource was defined (or overridden), closest to the root
            // (this node) in the dependency graph.
            val symbolTables = getSymbolTables(dependency)
            logger.info("Started rewriting $dependency")
            val rewriter = NamespaceRewriter(symbolTables, log ?: logger)

            // Brittle, relies on the AAR expansion logic that makes sure all jars have unique names
            try {
            inputClasses.forEach {
                val out = File(
                    outputClassesDirectory,
                    "namespaced-${dependency.sanitizedName}-${it.name}"
                )
                rewriter.rewriteJar(it, out)
            }
            } catch (e: Exception) {
                throw IOException("Failed to transform jar + ${dependency.getTransitiveFiles(ArtifactType.DEFINED_ONLY_SYMBOL_LIST)}", e)
            }
            rewriter.rewriteManifest(
                    manifest!!.toPath(),
                    outputManifests.toPath().resolve("${dependency.sanitizedName}_AndroidManifest.xml"))
            if (resources != null) {
                rewriter.rewriteAarResources(
                    resources.toPath(),
                    outputResourcesDirectory!!.toPath()
                )
                generatePublicFile(getDefinedSymbols(dependency), outputResourcesDirectory.toPath())
            }

            logger.info("Finished rewriting $dependency")

            // Also generate fake R classes for compilation.
            exportToCompiledJava(
                    ImmutableList.of(symbolTables[0]),
                    File(
                            outputRClassesDirectory,
                            "namespaced-${dependency.sanitizedName}-R.jar"
                    ).toPath()
            )
        }
    }

    private fun compile(
        rewrittenResourcesMap: Map<DependenciesGraph.Node, File>,
        aapt2ServiceKey: Aapt2ServiceKey,
        forkJoinPool: ForkJoinPool,
        outputDirectory: File
    ): Map<DependenciesGraph.Node, File> {
        val compiled = ImmutableMap.builder<DependenciesGraph.Node, File>()
        val tasks = mutableListOf<ForkJoinTask<*>>()

        rewrittenResourcesMap.forEach { node, rewrittenResources ->
            val nodeOutputDirectory = File(outputDirectory, node.sanitizedName)
            compiled.put(node, nodeOutputDirectory)
            Files.createDirectories(nodeOutputDirectory.toPath())
            for (resConfigurationDir in rewrittenResources.listFiles()) {
                for (resourceFile in resConfigurationDir.listFiles()) {
                    val request = CompileResourceRequest(
                        inputFile = resourceFile,
                        outputDirectory = nodeOutputDirectory
                    )
                    val params =
                        Aapt2CompileRunnable.Params(aapt2ServiceKey, listOf(request))
                    tasks.add(forkJoinPool.submit(Aapt2CompileRunnable(params)))
                }
            }
        }
        for (task in tasks) {
            task.get()
        }
        return compiled.build()
    }



    private fun getSymbolTables(node: DependenciesGraph.Node): ImmutableList<SymbolTable> {
        val builder = ImmutableList.builder<SymbolTable>()
        for (rDefFile in node.getTransitiveFiles(ArtifactType.DEFINED_ONLY_SYMBOL_LIST)) {
            builder.add(symbolTablesCache.getUnchecked(rDefFile))
        }
        return builder.build()
    }

    private fun getDefinedSymbols(node: DependenciesGraph.Node): SymbolTable {
        val rDefFile = node.getFile(ArtifactType.DEFINED_ONLY_SYMBOL_LIST)!!
        return symbolTablesCache.getUnchecked(rDefFile)
    }

    private fun ArtifactCollection.toMap(): ImmutableMap<String, ImmutableCollection<File>> =
        HashMap<String, MutableCollection<File>>().apply {
            for (artifact in artifacts) {
                val key = artifact.id.componentIdentifier.displayName
                getOrPut(key) { mutableListOf() }.add(artifact.file)
            }
        }.toImmutableMap { it.toImmutableList() }

    class ConfigAction(private val variantScope: VariantScope) :
        TaskConfigAction<AutoNamespaceDependenciesTask>() {

        override val name: String
            get() = variantScope.getTaskName("autoNamespace", "Dependencies")
        override val type: Class<AutoNamespaceDependenciesTask>
            get() = AutoNamespaceDependenciesTask::class.java

        override fun execute(task: AutoNamespaceDependenciesTask) {
            task.variantName = variantScope.fullVariantName
            task.setAndroidBuilder(variantScope.globalScope.androidBuilder)

            task.rFiles = variantScope.getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.EXTERNAL,
                    ArtifactType.DEFINED_ONLY_SYMBOL_LIST
            )

            task.jarFiles = variantScope.getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.EXTERNAL,
                    ArtifactType.NON_NAMESPACED_CLASSES
            )

            task.nonNamespacedManifests = variantScope.getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.EXTERNAL,
                    ArtifactType.NON_NAMESPACED_MANIFEST
            )

            task.outputRewrittenManifests = variantScope.artifacts.appendArtifact(
                    InternalArtifactType.NAMESPACED_MANIFESTS, task)

            task.outputClassesJar = variantScope.artifacts.appendArtifact(
                    InternalArtifactType.NAMESPACED_CLASSES_JAR, task, "namespaced-classes.jar")

            task.outputRClassesJar = variantScope.artifacts.appendArtifact(
                    InternalArtifactType.COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JAR,
                    task,
                    "namespaced-R.jar")

            task.outputStaticLibraries = variantScope.artifacts.appendArtifact(
                InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES,
                task
            )

            task.dependencies =
                    variantScope.variantData.variantDependency.runtimeClasspath.incoming

            task.externalNotNamespacedResources = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.ANDROID_RES
            )

            task.externalResStaticLibraries = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.RES_STATIC_LIBRARY
            )

            task.intermediateDirectory = variantScope.getIncrementalDir(name)

            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
            task.setAndroidBuilder(variantScope.globalScope.androidBuilder)
        }
    }

    companion object {
        val sharedForkJoinPool: ForkJoinPool by lazy {
            ForkJoinPool(
                Math.max(
                    1,
                    Math.min(8, Runtime.getRuntime().availableProcessors() / 2)
                )
            )
        }
    }
}
