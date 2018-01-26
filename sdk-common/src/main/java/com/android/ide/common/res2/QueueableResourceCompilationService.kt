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

package com.android.ide.common.res2

import com.android.annotations.concurrency.GuardedBy
import java.io.File
import java.util.concurrent.Future

/** Resource compilation service built on top of a QueueableResourceCompiler. */
class QueueableResourceCompilationService(private val queueableResourceCompiler: QueueableResourceCompiler)
    : ResourceCompilationService {

    @GuardedBy("this")
    private val pending = mutableListOf<Future<File>>()

    @Synchronized
    override fun submitCompile(request: CompileResourceRequest) {
        pending.add(queueableResourceCompiler.compile(request))
    }

    override fun compileOutputFor(request: CompileResourceRequest): File =
        queueableResourceCompiler.compileOutputFor(request)

    /** Ensure all resource processing is complete before returning. */
    @Synchronized
    override fun close() {
        pending.forEach { it.get() }
        pending.clear()
        queueableResourceCompiler.close()
    }
}