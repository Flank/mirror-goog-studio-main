/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.adblib.utils

import com.android.adblib.ShellCommandOutput
import com.android.adblib.ShellV2Collector
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

/**
 * A [ShellV2Collector] implementation that concatenates the entire output (and `stderr`) of
 * the command execution into a single [ShellCommandOutput] instance
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 */
class TextShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<ShellCommandOutput> {

    private val stdoutCollector = TextShellCollector(bufferCapacity)
    private val stderrCollector = TextShellCollector(bufferCapacity)
    private val stdoutFlowCollector = StringFlowCollector()
    private val stderrFlowCollector = StringFlowCollector()

    override suspend fun start(collector: FlowCollector<ShellCommandOutput>) {
        stdoutCollector.start(stdoutFlowCollector)
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<ShellCommandOutput>,
        stdout: ByteBuffer
    ) {
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<ShellCommandOutput>,
        stderr: ByteBuffer
    ) {
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(collector: FlowCollector<ShellCommandOutput>, exitCode: Int) {
        stdoutCollector.end(stdoutFlowCollector)
        stderrCollector.end(stderrFlowCollector)

        val result = ShellCommandOutput(
            stdoutFlowCollector.value ?: "",
            stderrFlowCollector.value ?: "",
            exitCode
        )
        collector.emit(result)
    }

    class StringFlowCollector : FlowCollector<String> {

        var value: String? = null

        override suspend fun emit(value: String) {
            this.value = value
        }
    }
}
