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

import com.android.build.gradle.internal.res.Aapt2CompileWithBlameRunnable
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.ResourceCompilationService
import com.android.repository.Revision
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File

/** Resource compilation service built on top of a Aapt2Daemon and Gradle Worker Executors. */
class WorkerExecutorResourceCompilationService(
        private val workerExecutor: WorkerExecutor,
        private val aaptVersion: Revision) : ResourceCompilationService {

    override fun submitCompile(request: CompileResourceRequest) {
        workerExecutor.submit(Aapt2CompileWithBlameRunnable::class.java) {
            it.isolationMode = IsolationMode.NONE
            it.setParams(Aapt2CompileWithBlameRunnable.Params(aaptVersion, request))
        }
    }

    override fun compileOutputFor(request: CompileResourceRequest): File {
        return File(
                request.outputDirectory,
                Aapt2RenamingConventions.compilationRename(request.inputFile))
    }

    override fun close() {
        // No need for workerExecutor.await() here as resource compilation is the last part of the
        // merge task. This means the MergeResources task action can return, allowing other tasks
        // in the same subproject to run while resources are still being compiled.
    }
}
