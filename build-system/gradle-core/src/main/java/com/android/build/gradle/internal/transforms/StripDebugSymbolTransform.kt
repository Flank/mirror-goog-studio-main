/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.stripping.createSymbolStripExecutableFinder
import com.google.common.base.Preconditions.checkArgument

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Status.ADDED
import com.android.build.api.transform.Status.CHANGED
import com.android.build.api.transform.Status.REMOVED
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.stripping.SymbolStripExecutableFinder
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import org.gradle.api.Project

/**
 * Transform to remove debug symbols from native libraries.
 */
class StripDebugSymbolTransform(
    private val project: Project,
    ndkHandler: NdkHandler,
    excludePattern: Set<String>,
    private val isLibrary: Boolean,
    private val includeFeaturesInScopes: Boolean
) : Transform() {

    private val stripToolFinder: SymbolStripExecutableFinder
    private val excludeMatchers = excludePattern.map { compileGlob(it) }

    init {
        checkArgument(ndkHandler.isConfigured)
        stripToolFinder = createSymbolStripExecutableFinder(ndkHandler)
    }

    override fun getName() = "stripDebugSymbol"

    override fun getInputTypes() = setOf(ExtendedContentType.NATIVE_LIBS)

    override fun getScopes(): MutableSet<in Scope> =
        when {
            isLibrary -> TransformManager.PROJECT_ONLY
            includeFeaturesInScopes -> TransformManager.SCOPE_FULL_WITH_FEATURES
            else -> TransformManager.SCOPE_FULL_PROJECT
        }

    override fun isIncremental() = true

    override fun isCacheable() = true



    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> =
        stripToolFinder.executables().map { SecondaryFile.nonIncremental(it) }.toMutableList()

    override fun transform(transformInvocation: TransformInvocation) {
        val outputProvider =
            transformInvocation.outputProvider ?: throw AssertionError(
                "Missing output object for transform $name")

        val isIncremental = transformInvocation.isIncremental

        if (!isIncremental) {
            outputProvider.deleteAll()
        }
        for (transformInput in transformInvocation.inputs) {
            for (directoryInput in transformInput.directoryInputs) {
                val folder = directoryInput.file
                val output =
                    outputProvider.getContentLocation(
                        folder.toString(),
                        inputTypes,
                        directoryInput.scopes,
                        Format.DIRECTORY
                    )
                if (isIncremental) {
                    for (fileStatus in directoryInput.changedFiles.entries) {
                        val input = fileStatus.key
                        if (input.isDirectory) {
                            continue
                        }
                        val abiName = input.parentFile.name
                        val abi = Abi.getByName(abiName)
                        val path = FileUtils.relativePossiblyNonExistingPath(input, folder)
                        val strippedLib = File(output, path)

                        when (fileStatus.value) {
                            ADDED, CHANGED ->
                                if (excludeMatchers.any { it.matches(Paths.get(path)) } ) {
                                    FileUtils.mkdirs(strippedLib.parentFile)
                                    FileUtils.copyFile(input, strippedLib)
                                } else {
                                    stripFile(input, strippedLib, abi)
                                }
                            REMOVED -> FileUtils.deletePath(File(output, path))
                            else -> {}
                        }
                    }
                } else {
                    for (input in FileUtils.getAllFiles(folder)) {
                        if (input.isDirectory) {
                            continue
                        }
                        val abiName = input.parentFile.name
                        val abi = Abi.getByName(abiName)
                        val path = FileUtils.relativePath(input, folder)
                        val strippedLib = File(output, path)

                        if (excludeMatchers.any { it.matches(Paths.get(path)) } ) {
                            FileUtils.mkdirs(strippedLib.parentFile)
                            FileUtils.copyFile(input, strippedLib)
                        } else {
                            stripFile(input, strippedLib, abi)
                        }
                    }
                }
            }

            for (jarInput in transformInput.jarInputs) {
                val outFile =
                    outputProvider.getContentLocation(
                        jarInput.file.toString(),
                        inputTypes,
                        jarInput.scopes,
                        Format.JAR
                    )
                if (!isIncremental || jarInput.status == ADDED || jarInput.status == CHANGED) {
                    // Just copy the jar files.  Native libraries in a jar files are not built by
                    // the plugin.  We expect the libraries to be stripped as we won't be able to
                    // debug the libraries unless we extract them anyway.
                    FileUtils.mkdirs(outFile.parentFile)
                    FileUtils.copyFile(jarInput.file, outFile)
                } else if (jarInput.status == REMOVED) {
                    FileUtils.deleteIfExists(outFile)
                }
            }
        }
    }

    private fun stripFile(input: File, output: File, abi: Abi?) {
        FileUtils.mkdirs(output.parentFile)
        val logger = LoggerWrapper(project.logger)
        val exe =
            stripToolFinder.stripToolExecutableFile(input, abi) {
                logger.warning("$it Packaging it as is.")
                return@stripToolExecutableFile null
            }

        if (exe == null) {
            // The strip executable couldn't be found and a message about the failure was reported
            // in getPathToStripExecutable.
            // Fall back to copying the file to the output location
            FileUtils.copyFile(input, output)
            return
        }

        val builder = ProcessInfoBuilder()
        builder.setExecutable(exe)
        builder.addArgs("--strip-unneeded")
        builder.addArgs("-o")
        builder.addArgs(output.toString())
        builder.addArgs(input.toString())
        val result =
            GradleProcessExecutor(project).execute(
                builder.createProcess(),
                LoggedProcessOutputHandler(logger)
            )
        if (result.exitValue != 0) {
            logger.warning(
                "Unable to strip library ${input.absolutePath} due to error ${result.exitValue}"
                        + " returned from $exe, packaging it as is.")
            FileUtils.copyFile(input, output)
        }
    }
}

private fun compileGlob(pattern: String): PathMatcher {
        val maybeSlash = if (pattern.startsWith("/") || pattern.startsWith("*")) "" else "/"
        return FileSystems.getDefault().getPathMatcher("glob:$maybeSlash$pattern")
}
