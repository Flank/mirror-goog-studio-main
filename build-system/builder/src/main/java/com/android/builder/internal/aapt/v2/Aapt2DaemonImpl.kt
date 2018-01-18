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
import java.util.Locale
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
        private val aaptPath: String,
        private val aaptCommand: List<String>,
        versionString: String,
        private val daemonTimeouts: Aapt2DaemonTimeouts,
        logger: ILogger) :
        Aapt2Daemon(
                displayName = "AAPT2 $versionString Daemon $displayId",
                logger = logger) {

    constructor(
            displayId: String,
            aaptExecutable: Path,
            daemonTimeouts: Aapt2DaemonTimeouts,
            logger: ILogger) :
            this(
                    displayId = displayId,
                    aaptPath = aaptExecutable.toFile().absolutePath,
                    aaptCommand = listOf(
                            aaptExecutable.toFile().absolutePath,
                            Aapt2DaemonUtil.DAEMON_MODE_COMMAND),
                    versionString = aaptExecutable.parent.fileName.toString(),
                    daemonTimeouts = daemonTimeouts,
                    logger = logger)

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
        process = ProcessBuilder(aaptCommand).start()
        writer = try {
            GrabProcessOutput.grabProcessOutput(
                    process,
                    GrabProcessOutput.Wait.ASYNC,
                    processOutput)
            process.outputStream.bufferedWriter(Charsets.UTF_8)
        } catch (e: Exception) {
            // Something went wrong with starting the process or reader threads.
            // Propagate the original exception, but also try to forcibly shutdown the process.
            throw e.apply {
                try {
                    process.destroyForcibly()
                } catch (suppressed: Exception) {
                    addSuppressed(suppressed)
                }
            }
        }

        try {
            waitForReady.future.get(daemonTimeouts.start, daemonTimeouts.startUnit)
        } catch (e: TimeoutException) {
            stopQuietly("Failed to start AAPT2 process $displayName. " +
                    "Not ready within ${daemonTimeouts.start} " +
                    "${daemonTimeouts.startUnit.name.toLowerCase(Locale.US)}.", e)
        } catch (e: Exception) {
            stopQuietly("Failed to start AAPT2 process.", e)
        }
        //Process is ready
        processOutput.delegate = noOutputExpected
    }

    /**
     * Something went wrong with the daemon startup.
     *
     * Propagate the original exception, but also try to shutdown the process.
     */
    private fun stopQuietly(message: String, e: Exception): Nothing {
        throw Aapt2InternalException(message, e).apply {
            try {
                stopProcess()
            } catch (suppressed: Exception) {
                addSuppressed(suppressed)
            }
        }
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
            val errors = waitForTask.future.get(daemonTimeouts.link, daemonTimeouts.linkUnit)
            if (errors != null) {
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
                                "Output:  $errors"))
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
        if (shutdown) {
            return
        }
        throw TimeoutException(
                "$displayName: Failed to shut down within " +
                        "${daemonTimeouts.stop} " +
                        "${daemonTimeouts.stopUnit.name.toLowerCase(Locale.US)}. " +
                        "Forcing shutdown").apply {
            try {
                process.destroyForcibly()
            } catch (suppressed: Exception) {
                addSuppressed(suppressed)
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
        private var foundError: Boolean = false

        override fun out(line: String?) {
            line?.let { logger.info("%1\$s: %2\$s", displayName, it) }
        }

        override fun err(line: String?) {
            when (line) {
                null -> return
                "Done" -> {
                    when {
                        foundError -> future.set(errors!!.toString())
                        errors != null -> {
                            logger.warning(errors.toString())
                            future.set(null)
                        }
                        else -> future.set(null)
                    }
                }
                "Error" -> {
                    foundError = true
                    if (errors == null) {
                        errors = StringBuilder()
                    }
                }
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