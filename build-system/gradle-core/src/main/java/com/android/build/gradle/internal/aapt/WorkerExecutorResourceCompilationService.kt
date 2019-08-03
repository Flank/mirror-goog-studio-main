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

package com.android.build.gradle.internal.aapt

import com.android.SdkConstants.DOT_9PNG
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.ResourceCompilationService
import com.android.ide.common.workers.WorkerExecutorFacade
import java.io.File

/** Resource compilation service built on top of a Aapt2Daemon and Gradle Worker Executors. */
class WorkerExecutorResourceCompilationService(
    private val workerExecutor: WorkerExecutorFacade,
    private val aapt2ServiceKey: Aapt2ServiceKey,
    private val errorFormatMode: SyncOptions.ErrorFormatMode
) : ResourceCompilationService {

    /** Temporary workaround for b/73804575 / https://github.com/gradle/gradle/issues/4502
     *  Only submit a small number of worker actions */
    private val requests: MutableList<CompileResourceRequest> = ArrayList()

    override fun submitCompile(request: CompileResourceRequest) {
        // b/73804575
        requests.add(request)

    }

    override fun compileOutputFor(request: CompileResourceRequest): File {
        return File(
                request.outputDirectory,
                Aapt2RenamingConventions.compilationRename(request.inputFile))
    }

    private fun getExtension(file: File): String {
        // kotlin File.extension returns png for 9.png files
        if (file.name.endsWith(DOT_9PNG)) {
            return DOT_9PNG
        }
        return file.extension
    }

    override fun close() {
        if (requests.isEmpty()) {
            return
        }
        // Sort the resource files by extension and size for a better distribution of files
        // between workers. Files of the same type will be distributed equally between the workers.
        // Large files of the same type will also be distributed equally between the workers.
        requests.sortWith(compareBy({getExtension(it.inputFile)}, {it.inputFile.length()}))
        val buckets = minOf(requests.size, 8) // Max 8 buckets

        for (bucket in 0 until buckets) {
            val bucketRequests = requests.filterIndexed { i, _ ->
                i.rem(buckets) == bucket
            }
            // b/73804575
            workerExecutor.submit(
                Aapt2CompileRunnable::class.java,
                Aapt2CompileRunnable.Params(aapt2ServiceKey, bucketRequests, errorFormatMode, true)
            )
        }
        requests.clear()

        // No need for workerExecutor.await() here as resource compilation is the last part of the
        // merge task. This means the MergeResources task action can return, allowing other tasks
        // in the same subproject to run while resources are still being compiled.
        workerExecutor.close()
    }
}
