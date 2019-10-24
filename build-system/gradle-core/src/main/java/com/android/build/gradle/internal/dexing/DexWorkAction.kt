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

package com.android.build.gradle.internal.dexing

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTaskDelegate
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry.Companion.INSTANCE
import com.android.builder.dexing.ClassFileEntry
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexArchiveBuilderConfig
import com.android.builder.dexing.DexArchiveBuilderException
import com.android.builder.dexing.DexerTool
import com.android.dx.command.dexer.DxContext
import com.android.ide.common.blame.MessageReceiver
import com.google.common.base.Preconditions
import com.google.common.io.Closer
import org.gradle.api.logging.Logging
import org.gradle.tooling.BuildException
import java.io.File
import java.io.OutputStream
import java.io.Serializable
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import javax.inject.Inject
import kotlin.math.abs

/** Work action to process a bucket of class files. */
class DexWorkAction @Inject constructor(private val params: DexWorkActionParams) : Runnable {

    override fun run() {
        try {
            launchProcessing(
                params,
                System.out,
                System.err,
                MessageReceiverImpl(
                    params.dexSpec.dexParams.errorFormatMode,
                    Logging.getLogger(DexArchiveBuilderTaskDelegate::class.java)
                )
            )
        } catch (e: Exception) {
            throw BuildException(e.message, e)
        }
    }
}

/** Parameters for running [DexWorkAction]. */
class DexWorkActionParams(
    val dexer: DexerTool,
    val dexSpec: IncrementalDexSpec,
    val dxDexParams: DxDexParameters
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

fun launchProcessing(
    dexWorkActionParams: DexWorkActionParams,
    outStream: OutputStream,
    errStream: OutputStream,
    receiver: MessageReceiver
) {
    val dexArchiveBuilder = getDexArchiveBuilder(
        dexWorkActionParams,
        outStream,
        errStream,
        receiver
    )

    val inputPaths = dexWorkActionParams.dexSpec.classFileRoots.map { it.toPath() }

    val hasIncrementalInfo =
        dexWorkActionParams.dexSpec.isDirectory && dexWorkActionParams.dexSpec.isIncremental

    fun toProcess(rootPath: Path, path: String): Boolean {
        val inputPath = Paths.get(path)
        // The inputPath could be relative path for jar input and absolute path for directory input
        val relativePath = if (inputPath.isAbsolute) rootPath.relativize(inputPath) else inputPath
        if (!belongsToThisBucket(dexWorkActionParams, relativePath.toString())) return false

        if (!hasIncrementalInfo) {
            return true
        }

        val resolved = rootPath.resolve(path).toFile()
        return resolved in dexWorkActionParams.dexSpec.impactedFiles
                || resolved in dexWorkActionParams.dexSpec.changedFiles
    }

    val bucketFilter = { rootPath: Path, path: String -> toProcess(rootPath, path) }
    inputPaths.forEach {
        loggerWrapper.verbose("Dexing '$it' to '${dexWorkActionParams.dexSpec.outputPath.path}'")
    }

    try {
        Closer.create().use { closer ->
            var classFileEntries = Stream.empty<ClassFileEntry>()
            classFileEntries.use {
                for (inputPath in inputPaths) {
                    val classFileInput =
                        ClassFileInputs.fromPath(inputPath).also { closer.register(it) }
                    classFileEntries =
                        Stream.concat(classFileEntries, classFileInput.entries(bucketFilter))
                }
                dexArchiveBuilder.convert(
                    classFileEntries,
                    dexWorkActionParams.dexSpec.outputPath.toPath()
                )
            }
        }
    } catch (ex: DexArchiveBuilderException) {
        if (dexWorkActionParams.dexSpec.isDirectory) {
            throw DexArchiveBuilderException("Failed to process for directories input", ex)
        } else {
            throw DexArchiveBuilderException("Failed to process ${inputPaths.first()}", ex)
        }
    }
}

private fun getDexArchiveBuilder(
    dexWorkActionParams: DexWorkActionParams,
    outStream: OutputStream,
    errStream: OutputStream,
    messageReceiver: MessageReceiver
): DexArchiveBuilder {
    val dexArchiveBuilder: DexArchiveBuilder
    with(dexWorkActionParams) {
        when (dexer) {
            DexerTool.DX -> {
                val config = DexArchiveBuilderConfig(
                    DxContext(outStream, errStream),
                    !dxDexParams.dxNoOptimizeFlagPresent, // optimizedDex
                    dxDexParams.inBufferSize,
                    dexSpec.dexParams.minSdkVersion,
                    DexerTool.DX,
                    dxDexParams.outBufferSize,
                    dxDexParams.jumboMode
                )

                dexArchiveBuilder = DexArchiveBuilder.createDxDexBuilder(config)
            }
            DexerTool.D8 -> dexArchiveBuilder = DexArchiveBuilder.createD8DexBuilder(
                com.android.builder.dexing.DexParameters(
                    minSdkVersion = dexSpec.dexParams.minSdkVersion,
                    debuggable = dexSpec.dexParams.debuggable,
                    dexPerClass = dexSpec.dexParams.dexPerClass,
                    withDesugaring = dexSpec.dexParams.withDesugaring,
                    desugarBootclasspath =
                            INSTANCE.getService(dexSpec.dexParams.desugarBootclasspath).service,
                    desugarClasspath =
                            INSTANCE.getService(dexSpec.dexParams.desugarClasspath).service,
                    coreLibDesugarConfig = dexSpec.dexParams.coreLibDesugarConfig,
                    coreLibDesugarOutputKeepRuleFile =
                    dexSpec.dexParams.coreLibDesugarOutputKeepRuleFile,
                    messageReceiver = messageReceiver
                )
            )
            else -> throw AssertionError("Unknown dexer type: " + dexer.name)
        }
    }
    return dexArchiveBuilder
}

private fun belongsToThisBucket(dexWorkActionParams: DexWorkActionParams, path: String): Boolean {
    return getBucketForFile(
        dexWorkActionParams.dexSpec.isDirectory,
        path,
        dexWorkActionParams.dexSpec.numberOfBuckets
    ) == dexWorkActionParams.dexSpec.buckedId
}

/**
 * Returns the bucket based on relative path for jar and directory input.
 */
private fun getBucketForFile(isDirectory: Boolean, path: String, numberOfBuckets: Int): Int {
    Preconditions.checkArgument(!Paths.get(path).isAbsolute, "Path should be relative: $path")
    if (!isDirectory) {
        return abs(path.hashCode()) % numberOfBuckets
    } else {
        val packagePath = Paths.get(path).parent ?: return 0
        return abs(packagePath.toString().hashCode()) % numberOfBuckets
    }
}

private val loggerWrapper = LoggerWrapper.getLogger(DexWorkAction::class.java)