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
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexArchiveBuilderConfig
import com.android.builder.dexing.DexArchiveBuilderException
import com.android.builder.dexing.DexerTool
import com.android.builder.dexing.DirectoryBucketGroup
import com.android.dx.command.dexer.DxContext
import com.android.ide.common.blame.MessageReceiver
import com.google.common.io.Closer
import org.gradle.api.logging.Logging
import org.gradle.tooling.BuildException
import java.io.File
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
    val dexArchiveBuilder = getDexArchiveBuilder(
        dexWorkActionParams,
        outStream,
        errStream,
        receiver
    )

    val inputClassFiles = dexWorkActionParams.dexSpec.inputClassFiles

    val hasIncrementalInfo =
        inputClassFiles.bucketGroup is DirectoryBucketGroup
                && dexWorkActionParams.dexSpec.isIncremental

    // A filter to select a subset of the class files in the bucket to process
    fun toProcess(rootPath: File, relativePath: String): Boolean {
        if (!hasIncrementalInfo) {
            return true
        }

        val resolved = rootPath.resolve(relativePath)
        return resolved in dexWorkActionParams.dexSpec.impactedFiles
                || resolved in dexWorkActionParams.dexSpec.changedFiles
    }

    val inputRoots = inputClassFiles.bucketGroup.getRoots()
    inputRoots.forEach {
        loggerWrapper
            .verbose("Dexing '${it.path}' to '${dexWorkActionParams.dexSpec.outputPath.path}'")
    }

    try {
        Closer.create().use { closer ->
            inputClassFiles.getClassFiles(filter = ::toProcess, closer = closer).use {
                dexArchiveBuilder.convert(it, dexWorkActionParams.dexSpec.outputPath.toPath())
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

private val loggerWrapper = LoggerWrapper.getLogger(DexWorkAction::class.java)