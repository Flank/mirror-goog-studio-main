/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.stripping.SymbolStripExecutableFinder
import com.android.build.gradle.internal.fixtures.FakeGradleExecOperations
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeInjectableService
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.ide.common.resources.FileStatus
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.reflect.jvm.javaMethod

/**
 * Unit tests for [StripDebugSymbolsTask].
 */
class StripDebugSymbolsTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()
    private val execOperations = FakeGradleExecOperations()

    private lateinit var inputDir: File
    private lateinit var x86Foo: File
    private lateinit var x86DoNotStrip: File
    private lateinit var armeabiFoo: File
    private lateinit var armeabiDoNotStrip: File
    private lateinit var outputDir: File
    private lateinit var fakeExe: File
    private lateinit var stripToolFinderProvider: Provider<SymbolStripExecutableFinder>
    private lateinit var workers: WorkerExecutor
    private lateinit var instantiatorTask: AndroidVariantTask

    @Before
    fun setUp() {
        // create input dir with lib/x86/foo.so, lib/x86/doNotStrip.so, lib/armeabi/foo.so,
        // and lib/armeabi/doNotStrip.so files
        inputDir = temporaryFolder.newFolder("inputDir")
        x86Foo = FileUtils.join(inputDir, "lib", "x86", "foo.so")
        FileUtils.createFile(x86Foo, "foo")
        assertThat(x86Foo).exists()
        x86DoNotStrip = FileUtils.join(inputDir, "lib", "x86", "doNotStrip.so")
        FileUtils.createFile(x86DoNotStrip, "doNotStrip")
        assertThat(x86DoNotStrip).exists()
        armeabiFoo = FileUtils.join(inputDir, "lib", "armeabi", "foo.so")
        FileUtils.createFile(armeabiFoo, "foo")
        assertThat(armeabiFoo).exists()
        armeabiDoNotStrip = FileUtils.join(inputDir, "lib", "armeabi", "doNotStrip.so")
        FileUtils.createFile(armeabiDoNotStrip, "doNotStrip")
        assertThat(armeabiDoNotStrip).exists()

        outputDir = temporaryFolder.newFolder("outputDir")

        fakeExe = temporaryFolder.newFile("fake.exe")
        stripToolFinderProvider = FakeGradleProvider(
            SymbolStripExecutableFinder(mapOf(Pair(Abi.X86, fakeExe), Pair(Abi.ARMEABI, fakeExe)))
        )

        with(ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()) {
            workers = FakeGradleWorkExecutor(
                objects, temporaryFolder.newFolder(), listOf(
                    FakeInjectableService(
                        StripDebugSymbolsRunnable::execOperations.getter.javaMethod!!,
                        execOperations
                    )
                )
            )
            instantiatorTask = tasks.create("task", AndroidVariantTask::class.java)
            instantiatorTask.analyticsService.set(FakeNoOpAnalyticsService())
        }
    }

    @Test
    fun `test non-incremental`() {
        val keepDebugSymbols = setOf("**/doNotStrip.so")

        StripDebugSymbolsDelegate(
            workers,
            inputDir,
            outputDir,
            keepDebugSymbols,
            stripToolFinderProvider,
            null,
            instantiatorTask
        ).run()

        // Check that executable only runs for x86Foo and armeabiFoo (not doNotStrip files)
        assertThat(execOperations.capturedExecutions).named("number of invocations").hasSize(2)
        for (processInfo in execOperations.capturedExecutions) {
            assertThat(processInfo.executable).isEqualTo(fakeExe.absolutePath)
            if (processInfo.args.contains(x86Foo.toString())) {
                val path = x86Foo.toRelativeString(inputDir)
                val outputFile = File(outputDir, path)
                assertThat(processInfo.args)
                    .containsExactly(
                        "--strip-unneeded", "-o", outputFile.toString(), x86Foo.toString()
                    )
            } else {
                val path = armeabiFoo.toRelativeString(inputDir)
                val outputFile = File(outputDir, path)
                assertThat(processInfo.args)
                    .containsExactly(
                        "--strip-unneeded", "-o", outputFile.toString(), armeabiFoo.toString()
                    )
            }
        }

        // Check that all files are in outputDir, even doNotStrip files
        val x86FooPath = x86Foo.toRelativeString(inputDir)
        assertThat(File(outputDir, x86FooPath)).exists()
        val x86DoNotStripPath = x86DoNotStrip.toRelativeString(inputDir)
        assertThat(File(outputDir, x86DoNotStripPath)).exists()
        val armeabiFooPath = armeabiFoo.toRelativeString(inputDir)
        assertThat(File(outputDir, armeabiFooPath)).exists()
        val armeabiDoNotStripPath = armeabiDoNotStrip.toRelativeString(inputDir)
        assertThat(File(outputDir, armeabiDoNotStripPath)).exists()
    }

    @Test
    fun `test incremental`() {

        val changedInputs =
            mapOf(Pair(x86Foo, FileStatus.NEW), Pair(armeabiDoNotStrip, FileStatus.NEW))

        val excludePatterns = setOf("**/doNotStrip.so")

        StripDebugSymbolsDelegate(
            workers,
            inputDir,
            outputDir,
            excludePatterns,
            stripToolFinderProvider,
            changedInputs,
            instantiatorTask
        ).run()

        // Check that executable only runs for x86Foo
        // Check that executable only runs for x86Foo and armeabiFoo (not doNotStrip files)
        assertThat(execOperations.capturedExecutions).named("number of invocations").hasSize(1)
        val execSpec = execOperations.capturedExecutions.single()
        assertThat(execSpec.executable).isEqualTo(fakeExe.absolutePath)
        val x86FooPath = x86Foo.toRelativeString(inputDir)
        val x86FooOutputFile = File(outputDir, x86FooPath)
        assertThat(execSpec.args)
            .containsExactly(
                "--strip-unneeded", "-o", x86FooOutputFile.toString(), x86Foo.toString()
            )

        // Check that NEW files are in outputDir,
        assertThat(x86FooOutputFile).exists()
        val armeabiDoNotStripPath = armeabiDoNotStrip.toRelativeString(inputDir)
        assertThat(File(outputDir, armeabiDoNotStripPath)).exists()

        // Check that other outputs are *not* in outputDir (since they weren't in changedInputs)
        val x86DoNotStripPath = x86DoNotStrip.toRelativeString(inputDir)
        assertThat(File(outputDir, x86DoNotStripPath)).doesNotExist()
        val armeabiFooPath = armeabiFoo.toRelativeString(inputDir)
        assertThat(File(outputDir, armeabiFooPath)).doesNotExist()
    }
}
