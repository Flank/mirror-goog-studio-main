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
import com.android.builder.dexing.ClassBucket
import com.android.builder.dexing.DependencyGraphUpdater
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexArchiveBuilderConfig
import com.android.builder.dexing.DexArchiveBuilderException
import com.android.builder.dexing.DexerTool
import com.android.builder.dexing.MutableDependencyGraph
import com.android.dx.command.dexer.DxContext
import com.android.ide.common.blame.MessageReceiver
import com.android.utils.FileUtils
import com.google.common.io.Closer
import org.gradle.api.logging.Logging
import org.gradle.tooling.BuildException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import javax.inject.Inject

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
    with(dexWorkActionParams.dexSpec) {
        var canBeIncremental = isIncremental

        // Desugaring graph is not null iff dexSpec.impactedFiles == null
        val desugarGraph: MutableDependencyGraph<File>? =
            if (impactedFiles == null) {
                // Read the desugaring graph from disk or create a new, empty graph if this is a
                // non-incremental build (or if the graph cannot be read for some reason).
                if (canBeIncremental) {
                    try {
                        ObjectInputStream(FileInputStream(desugarGraphFile!!).buffered()).use {
                            @Suppress("UNCHECKED_CAST")
                            it.readObject() as MutableDependencyGraph<File>
                        }
                    } catch (e: Exception) {
                        loggerWrapper.warning(
                            "Failed to read dependency graph: ${e.message}." +
                                    " Fall back to non-incremental mode."
                        )
                        canBeIncremental = false
                        MutableDependencyGraph<File>()
                    }
                } else {
                    MutableDependencyGraph()
                }
            } else {
                null
            }

        // Compute impacted files based on the desugaring graph and the changed (removed, modified,
        // added) files, if they are not precomputed.
        val changedAndImpactedFiles = if (canBeIncremental) {
            val unchangedButImpactedFiles =
                impactedFiles ?: desugarGraph!!.getAllDependents(changedFiles)
            changedFiles + unchangedButImpactedFiles
        } else {
            // In non-incremental mode, this set must be null as we won't use it.
            null
        }

        // Remove stale nodes in the desugaring graph
        if (impactedFiles == null && canBeIncremental) {
            // Note that the `changedAndImpactedFiles` set may contain added files, which should not
            // exist in the graph and will be ignored.
            changedAndImpactedFiles!!.forEach { desugarGraph!!.removeNode(it) }
        }

        // Process the class files and update the desugaring graph
        process(
            dexArchiveBuilder = getDexArchiveBuilder(
                dexWorkActionParams,
                outStream,
                errStream,
                receiver
            ),
            inputClassFiles = dexWorkActionParams.dexSpec.inputClassFiles,
            outputPath = dexWorkActionParams.dexSpec.outputPath,
            desugarGraphUpdater = desugarGraph,
            processIncrementally = canBeIncremental,
            changedAndImpactedFiles = changedAndImpactedFiles
        )

        // Store the desugaring graph for use in the next build. If dexing failed earlier, it is
        // intended that we will not store the graph as the graph is only meant to contain info
        // about a previous successful build.
        if (desugarGraph != null) {
            FileUtils.mkdirs(desugarGraphFile!!.parentFile)
            ObjectOutputStream(FileOutputStream(desugarGraphFile).buffered()).use {
                it.writeObject(desugarGraph)
            }
        }
    }
}

private fun process(
    dexArchiveBuilder: DexArchiveBuilder,
    inputClassFiles: ClassBucket,
    outputPath: File,
    desugarGraphUpdater: DependencyGraphUpdater<File>?,
    processIncrementally: Boolean,
    // Not null iff processIncrementally == true.
    // Note that this set may contain removed files, but the implementation below makes sure we
    // won't process removed files.
    changedAndImpactedFiles: Set<File>?
) {
    // Filter to select a subset of the class files to process:
    //   - In incremental mode, process only changed (modified, added) or unchanged-but-impacted
    //     files.
    //   - In non-incremental mode, process all files.
    fun shouldBeProcessed(rootPath: File, relativePath: String): Boolean {
        return if (processIncrementally) {
            rootPath in changedAndImpactedFiles!! /* for jars */ ||
                    rootPath.resolve(relativePath) in changedAndImpactedFiles /* for dirs */
        } else {
            true
        }
    }

    val inputRoots = inputClassFiles.bucketGroup.getRoots()
    inputRoots.forEach { loggerWrapper.verbose("Dexing '${it.path}' to '${outputPath.path}'") }
    try {
        Closer.create().use { closer ->
            inputClassFiles.getClassFiles(filter = ::shouldBeProcessed, closer = closer).use {
                dexArchiveBuilder.convert(it, outputPath.toPath(), desugarGraphUpdater)
            }
        }
    } catch (ex: DexArchiveBuilderException) {
        throw DexArchiveBuilderException(
            "Failed to process: ${inputRoots.joinToString(", ") { it.path }}",
            ex
        )
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
                            DexArchiveBuilderTaskDelegate.sharedState.getService(dexSpec.dexParams.desugarBootclasspath).service,
                    desugarClasspath =
                    DexArchiveBuilderTaskDelegate.sharedState.getService(dexSpec.dexParams.desugarClasspath).service,
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

private val loggerWrapper = LoggerWrapper.getLogger(DexWorkAction::class.java)