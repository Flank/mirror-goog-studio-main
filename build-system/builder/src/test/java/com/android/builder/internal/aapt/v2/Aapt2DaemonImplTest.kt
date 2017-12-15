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

package com.android.builder.internal.aapt.v2

import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.res2.CompileResourceRequest
import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockLog
import com.android.testutils.TestUtils
import com.android.testutils.apk.Zip
import com.android.testutils.truth.MoreTruth.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

/** Tests for [Aapt2DaemonImpl], including error conditions */
class Aapt2DaemonImplTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val logger = MockLog()

    /** keep track of the daemon to ensure it is closed*/
    private var daemon: Aapt2Daemon? = null

    /** No errors or warnings output when the daemon is not used at all. */
    @Test
    fun noOperationsCheck() {

        createDaemon(0).shutDown()
    }

    @Test
    fun testCompileMultipleCalls() {
        val outDir = temporaryFolder.newFolder()
        val requests = listOf(
                CompileResourceRequest(
                        inputFile = valuesFile("strings", "<resources></resources>"),
                        outputDirectory = outDir),
                CompileResourceRequest(
                        inputFile = valuesFile("styles", "<resources></resources>"),
                        outputDirectory = outDir)
        )
        val daemon = createDaemon(1)
        requests.forEach { daemon.compile(it, logger) }
        assertThat(outDir.list()).asList()
                .containsExactlyElementsIn(
                        requests.map { Aapt2RenamingConventions.compilationRename(it.inputFile) })
    }

    @Test
    fun testCompileInvalidFile() {
        val compiledDir = temporaryFolder.newFolder()
        val inputFile = resourceFile("values", "foo.txt", "content")
        val daemon = createDaemon(1)
        val exception = assertFailsWith(Aapt2Exception::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = inputFile,
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(exception.message).contains("error: invalid file path")
        assertThat(exception.message).contains("foo.txt")
    }

    @Test
    fun testLink() {
        val daemon = createDaemon(2)

        val compiledDir = temporaryFolder.newFolder()
        daemon.compile(
                CompileResourceRequest(
                        inputFile = resourceFile("raw", "foo.txt", "content"),
                        outputDirectory = compiledDir),
                logger)

        val manifest = resourceFile(
                "src",
                "AndroidManifest.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                    |<manifest
                    |        xmlns:android="http://schemas.android.com/apk/res/android"
                    |        package="com.example.aapt2daemon.test">
                    |</manifest>""".trimMargin())

        val outputFile = File(temporaryFolder.newFolder(), "lib.apk")

        val request = AaptPackageConfig.Builder()
                .setAndroidTarget(target)
                .setManifestFile(manifest)
                .setResourceDir(compiledDir)
                .setResourceOutputApk(outputFile)
                .setLogger(logger)
                .setOptions(AaptOptions(noCompress = null,
                        failOnMissingConfigEntry = false,
                        additionalParameters = null))
                .build()

        daemon.link(request, temporaryFolder.newFolder())
        assertThat(Zip(outputFile)).containsFileWithContent("res/raw/foo.txt", "content")
    }

    @Test
    fun testLinkInvalidManifest() {
        val daemon = createDaemon(3)

        val compiledDir = temporaryFolder.newFolder()
        daemon.compile(
                CompileResourceRequest(
                        inputFile = resourceFile("raw", "foo.txt", "content"),
                        outputDirectory = compiledDir),
                logger)

        val manifest = resourceFile(
                "src",
                "AndroidManifest.xml",
                """<""")

        val outputFile = File(temporaryFolder.newFolder(), "lib.apk")

        val request = AaptPackageConfig.Builder()
                .setAndroidTarget(target)
                .setManifestFile(manifest)
                .setResourceOutputApk(outputFile)
                .setResourceDir(compiledDir)
                .setLogger(logger)
                .setOptions(AaptOptions(noCompress = null,
                        failOnMissingConfigEntry = false,
                        additionalParameters = null))
                .build()
        val exception = assertFailsWith(Aapt2Exception::class) {
            daemon.link(request, tempDirectory = temporaryFolder.newFolder())
        }
        assertThat(exception.message).contains("Android resource linking failed")
        assertThat(exception.message).contains("AndroidManifest.xml")
        assertThat(exception.message).contains("error: unclosed token.")
        // Compiled resources should be listed in a file.
        assertThat(exception.message).contains("@")
    }

    @Test
    fun testCompileTimeout() {
        val compiledDir = temporaryFolder.newFolder()
        val daemon = createDaemon(5, Aapt2DaemonTimeouts(compile = 0, compileUnit = TimeUnit.SECONDS))
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(exception.message).contains("Compile")
        assertThat(exception.message).contains("timed out, attempting to stop daemon")
        // The daemon should be shut down.
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
        // The compile might succeed, ignore the output from it.
        logger.clear()
    }

    @Test
    fun testLinkTimeout() {
        val manifest = resourceFile(
                "src",
                "AndroidManifest.xml",
                """<""")

        val outputFile = File(temporaryFolder.newFolder(), "lib.apk")

        val request = AaptPackageConfig.Builder()
                .setAndroidTarget(target)
                .setManifestFile(manifest)
                .setResourceOutputApk(outputFile)
                .setLogger(logger)
                .setOptions(AaptOptions(noCompress = null,
                        failOnMissingConfigEntry = false,
                        additionalParameters = null))
                .build()

        val daemon = createDaemon(1, Aapt2DaemonTimeouts(link = 0, linkUnit = TimeUnit.SECONDS))
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.link(request, tempDirectory = temporaryFolder.newFolder())
        }
        assertThat(exception.message).contains("Link")
        assertThat(exception.message).contains("timed out, attempting to stop daemon")
        // The daemon should be shut down.
        assertThat(daemon.state).isEqualTo(Aapt2Daemon.State.SHUTDOWN)
        // The compile might succeed, ignore the output from it.
        logger.clear()
    }


    @Test
    fun testInvalidAaptBinary() {
        val compiledDir = temporaryFolder.newFolder()
        val daemon = Aapt2DaemonImpl(
                displayId = 0,
                aaptExecutable = temporaryFolder.newFolder("invalidBuildTools").toPath().resolve("aapt2"),
                logger = logger,
                daemonTimeouts = Aapt2DaemonTimeouts())
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(exception.message).contains("failed to start process")
        assertThat(exception.cause).isInstanceOf(IOException::class.java)
    }

    @After
    fun assertNoWarningOrErrorLogs() {
        assertThat(logger.messages.filter { !(isStartOrShutdownLog(it) || it.startsWith("V")) })
                .isEmpty()
    }

    @After
    fun shutdownDaemon() {
        daemon?.let { daemon ->
            if (daemon.state != Aapt2Daemon.State.SHUTDOWN) {
                daemon.shutDown()
            }
        }
    }

    private fun isStartOrShutdownLog(line: String) =
            line.startsWith("P") && (line.contains("starting") || line.contains("shutdown"))

    private fun createDaemon(displayId: Int,
            daemonTimeouts: Aapt2DaemonTimeouts = Aapt2DaemonTimeouts()): Aapt2Daemon {
        val daemon = Aapt2DaemonImpl(
                displayId = displayId,
                aaptExecutable = aaptExecutable,
                logger = logger,
                daemonTimeouts = daemonTimeouts)
        this.daemon = daemon
        return daemon
    }

    private fun valuesFile(name: String, content: String) =
            resourceFile("values", "$name.xml", content)

    private fun resourceFile(directory: String, name: String, content: String) =
            temporaryFolder.newFolder()
                    .toPath()
                    .resolve(directory)
                    .resolve(name)
                    .apply {
                        Files.createDirectories(this.parent)
                        Files.write(this, content.toByteArray(StandardCharsets.UTF_8))
                    }
                    .toFile()

    companion object {
        private val aaptExecutable: Path by lazy(LazyThreadSafetyMode.NONE) {
            Paths.get(AndroidSdkHandler.getInstance(TestUtils.getSdk())
                    .getLatestBuildTool(FakeProgressIndicator(), true)!!
                    .getPath(BuildToolInfo.PathId.AAPT2))
        }

        private val target: IAndroidTarget by lazy(LazyThreadSafetyMode.NONE) {
            AndroidSdkHandler.getInstance(TestUtils.getSdk())
                    .getAndroidTargetManager(FakeProgressIndicator())
                    .getTargets(FakeProgressIndicator())
                    .maxBy { it.version }!!
        }
    }
}