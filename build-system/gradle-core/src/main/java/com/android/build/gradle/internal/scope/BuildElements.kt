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

package com.android.build.gradle.internal.scope

import com.android.ide.common.build.ApkInfo
import com.android.ide.common.internal.WaitableExecutor
import com.google.gson.GsonBuilder
import org.gradle.tooling.BuildException
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Path
import java.util.stream.Stream

/**
 * Encapsulates the result of a task action. Therefore it is specific to a Task that generate
 * the same type of output for different splits.
 *
 * This can represent a past task action which result got loaded from a saved .json file or
 * the result of an executing of a task (or part of task) to save to a .json file.
 */
open class BuildElements(val elements: Collection<BuildOutput>) : Iterable<BuildOutput> {

    private val executor : WaitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()

    override fun iterator(): Iterator<BuildOutput> = elements.iterator()

    fun element(apkInfo: ApkInfo) : BuildOutput? {
        return elements.find {
            // TODO : simplify once ApkInfo can become a data class.
            it.apkInfo.type == apkInfo.type
                    && it.apkInfo.filters == apkInfo.filters
                    && it.apkInfo.fullName == apkInfo.fullName
        }
    }

    fun size(): Int = elements.size
    fun isEmpty(): Boolean = elements.isEmpty()
    fun stream() : Stream<BuildOutput> = elements.stream()

    /**
     * Register an action to use all current build elements and create new built elements.
     * The passed action will not be invoked until one of the [BuildElementActionScheduler] API
     * is used.
     *
     * @param action a lambda to transform an owned element into a new file.
     */
    open fun transform(action : (apkInfo: ApkInfo, input: File) -> File?) : BuildElementActionScheduler {
        return ExecutorBasedScheduler(this, action)
    }

    /**
     * Persists the passed output types and split output to a [String] using gson.
     *
     * @param projectPath path to relativize output file paths against.
     * @return a json String.
     */
    fun persist(projectPath: Path): String {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(ApkInfo::class.java, ExistingBuildElements.ApkInfoAdapter())
        gsonBuilder.registerTypeAdapter(
                TaskOutputHolder.TaskOutputType::class.java, ExistingBuildElements.OutputTypeTypeAdapter())
        gsonBuilder.registerTypeAdapter(
                TaskOutputHolder.AnchorOutputType::class.java,
                ExistingBuildElements.OutputTypeTypeAdapter())
        val gson = gsonBuilder.create()

        // flatten and relativize the file paths to be persisted.
        return gson.toJson(elements
                .asSequence()
                .map {  buildOutput ->
                    BuildOutput(
                            buildOutput.type,
                            buildOutput.apkInfo,
                            projectPath.relativize(buildOutput.outputPath),
                            buildOutput.properties) }
                .toList())
    }

    @Throws(IOException::class)
    fun save(folder: File) : BuildElements {
        val persistedOutput = persist(folder.toPath())
        FileWriter(ExistingBuildElements.getMetadataFile(folder)).use { writer ->
            writer.append(persistedOutput)
        }
        return this
    }

    private data class ExecutorBasedScheduler(val input : BuildElements,
            val action : (apkInfo: ApkInfo, input: File) -> File?) : BuildElementActionScheduler() {

        @Throws(BuildException::class)
        override fun into(type : TaskOutputHolder.TaskOutputType) : BuildElements {
            return transform(type, action)
        }

        @Throws(BuildException::class)
        private fun transform(to: TaskOutputHolder.TaskOutputType, action : (apkInfo: ApkInfo, input: File) -> File?) : BuildElements {
            input.elements.forEach { input.executor.execute {
                ActionItem(it.apkInfo, action(it.apkInfo, it.outputFile)) }
            }

            val tasksResults = try {
                input.executor.waitForAllTasks<ActionItem>()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException(e)
            }

            return BuildElements(tasksResults.asSequence()
                    .onEach {  if (it.exception != null)
                        throw BuildException(it.exception?.message, it.exception)
                    }
                    .filter { it.value?.output != null}
                    .map {
                        BuildOutput(to,
                                (it.value as ActionItem).apkInfo,
                                (it.value as ActionItem).output!!)
                    }.toList())
        }
    }
    internal data class ActionItem(val apkInfo : ApkInfo, val output: File?)
}