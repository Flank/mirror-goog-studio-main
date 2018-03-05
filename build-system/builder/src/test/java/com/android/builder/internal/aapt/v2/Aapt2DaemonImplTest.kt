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

import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.AaptTestUtils
import com.android.ide.common.resources.CompileResourceRequest
import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockLog
import com.android.testutils.TestUtils
import com.android.testutils.apk.Zip
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertFailsWith

/** Tests for [Aapt2DaemonImpl], including error conditions */
class Aapt2DaemonImplTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testName = TestName()

    private val logger = MockLog()

    /** keep track of the daemon to ensure it is closed*/
    private var daemon: Aapt2Daemon? = null

    /** No errors or warnings output when the daemon is not used at all. */
    @Test
    fun noOperationsCheck() {
        createDaemon().shutDown()
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
        val daemon = createDaemon()
        requests.forEach { daemon.compile(it, logger) }
        assertThat(outDir.list()).asList()
                .containsExactlyElementsIn(
                        requests.map { Aapt2RenamingConventions.compilationRename(it.inputFile) })
    }

    @Test
    fun testWarningsDoNotFailBuild() {
        val outDir = temporaryFolder.newFolder()
        val request = CompileResourceRequest(
                        inputFile = valuesFile(
                                "strings",
                                "<resources><string name=\"foo\">%s %d</string></resources>"),
                        outputDirectory = outDir)

        val daemon = createDaemon()
        daemon.compile(request, logger)
        assertThat(outDir.list()).asList()
                .containsExactly(Aapt2RenamingConventions.compilationRename(request.inputFile))
        assertThat(logger.messages).hasSize(2)
        assertThat(logger.messages[1]).contains(
                "warn: multiple substitutions specified in non-positional format")
        logger.clear()
    }

    @Test
    fun testCompileInvalidFile() {
        val compiledDir = temporaryFolder.newFolder()
        val inputFile = resourceFile("values", "foo.txt", "content")
        val daemon = createDaemon()
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
        val daemon = createDaemon()

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

        val request = AaptPackageConfig(
                androidJarPath = target.getPath(IAndroidTarget.ANDROID_JAR),
                manifestFile = manifest,
                resourceDirs = ImmutableList.of(compiledDir),
                resourceOutputApk = outputFile,
                options = AaptOptions(),
                variantType = VariantTypeImpl.BASE_APK
        )

        daemon.link(request, logger)
        assertThat(Zip(outputFile)).containsFileWithContent("res/raw/foo.txt", "content")
    }

    @Test
    fun testLinkInvalidManifest() {
        val daemon = createDaemon()

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

        val request = AaptPackageConfig(
                androidJarPath = target.getPath(IAndroidTarget.ANDROID_JAR),
                manifestFile = manifest,
                resourceOutputApk = outputFile,
                resourceDirs = ImmutableList.of(compiledDir),
                options = AaptOptions(),
                variantType = VariantTypeImpl.BASE_APK
        )
        val exception = assertFailsWith(Aapt2Exception::class) {
            daemon.link(request.copy(intermediateDir = temporaryFolder.newFolder()), logger)
        }
        assertThat(exception.message).contains("Android resource linking failed")
        assertThat(exception.message).contains("AndroidManifest.xml")
        assertThat(exception.message).contains("error: unclosed token.")
        // Compiled resources should be listed in a file.
        assertThat(exception.message).contains("@")

        val exception2 = assertFailsWith(Aapt2Exception::class) {
            daemon.link(request, logger)
        }
        assertThat(exception2.message).contains("Android resource linking failed")
        assertThat(exception2.message).contains("AndroidManifest.xml")
        assertThat(exception2.message).contains("error: unclosed token.")
        // Compiled resources should not be listed in a file.
        assertThat(exception2.message).doesNotContain("@")
        assertThat(exception2.message).contains("foo.txt")
    }

    @Test
    fun testInvalidAaptBinary() {
        val compiledDir = temporaryFolder.newFolder()
        val daemon = createDaemon(
                executable = temporaryFolder.newFolder("invalidBuildTools").toPath().resolve("aapt2"),
                daemonTimeouts = Aapt2DaemonTimeouts())
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }
        assertThat(exception.message).contains("Daemon startup failed")
        assertThat(exception.cause).isInstanceOf(IOException::class.java)
    }

    @Test
    fun pngWithLongPathCrunchingTest() {
        val daemon = createDaemon()

        val request = CompileResourceRequest(
                AaptTestUtils.getTestPngWithLongFileName(temporaryFolder),
                AaptTestUtils.getOutputDir(temporaryFolder),
                "test")
        daemon.compile(request, logger)
        val compiled =
                request.outputDirectory.toPath().resolve(
                        Aapt2RenamingConventions.compilationRename(request.inputFile))
        assertThat(compiled).exists()
    }

    @Test
    @Throws(Exception::class)
    fun crunchFlagIsRespected() {
        val daemon = createDaemon()
        val png = AaptTestUtils.getTestPng(temporaryFolder)
        val outDir = AaptTestUtils.getOutputDir(temporaryFolder)
        daemon.compile(
                CompileResourceRequest(
                        inputFile = png,
                        outputDirectory = outDir,
                        isPseudoLocalize = false,
                        isPngCrunching = true),
                logger)
        val outFile = outDir.toPath().resolve(Aapt2RenamingConventions.compilationRename(png))
        val withCrunchEnabledSize = Files.size(outFile)
        daemon.compile(
                CompileResourceRequest(
                        inputFile = png,
                        outputDirectory = outDir,
                        isPseudoLocalize = false,
                        isPngCrunching = false),
                logger)

        val withCrunchDisabledSize = Files.size(outFile)
        assertThat(withCrunchEnabledSize).isLessThan(withCrunchDisabledSize)
    }

    @Test
    @Throws(Exception::class)
    fun ninePatchPngsAlwaysProcessedEvenWhenCrunchingDisabled() {
        val daemon = createDaemon()
        val ninePatch = AaptTestUtils.getTest9Patch(temporaryFolder)
        val outDir = AaptTestUtils.getOutputDir(temporaryFolder)

        daemon.compile(
                CompileResourceRequest(
                        inputFile = ninePatch,
                        outputDirectory = outDir,
                        isPseudoLocalize = false,
                        isPngCrunching = true),
                logger)
        val outFile = outDir.toPath().resolve(Aapt2RenamingConventions.compilationRename(ninePatch))
        val withCrunchEnabled = Files.readAllBytes(outFile)
        daemon.compile(
                CompileResourceRequest(
                        inputFile = ninePatch,
                        outputDirectory = outDir,
                        isPseudoLocalize = false,
                        isPngCrunching = false),
                logger)
        val withCrunchDisabled = Files.readAllBytes(outFile)
        assertThat(withCrunchDisabled).isEqualTo(withCrunchEnabled)
    }

    @Test
    fun testNeverReady() {
        val args = listOf<String>(
                FileUtils.join(System.getProperty("java.home"), "bin", "java"),
                "-cp",
                System.getProperty("java.class.path"),
                NeverReadyAapt2Daemon::class.java.name)

        val daemon = Aapt2DaemonImpl(
                displayId = "'Aapt2DaemonImplTest.${testName.methodName}'",
                aaptPath = "fake_path",
                aaptCommand = args,
                versionString = "fake_version",
                daemonTimeouts = Aapt2DaemonTimeouts(
                        start = 2, startUnit = TimeUnit.MILLISECONDS,
                        stop = 3000, stopUnit = TimeUnit.NANOSECONDS),
                logger = logger)
        this.daemon = daemon

        val compiledDir = temporaryFolder.newFolder()
        val exception = assertFailsWith(Aapt2InternalException::class) {
            daemon.compile(
                    CompileResourceRequest(
                            inputFile = File("values/does_not_matter.xml"),
                            outputDirectory = compiledDir),
                    logger)
        }

        assertThat(exception.javaClass).isEqualTo(Aapt2InternalException::class.java)
        assertThat(exception.cause).isNotNull()
        // The inner startup failure
        assertThat(exception.cause!!.javaClass).isEqualTo(Aapt2InternalException::class.java)
        // The original cause was a timeout waiting for ready.
        assertThat(Throwables.getRootCause(exception).javaClass).isEqualTo(TimeoutException::class.java)

        // The shutdown failure should be a suppressed exception on the inner startup failure,
        // which is also a timeout.
        assertThat(exception.cause!!.suppressed).hasLength(1)
        exception.cause!!.suppressed[0].let {
            assertThat(it.javaClass).isEqualTo(TimeoutException::class.java)
            assertThat(it.cause).isNull()
        }
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

    private fun createDaemon(
            daemonTimeouts: Aapt2DaemonTimeouts = Aapt2DaemonTimeouts(),
            executable: Path = TestUtils.getAapt2()): Aapt2Daemon {
        val daemon = Aapt2DaemonImpl(
                displayId = "'Aapt2DaemonImplTest.${testName.methodName}'",
                aaptExecutable = executable,
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
        private val target: IAndroidTarget by lazy(LazyThreadSafetyMode.NONE) {
            AndroidSdkHandler.getInstance(TestUtils.getSdk())
                    .getAndroidTargetManager(FakeProgressIndicator())
                    .getTargets(FakeProgressIndicator())
                    .maxBy { it.version }!!
        }
    }
}