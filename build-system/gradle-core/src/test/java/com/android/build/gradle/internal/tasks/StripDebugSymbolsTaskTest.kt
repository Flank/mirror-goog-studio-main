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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.stripping.SymbolStripExecutableFinder
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.ide.common.process.ProcessInfo
import com.android.ide.common.process.ProcessResult
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.Serializable

/**
 * Unit tests for [StripDebugSymbolsTask].
 */
class StripDebugSymbolsTaskTest {

    @Mock
    private lateinit var processExecutor: GradleProcessExecutor
    @Mock
    private lateinit var processResult: ProcessResult
    @Mock
    private lateinit var logger: LoggerWrapper

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var inputDir: File
    private lateinit var x86Foo: File
    private lateinit var x86DoNotStrip: File
    private lateinit var armeabiFoo: File
    private lateinit var armeabiDoNotStrip: File
    private lateinit var outputDir: File
    private lateinit var fakeExe: File
    private lateinit var stripToolFinder: SymbolStripExecutableFinder
    private lateinit var workers: WorkerExecutorFacade

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(processExecutor.execute(any(), any())).thenReturn(processResult)
        Mockito.`when`(processResult.exitValue).thenReturn(1)

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
        stripToolFinder =
            SymbolStripExecutableFinder(mapOf(Pair(Abi.X86, fakeExe), Pair(Abi.ARMEABI, fakeExe)))

        workers = object: WorkerExecutorFacade {
            override fun submit(actionClass: Class<out Runnable>, parameter: Serializable) {
                val configuration =
                    WorkerExecutorFacade.Configuration(
                        parameter, WorkerExecutorFacade.IsolationMode.NONE, listOf()
                    )
                val action =
                    actionClass.getConstructor(configuration.parameter.javaClass)
                        .newInstance(configuration.parameter)
                action.run()
            }

            override fun await() {}

            override fun close() {}
        }
    }

    @Test
    fun `test non-incremental`() {
        val excludePatterns = listOf("**/doNotStrip.so")

        StripDebugSymbolsDelegate(
            workers,
            inputDir,
            outputDir,
            excludePatterns,
            stripToolFinder,
            processExecutor,
            null
        ).run()

        // Check that executable only runs for x86Foo and armeabiFoo (not doNotStrip files)
        val processInfos = ArgumentCaptor.forClass(ProcessInfo::class.java)
        verify(processExecutor, Mockito.times(2)).execute(processInfos.capture(), any())
        assertThat(processInfos.allValues).hasSize(2)
        for (processInfo in processInfos.allValues) {
            assertThat(processInfo.executable).isEqualTo(fakeExe.absolutePath)
            if (processInfo.args.contains(x86Foo.toString())) {
                val path = FileUtils.relativePossiblyNonExistingPath(x86Foo, inputDir)
                val outputFile = File(outputDir, path)
                assertThat(processInfo.args)
                    .containsExactly(
                        "--strip-unneeded", "-o", outputFile.toString(), x86Foo.toString()
                    )
            } else {
                val path = FileUtils.relativePossiblyNonExistingPath(armeabiFoo, inputDir)
                val outputFile = File(outputDir, path)
                assertThat(processInfo.args)
                    .containsExactly(
                        "--strip-unneeded", "-o", outputFile.toString(), armeabiFoo.toString()
                    )
            }
        }

        // Check that all files are in outputDir, even doNotStrip files
        val x86FooPath = FileUtils.relativePossiblyNonExistingPath(x86Foo, inputDir)
        assertThat(File(outputDir, x86FooPath)).exists()
        val x86DoNotStripPath = FileUtils.relativePossiblyNonExistingPath(x86DoNotStrip, inputDir)
        assertThat(File(outputDir, x86DoNotStripPath)).exists()
        val armeabiFooPath = FileUtils.relativePossiblyNonExistingPath(armeabiFoo, inputDir)
        assertThat(File(outputDir, armeabiFooPath)).exists()
        val armeabiDoNotStripPath =
            FileUtils.relativePossiblyNonExistingPath(armeabiDoNotStrip, inputDir)
        assertThat(File(outputDir, armeabiDoNotStripPath)).exists()
    }

    @Test
    fun `test incremental`() {

        val changedInputs =
            mapOf(Pair(x86Foo, FileStatus.NEW), Pair(armeabiDoNotStrip, FileStatus.NEW))

        val excludePatterns = listOf("**/doNotStrip.so")

        StripDebugSymbolsDelegate(
            workers,
            inputDir,
            outputDir,
            excludePatterns,
            stripToolFinder,
            processExecutor,
            changedInputs
        ).run()

        // Check that executable only runs for x86Foo
        val processInfos = ArgumentCaptor.forClass(ProcessInfo::class.java)
        verify(processExecutor, Mockito.times(1)).execute(processInfos.capture(), any())
        assertThat(processInfos.value.executable).isEqualTo(fakeExe.absolutePath)
        val x86FooPath = FileUtils.relativePossiblyNonExistingPath(x86Foo, inputDir)
        val x86FooOutputFile = File(outputDir, x86FooPath)
        assertThat(processInfos.value.args)
            .containsExactly(
                "--strip-unneeded", "-o", x86FooOutputFile.toString(), x86Foo.toString()
            )

        // Check that NEW files are in outputDir,
        assertThat(x86FooOutputFile).exists()
        val armeabiDoNotStripPath =
            FileUtils.relativePossiblyNonExistingPath(armeabiDoNotStrip, inputDir)
        assertThat(File(outputDir, armeabiDoNotStripPath)).exists()

        // Check that other outputs are *not* in outputDir (since they weren't in changedInputs)
        val x86DoNotStripPath = FileUtils.relativePossiblyNonExistingPath(x86DoNotStrip, inputDir)
        assertThat(File(outputDir, x86DoNotStripPath)).doesNotExist()
        val armeabiFooPath = FileUtils.relativePossiblyNonExistingPath(armeabiFoo, inputDir)
        assertThat(File(outputDir, armeabiFooPath)).doesNotExist()
    }
}
