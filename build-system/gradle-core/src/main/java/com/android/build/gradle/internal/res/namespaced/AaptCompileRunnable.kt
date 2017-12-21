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

package com.android.build.gradle.internal.res.namespaced

import com.android.builder.internal.aapt.Aapt
import com.android.ide.common.res2.CompileResourceRequest
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject

class AaptCompileRunnable @Inject constructor(
        private val params: Params) : Runnable {
    override fun run() {
        val aapt = params.aapt.invoke()
        val runningRequests = params.requests.map { aapt.compile(it) }

        for (toDelete in params.toDelete) {
            Files.delete(aapt.compileOutputFor(CompileResourceRequest(
                    inputFile = toDelete,
                    outputDirectory = params.outputDirectory)).toPath())
        }

        for (request in runningRequests) {
            val output =
                    request.get() ?:
                            throw IllegalStateException("AAPT2 compiles all file types, " +
                                    "requests should never return null")
            if (!output.isFile) {
                throw IllegalStateException("Output file not created $output")
            }
        }
    }

    class Params(
            val aapt: () -> Aapt,
            val requests: List<CompileResourceRequest>,
            val toDelete: List<File>,
            val outputDirectory: File) : Serializable
}
