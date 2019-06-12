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

package com.android.build.gradle.internal.transforms

import com.android.SdkConstants
import com.android.build.api.transform.Context
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.crash.PluginCrashReporter
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.OriginalStream
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry.Companion.INSTANCE
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.DefaultDexOptions
import com.android.builder.core.DexOptions
import com.android.builder.dexing.ClassFileEntry
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexArchiveBuilderConfig
import com.android.builder.dexing.DexArchiveBuilderException
import com.android.builder.dexing.DexerTool
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.builder.utils.FileCache
import com.android.dx.command.dexer.DxContext
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.MessageReceiver
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.DexParser
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessOutput
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.tooling.BuildException
import org.gradle.workers.IsolationMode
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import javax.inject.Inject

/**
 * Transform that converts CLASS files to dex archives, [com.android.builder.dexing.DexArchive].
 * This will consume [TransformManager.CONTENT_CLASS], and for each of the inputs, corresponding dex
 * archive will be produced.
 *
 *
 * This transform is incremental, only changed streams will be converted again. Additionally, if
 * an input stream is able to provide a list of individual files that were changed, only those files
 * will be processed. Their corresponding dex archives will be updated.
 */
class DexArchiveBuilderTransform internal constructor(
    private val androidJarClasspath: FileCollection,
    private val dexOptions: DexOptions,
    private val messageReceiver: MessageReceiver,
    private val errorFormatMode: SyncOptions.ErrorFormatMode,
    userLevelCache: FileCache?,
    private val minSdkVersion: Int,
    private val dexer: DexerTool,
    private val useGradleWorkers: Boolean,
    inBufferSize: Int?,
    outBufferSize: Int?,
    private val isDebuggable: Boolean,
    private val java8LangSupportType: VariantScope.Java8LangSupport,
    private val projectVariant: String,
    numberOfBuckets: Int?,
    private val includeFeaturesInScopes: Boolean,
    private val enableDexingArtifactTransform: Boolean
) : Transform() {
    private val executor: WaitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()
    private val cacheHandler: DexArchiveBuilderCacheHandler = DexArchiveBuilderCacheHandler(
        userLevelCache, dexOptions, minSdkVersion, isDebuggable, dexer
    )
    private val inBufferSize: Int = (inBufferSize ?: DEFAULT_BUFFER_SIZE_IN_KB) * 1024
    private val outBufferSize: Int = (outBufferSize ?: DEFAULT_BUFFER_SIZE_IN_KB) * 1024
    private val numberOfBuckets: Int = numberOfBuckets ?: DEFAULT_NUM_BUCKETS
    private val needsClasspath =
        java8LangSupportType == VariantScope.Java8LangSupport.D8
                && minSdkVersion < AndroidVersion.VersionCodes.N

    /**
     * Classpath resources provider is shared between invocations, and this key uniquely identifies
     * it.
     */
    data class ClasspathServiceKey(private val id: Long) :
        WorkerActionServiceRegistry.ServiceKey<ClassFileProviderFactory> {

        override val type = ClassFileProviderFactory::class.java
    }

    /** Wrapper around the [com.android.builder.dexing.r8.ClassFileProviderFactory].  */
    class ClasspathService(override val service: ClassFileProviderFactory) :
        WorkerActionServiceRegistry.RegisteredService<ClassFileProviderFactory> {

        override fun shutdown() {
            // nothing to be done, as providerFactory is a closable
        }
    }

    override fun getName(): String {
        return "dexBuilder"
    }

    override fun getInputTypes(): Set<ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getOutputTypes(): Set<ContentType> {
        return ImmutableSet.of<ContentType>(ExtendedContentType.DEX_ARCHIVE)
    }

    override fun getScopes(): ImmutableSet<in Scope> {
        return when {
            enableDexingArtifactTransform -> ImmutableSet.of(Scope.PROJECT)
            includeFeaturesInScopes -> ImmutableSet.copyOf(TransformManager.SCOPE_FULL_WITH_FEATURES)
            else -> ImmutableSet.copyOf(TransformManager.SCOPE_FULL_PROJECT)
        }
    }

    override fun getReferencedScopes(): ImmutableSet<in Scope> {
        if (!needsClasspath) {
            return ImmutableSet.of()
        }

        return ImmutableSet.Builder<QualifiedContent.ScopeType>().also {
            it.add(Scope.TESTED_CODE, Scope.PROVIDED_ONLY)
            if (enableDexingArtifactTransform) {
                it.add(Scope.SUB_PROJECTS)
                it.add(Scope.EXTERNAL_LIBRARIES)
                if (includeFeaturesInScopes) {
                    it.add(InternalScope.FEATURES)
                }
            }
        }.build()
    }

    override fun getParameterInputs(): Map<String, Any> = mapOf(
        "optimize" to !dexOptions.additionalParameters.contains("--no-optimize"),
        "jumbo" to dexOptions.jumboMode,
        "min-sdk-version" to minSdkVersion,
        "dex-builder-tool" to dexer.name,
        "enable-dexing-artifact-transform" to enableDexingArtifactTransform
    )

    override fun isIncremental() = true

    override fun transform(transformInvocation: TransformInvocation) {
        val outputProvider =
            checkNotNull(transformInvocation.outputProvider) { "Missing output provider." }
        if ("--no-optimize" in dexOptions.additionalParameters) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING)
        }

        logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental)
        if (!transformInvocation.isIncremental) {
            outputProvider.deleteAll()
        }

        val transformInputs = TransformInputUtil.getInputAndReferenced(transformInvocation)
        val allInputs = TransformInputUtil.getAllFiles(transformInputs)
        val desugarIncrementalTransformHelper: DesugarIncrementalTransformHelper? =
            DesugarIncrementalTransformHelper(
                projectVariant,
                transformInvocation.isIncremental,
                allInputs,
                { TransformInputUtil.findChangedPaths(transformInputs) },
                executor
            ).takeIf { java8LangSupportType == VariantScope.Java8LangSupport.D8 }

        val additionalPaths: Set<File> =
            desugarIncrementalTransformHelper?.additionalPaths?.map { it.toFile() }?.toSet()
                ?: emptySet()

        val cacheableItems = mutableListOf<DexArchiveBuilderCacheHandler.CacheableItem>()

        val classpath = getClasspath(transformInvocation).map { Paths.get(it) }
        val bootclasspath = getBootClasspath(androidJarClasspath).map { Paths.get(it) }

        var bootclasspathServiceKey: ClasspathServiceKey? = null
        var classpathServiceKey: ClasspathServiceKey? = null
        try {
            ClassFileProviderFactory(bootclasspath).use { bootClasspathProvider ->
                ClassFileProviderFactory(classpath).use { libraryClasspathProvider ->
                    bootclasspathServiceKey = ClasspathServiceKey(bootClasspathProvider.id)
                    classpathServiceKey = ClasspathServiceKey(libraryClasspathProvider.id)
                    INSTANCE.registerService(
                        bootclasspathServiceKey!!
                    ) { ClasspathService(bootClasspathProvider) }
                    INSTANCE.registerService(
                        classpathServiceKey!!
                    ) { ClasspathService(libraryClasspathProvider) }

                    for (input in transformInvocation.inputs) {

                        for (dirInput in input.directoryInputs) {
                            logger.verbose("Dir input %s", dirInput.file.toString())
                            convertToDexArchive(
                                transformInvocation.context,
                                dirInput,
                                outputProvider,
                                transformInvocation.isIncremental,
                                bootclasspathServiceKey!!,
                                classpathServiceKey!!,
                                additionalPaths
                            )
                        }

                        for (jarInput in input.jarInputs) {
                            logger.verbose("Jar input %s", jarInput.file.toString())

                            val cacheInfo = getD8DesugaringCacheInfo(
                                desugarIncrementalTransformHelper,
                                bootclasspath,
                                classpath,
                                jarInput
                            )

                            val dexArchives = processJarInput(
                                transformInvocation.context,
                                transformInvocation.isIncremental,
                                jarInput,
                                outputProvider,
                                bootclasspathServiceKey!!,
                                classpathServiceKey!!,
                                additionalPaths,
                                cacheInfo
                            )
                            if (cacheInfo != DesugaringDontCache
                                && dexArchives.isNotEmpty()
                                && isExternalLib(jarInput)) {
                                cacheableItems.add(
                                    DexArchiveBuilderCacheHandler.CacheableItem(
                                        jarInput.file,
                                        dexArchives,
                                        cacheInfo.orderedD8DesugaringDependencies
                                    )
                                )
                            }
                        }
                    }

                    // all work items have been submitted, now wait for completion.
                    if (useGradleWorkers) {
                        transformInvocation.context.workerExecutor.await()
                    } else {
                        executor.waitForTasksWithQuickFail<Any>(true)
                    }

                    // if we are in incremental mode, delete all removed files.
                    if (transformInvocation.isIncremental) {
                        for (transformInput in transformInvocation.inputs) {
                            removeDeletedEntries(outputProvider, transformInput)
                        }
                    }

                    // and finally populate the caches.
                    if (cacheableItems.isNotEmpty()) {
                        cacheHandler.populateCache(cacheableItems)
                    }

                    logger.verbose("Done with all dex archive conversions")
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TransformException(e)
        } catch (e: Exception) {
            PluginCrashReporter.maybeReportException(e)
            logger.error(null, Throwables.getStackTraceAsString(e))
            throw TransformException(e)
        } finally {
            classpathServiceKey?.let { INSTANCE.removeService(it) }
            bootclasspathServiceKey?.let { INSTANCE.removeService(it) }
        }
    }

    private fun getD8DesugaringCacheInfo(
        desugarIncrementalTransformHelper: DesugarIncrementalTransformHelper?,
        bootclasspath: List<Path>,
        classpath: List<Path>,
        jarInput: JarInput
    ): D8DesugaringCacheInfo {

        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return DesugaringNoInfoCache
        }
        desugarIncrementalTransformHelper as DesugarIncrementalTransformHelper

        val unorderedD8DesugaringDependencies =
            desugarIncrementalTransformHelper.getDependenciesPaths(jarInput.file.toPath())

        // Don't cache libraries depending on class files in folders:
        // Folders content is expected to change often so probably not worth paying the cache cost
        // if we frequently need to rebuild anyway.
        // Supporting dependency to class files would also require special care to respect order.
        if (unorderedD8DesugaringDependencies
                .any { path -> !path.toString().endsWith(SdkConstants.DOT_JAR) }
        ) {
            return DesugaringDontCache
        }

        // DesugaringGraph is not calculating the bootclasspath dependencies so just keep the full
        // bootclasspath for now.
        val bootclasspathPaths = bootclasspath.distinct()

        val classpathJars =
            classpath.distinct().filter { unorderedD8DesugaringDependencies.contains(it) }

        val allDependencies = ArrayList<Path>(bootclasspathPaths.size + classpathJars.size)

        allDependencies.addAll(bootclasspathPaths)
        allDependencies.addAll(classpathJars)
        return D8DesugaringCacheInfo(allDependencies)
    }

    /** Returns if the qualified content is an external jar.  */
    private fun isExternalLib(content: QualifiedContent): Boolean {
        return (content.file.isFile
                && content.scopes == setOf(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                && content.contentTypes == setOf(QualifiedContent.DefaultContentType.CLASSES)
                && !content.name.startsWith(OriginalStream.LOCAL_JAR_GROUPID))
    }

    private fun removeDeletedEntries(
        outputProvider: TransformOutputProvider, transformInput: TransformInput
    ) {
        for (input in transformInput.directoryInputs) {
            for ((file, value) in input.changedFiles) {
                if (value != Status.REMOVED) {
                    continue
                }

                val relativePath = input.file.toPath().relativize(file.toPath())

                val fileToDelete = if (file.name.endsWith(SdkConstants.DOT_CLASS)) {
                    ClassFileEntry.withDexExtension(relativePath.toString())
                } else {
                    relativePath.toString()
                }

                val outputFile = getOutputForDir(outputProvider, input)
                FileUtils.deleteRecursivelyIfExists(
                    outputFile.toPath().resolve(fileToDelete).toFile()
                )
            }
        }
    }

    private fun processJarInput(
        context: Context,
        isIncremental: Boolean,
        jarInput: JarInput,
        transformOutputProvider: TransformOutputProvider,
        bootclasspath: ClasspathServiceKey,
        classpath: ClasspathServiceKey,
        additionalPaths: Set<File>,
        cacheInfo: D8DesugaringCacheInfo
    ): List<File> {
        if (!isIncremental) {
            Preconditions.checkState(
                jarInput.file.exists(),
                "File %s does not exist, yet it is reported as input. Try \n" + "cleaning the build directory.",
                jarInput.file.toString()
            )
            return convertJarToDexArchive(
                context,
                jarInput,
                transformOutputProvider,
                bootclasspath,
                classpath,
                cacheInfo
            )
        } else if (jarInput.status != Status.NOTCHANGED || additionalPaths.contains(jarInput.file)) {
            // delete all preDex jars if they exists.
            for (bucketId in 0 until numberOfBuckets) {
                val shardedOutput = getOutputForJar(transformOutputProvider, jarInput, bucketId)
                FileUtils.deleteIfExists(shardedOutput)
                if (jarInput.status != Status.REMOVED) {
                    FileUtils.mkdirs(shardedOutput.parentFile)
                }
            }
            val nonShardedOutput = getOutputForJar(transformOutputProvider, jarInput, null)
            FileUtils.deleteIfExists(nonShardedOutput)
            if (jarInput.status != Status.REMOVED) {
                FileUtils.mkdirs(nonShardedOutput.parentFile)
            }

            // and perform dexing if necessary.
            if (jarInput.status == Status.ADDED
                || jarInput.status == Status.CHANGED
                || additionalPaths.contains(jarInput.file)
            ) {
                return convertJarToDexArchive(
                    context,
                    jarInput,
                    transformOutputProvider,
                    bootclasspath,
                    classpath,
                    cacheInfo
                )
            }
        }
        return listOf()
    }

    private fun convertJarToDexArchive(
        context: Context,
        toConvert: JarInput,
        transformOutputProvider: TransformOutputProvider,
        bootclasspath: ClasspathServiceKey,
        classpath: ClasspathServiceKey,
        cacheInfo: D8DesugaringCacheInfo
    ): List<File> {

        if (cacheInfo !== DesugaringDontCache) {
            val cachedVersion = cacheHandler.getCachedVersionIfPresent(
                toConvert.file, cacheInfo.orderedD8DesugaringDependencies
            )
            if (cachedVersion != null) {
                val outputFile = getOutputForJar(transformOutputProvider, toConvert, null)
                Files.copy(
                    cachedVersion.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                // no need to try to cache an already cached version.
                return listOf()
            }
        }
        return convertToDexArchive(
            context,
            toConvert,
            transformOutputProvider,
            false,
            bootclasspath,
            classpath,
            ImmutableSet.of()
        )
    }

    class DexConversionParameters(
        internal val input: QualifiedContent,
        internal val bootClasspath: ClasspathServiceKey,
        internal val classpath: ClasspathServiceKey,
        output: File,
        private val numberOfBuckets: Int,
        private val buckedId: Int,
        internal val minSdkVersion: Int,
        internal val dexAdditionalParameters: List<String>,
        internal val inBufferSize: Int,
        internal val outBufferSize: Int,
        internal val dexer: DexerTool,
        internal val isDebuggable: Boolean,
        internal val isIncremental: Boolean,
        internal val java8LangSupportType: VariantScope.Java8LangSupport,
        internal val additionalPaths: Set<File>,
        internal val errorFormatMode: SyncOptions.ErrorFormatMode
    ) : Serializable {
        internal val output: String = output.toURI().toString()

        val isDirectoryBased = input is DirectoryInput

        fun belongsToThisBucket(path: String): Boolean {
            return getBucketForFile(input, path, numberOfBuckets) == buckedId
        }
    }

    class DexConversionWorkAction @Inject
    constructor(private val dexConversionParameters: DexConversionParameters) : Runnable {

        override fun run() {
            try {
                launchProcessing(
                    dexConversionParameters,
                    System.out,
                    System.err,
                    MessageReceiverImpl(
                        dexConversionParameters.errorFormatMode,
                        Logging.getLogger(DexArchiveBuilderTransform::class.java)
                    )
                )
            } catch (e: Exception) {
                throw BuildException(e.message, e)
            }
        }
    }

    private open class D8DesugaringCacheInfo constructor(val orderedD8DesugaringDependencies: List<Path>)
    private object DesugaringNoInfoCache : D8DesugaringCacheInfo(emptyList())
    private object DesugaringDontCache : D8DesugaringCacheInfo(emptyList())

    private fun convertToDexArchive(
        context: Context,
        input: QualifiedContent,
        outputProvider: TransformOutputProvider,
        isIncremental: Boolean,
        bootClasspath: ClasspathServiceKey,
        classpath: ClasspathServiceKey,
        additionalPaths: Set<File>
    ): List<File> {
        logger.verbose("Dexing %s", input.file.absolutePath)

        val dexArchives = mutableListOf<File>()
        for (bucketId in 0 until numberOfBuckets) {

            val preDexOutputFile = if (input is DirectoryInput) {
                getOutputForDir(outputProvider, input).also { it.mkdirs() }
            } else {
                getOutputForJar(outputProvider, input as JarInput, bucketId)
            }

            dexArchives.add(preDexOutputFile)
            val parameters = DexConversionParameters(
                input,
                bootClasspath,
                classpath,
                preDexOutputFile,
                numberOfBuckets,
                bucketId,
                minSdkVersion,
                dexOptions.additionalParameters,
                inBufferSize,
                outBufferSize,
                dexer,
                isDebuggable,
                isIncremental,
                java8LangSupportType,
                additionalPaths,
                errorFormatMode
            )

            if (useGradleWorkers) {
                context.workerExecutor
                    .submit(
                        DexConversionWorkAction::class.java
                    ) { configuration ->
                        configuration.isolationMode = IsolationMode.NONE
                        configuration.setParams(parameters)
                    }
            } else {
                executor.execute<Any> {
                    val outputHandler = ParsingProcessOutputHandler(
                        ToolOutputParser(
                            DexParser(), Message.Kind.ERROR, logger
                        ),
                        ToolOutputParser(DexParser(), logger),
                        messageReceiver
                    )
                    var output: ProcessOutput? = null
                    try {
                        outputHandler.createOutput().use {
                            output = it
                            launchProcessing(
                                parameters,
                                output!!.standardOutput,
                                output!!.errorOutput,
                                messageReceiver
                            )
                        }
                    } finally {
                        output?.let {
                            try {
                                outputHandler.handleOutput(it)
                            } catch (e: ProcessException) {
                                // ignore this one
                            }
                        }
                    }
                    null
                }
            }
        }
        return dexArchives
    }

    private fun getOutputForDir(
        output: TransformOutputProvider, directoryInput: DirectoryInput
    ): File {
        return output.getContentLocation(
            directoryInput.file.toString(),
            setOf(ExtendedContentType.DEX_ARCHIVE),
            directoryInput.scopes,
            Format.DIRECTORY
        )
    }

    private fun getClasspath(transformInvocation: TransformInvocation): List<String> {
        if (!needsClasspath) {
            return emptyList()
        }
        val classpathEntries = mutableListOf<String>()

        val dependencies = Iterables.concat(
            transformInvocation.inputs, transformInvocation.referencedInputs
        )

        classpathEntries.addAll(TransformInputUtil.getDirectories(dependencies).distinct().map { it.path })

        classpathEntries.addAll(
            dependencies
                .flatMap { transformInput -> transformInput.jarInputs }
                .filter { jarInput -> jarInput.status != Status.REMOVED }
                .map { jarInput -> jarInput.file.path }
                .distinct())

        return classpathEntries
    }

    private fun getBootClasspath(androidJarClasspath: FileCollection): List<String> {
        return if (needsClasspath) {
            androidJarClasspath.files.map { it.path }
        } else {
            emptyList()
        }
    }

    private fun getOutputForJar(
        output: TransformOutputProvider,
        qualifiedContent: JarInput,
        bucketId: Int?
    ): File {
        return output.getContentLocation(
            qualifiedContent.file.toString() + ("-$bucketId".takeIf { bucketId != null } ?: ""),
            setOf(ExtendedContentType.DEX_ARCHIVE),
            qualifiedContent.scopes,
            Format.JAR
        )
    }
}

/**
 * Returns the bucket for the specified path. For jar inputs, path in the jar file should be
 * specified (both relative and absolute path work). For directories, absolute path should be
 * specified.
 */
private fun getBucketForFile(
    content: QualifiedContent, path: String, numberOfBuckets: Int
): Int {
    if (content !is DirectoryInput) {
        return Math.abs(path.hashCode()) % numberOfBuckets
    } else {
        val filePath = Paths.get(path)
        Preconditions.checkArgument(filePath.isAbsolute, "Path should be absolute: $path")
        val packagePath = filePath.parent ?: return 0
        return Math.abs(packagePath.toString().hashCode()) % numberOfBuckets
    }
}

@Throws(IOException::class, URISyntaxException::class)
private fun launchProcessing(
    dexConversionParameters: DexArchiveBuilderTransform.DexConversionParameters,
    outStream: OutputStream,
    errStream: OutputStream,
    receiver: MessageReceiver
) {
    val dexArchiveBuilder = getDexArchiveBuilder(
        dexConversionParameters.minSdkVersion,
        dexConversionParameters.dexAdditionalParameters,
        dexConversionParameters.inBufferSize,
        dexConversionParameters.outBufferSize,
        dexConversionParameters.bootClasspath,
        dexConversionParameters.classpath,
        dexConversionParameters.dexer,
        dexConversionParameters.isDebuggable,
        VariantScope.Java8LangSupport.D8 == dexConversionParameters.java8LangSupportType,
        outStream,
        errStream,
        receiver
    )

    val inputPath = dexConversionParameters.input.file.toPath()

    val hasIncrementalInfo =
        dexConversionParameters.isDirectoryBased && dexConversionParameters.isIncremental

    fun toProcess(path: String): Boolean {
        if (!dexConversionParameters.belongsToThisBucket(path)) return false

        if (!hasIncrementalInfo) {
            return true
        }

        val resolved = inputPath.resolve(path).toFile()
        if (dexConversionParameters.additionalPaths.contains(resolved)) {
            return true
        }
        val changedFiles = (dexConversionParameters.input as DirectoryInput)
            .changedFiles

        val status = changedFiles[resolved]
        return status == Status.ADDED || status == Status.CHANGED
    }

    val bucketFilter = { name: String -> toProcess(name) }
    logger.verbose("Dexing '" + inputPath + "' to '" + dexConversionParameters.output + "'")

    try {
        ClassFileInputs.fromPath(inputPath).use { input ->
            input.entries(bucketFilter).use { entries ->
                dexArchiveBuilder.convert(
                    entries,
                    Paths.get(URI(dexConversionParameters.output)),
                    dexConversionParameters.isDirectoryBased
                )
            }
        }
    } catch (ex: DexArchiveBuilderException) {
        throw DexArchiveBuilderException("Failed to process $inputPath", ex)
    }
}

private fun getDexArchiveBuilder(
    minSdkVersion: Int,
    dexAdditionalParameters: List<String>,
    inBufferSize: Int,
    outBufferSize: Int,
    bootClasspath: DexArchiveBuilderTransform.ClasspathServiceKey,
    classpath: DexArchiveBuilderTransform.ClasspathServiceKey,
    dexer: DexerTool,
    isDebuggable: Boolean,
    d8DesugaringEnabled: Boolean,
    outStream: OutputStream,
    errStream: OutputStream,
    messageReceiver: MessageReceiver
): DexArchiveBuilder {

    val dexArchiveBuilder: DexArchiveBuilder
    when (dexer) {
        DexerTool.DX -> {
            val optimizedDex = !dexAdditionalParameters.contains("--no-optimize")
            val dxContext = DxContext(outStream, errStream)
            val config = DexArchiveBuilderConfig(
                dxContext,
                optimizedDex,
                inBufferSize,
                minSdkVersion,
                DexerTool.DX,
                outBufferSize,
                DexArchiveBuilderCacheHandler.isJumboModeEnabledForDx()
            )

            dexArchiveBuilder = DexArchiveBuilder.createDxDexBuilder(config)
        }
        DexerTool.D8 -> dexArchiveBuilder = DexArchiveBuilder.createD8DexBuilder(
            minSdkVersion,
            isDebuggable,
            INSTANCE.getService(bootClasspath).service,
            INSTANCE.getService(classpath).service,
            d8DesugaringEnabled,
            messageReceiver
        )
        else -> throw AssertionError("Unknown dexer type: " + dexer.name)
    }
    return dexArchiveBuilder
}

private const val DEFAULT_BUFFER_SIZE_IN_KB = 100
private val DEFAULT_NUM_BUCKETS = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1)
private val logger = LoggerWrapper.getLogger(DexArchiveBuilderTransform::class.java)
