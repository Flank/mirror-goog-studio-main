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

package com.android.builder.internal.aapt.v2

import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.res2.CompileResourceRequest
import com.android.utils.GrabProcessOutput
import com.android.utils.ILogger
import com.google.common.util.concurrent.SettableFuture
import java.io.Writer
import java.nio.file.Path
import java.util.concurrent.TimeoutException

/**
 * The real AAPT2 process.
 *
 * Loosely based on [com.android.builder.png.AaptProcess], but with much of the concurrency removed.
 *
 * See [Aapt2Daemon] docs for more information.
 */
class Aapt2DaemonImpl(
        displayId: String,
        aaptExecutable: Path,
        private val daemonTimeouts: Aapt2DaemonTimeouts,
        logger: ILogger) :
        Aapt2Daemon(
                displayName = "AAPT2 ${aaptExecutable.parent.fileName} Daemon $displayId",
                logger = logger) {

    private val aaptPath = aaptExecutable.toFile().absolutePath
    private val noOutputExpected = NoOutputExpected(displayName, logger)

    private lateinit var process: Process
    private lateinit var writer: Writer

    private val processOutput = object : GrabProcessOutput.IProcessOutput {
        @Volatile
        var delegate: GrabProcessOutput.IProcessOutput = noOutputExpected

        override fun out(line: String?) = delegate.out(line)
        override fun err(line: String?) = delegate.err(line)
    }

    @Throws(TimeoutException::class)
    override fun startProcess() {
        val waitForReady = WaitForReadyOnStdOut(displayName, logger)
        processOutput.delegate = waitForReady
        process = ProcessBuilder(aaptPath, Aapt2DaemonUtil.DAEMON_MODE_COMMAND).start()
        writer = try {
            GrabProcessOutput.grabProcessOutput(
                    process,
                    GrabProcessOutput.Wait.ASYNC,
                    processOutput)
            process.outputStream.bufferedWriter(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                throw e
            } finally {
                process.destroyForcibly()
            }
        }

        try {
            waitForReady.future.get(daemonTimeouts.start, daemonTimeouts.startUnit)
        } catch (e: Exception) {
            try {
                throw e
            } finally {
                shutDown()
            }
        }
        //Process is ready
        processOutput.delegate = noOutputExpected
    }

    @Throws(TimeoutException::class)
    override fun doCompile(request: CompileResourceRequest, logger: ILogger) {
        val waitForTask = WaitForTaskCompletion(displayName, logger)
        try {
            processOutput.delegate = waitForTask
            Aapt2DaemonUtil.requestCompile(writer, request)
            val error = waitForTask.future.get(daemonTimeouts.compile, daemonTimeouts.compileUnit)
            if (error != null) {
                val args = AaptV2CommandBuilder.makeCompile(request).joinToString(" \\\n        ")
                throw Aapt2Exception(
                        "Android resource compilation failed ($displayName)\n" +
                                "Command: $aaptPath compile $args\n" +
                                "Output:  $error")
            }
        } finally {
            processOutput.delegate = noOutputExpected
        }
    }

    @Throws(TimeoutException::class)
    override fun doLink(request: AaptPackageConfig, logger: ILogger) {
        val waitForTask = WaitForTaskCompletion(displayName, logger)
        try {
            processOutput.delegate = waitForTask
            Aapt2DaemonUtil.requestLink(writer, request)
            val error = waitForTask.future.get(daemonTimeouts.link, daemonTimeouts.linkUnit)
            if (error != null) {
                val configWithResourcesListed =
                        if (request.intermediateDir != null) {
                            request.copy(listResourceFiles = true)
                        } else {
                            request
                        }
                val args =
                        AaptV2CommandBuilder.makeLink(configWithResourcesListed)
                                .joinToString("\\\n        ")
                throw Aapt2Exception(
                        ("Android resource linking failed ($displayName)\n" +
                                "Command: $aaptPath link $args\n" +
                                "Output:  $error"))
            }
        } finally {
            processOutput.delegate = noOutputExpected
        }
    }

    @Throws(TimeoutException::class)
    override fun stopProcess() {
        processOutput.delegate = AllowShutdown(displayName, logger)
        writer.write("quit\n\n")
        writer.flush()

        val shutdown = process.waitFor(daemonTimeouts.stop, daemonTimeouts.stopUnit)
        if (!shutdown) {
            try {
                throw TimeoutException("$displayName: Failed to shut down within ${daemonTimeouts.stop}, ${daemonTimeouts.stopUnit}. Forcing shutdown")
            } finally {
                process.destroyForcibly()
            }
        }
    }

    class NoOutputExpected(private val displayName: String,
            val logger: ILogger) : GrabProcessOutput.IProcessOutput {
        override fun out(line: String?) {
            line?.let {
                logger.error(null, "$displayName: Unexpected standard output: $it")
            }
        }

        override fun err(line: String?) {
            line?.let {
                logger.error(null, "$displayName: Unexpected error output: $it")
            }
        }
    }

    class WaitForReadyOnStdOut(private val displayName: String,
            val logger: ILogger) : GrabProcessOutput.IProcessOutput {

        val future: SettableFuture<Void> = SettableFuture.create<Void>()

        override fun out(line: String?) {
            when (line) {
                null -> return
                "" -> return
                "Ready" -> future.set(null)
                else -> {
                    logger.error(null, "$displayName: Unexpected error output: $line")
                }
            }
        }

        override fun err(line: String?) {
            line?.let {
                logger.error(null, "$displayName: Unexpected error output: $it")
            }
        }
    }

    class WaitForTaskCompletion(
            private val displayName: String,
            val logger: ILogger) : GrabProcessOutput.IProcessOutput {

        /** Set to null on success, the error output on failure. */
        val future = SettableFuture.create<String?>()!!
        private var errors: StringBuilder? = null

        override fun out(line: String?) {
            line?.let { logger.info("%1\$s: %2\$s", displayName, it) }
        }

        override fun err(line: String?) {
            when (line) {
                null -> return
                "Done" -> future.set(errors?.toString())
                "Error" -> if (errors == null) { errors = StringBuilder() }
                else -> {
                    if (errors == null) {
                        errors = StringBuilder()
                    }
                    errors!!.append(line).append('\n')
                }
            }
        }
    }

    class AllowShutdown(private val displayName: String,
            val logger: ILogger) : GrabProcessOutput.IProcessOutput {

        override fun out(line: String?) {
            when (line) {
                null, "", "Exiting daemon" -> return
                else -> logger.error(null, "$displayName: Unexpected standard output: $line")
            }
        }

        override fun err(line: String?) {
            line?.let {
                logger.error(null, "$displayName: Unexpected error output: $line")
            }
        }
    }

}