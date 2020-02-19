/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.ide.common.process.ProcessInfo
import com.android.ide.common.process.ProcessResult
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

/**
 * Unit tests for [ExtractNativeDebugMetadataTask].
 */
class ExtractNativeDebugMetadataTaskTest {

    @Mock
    private lateinit var processExecutor: GradleProcessExecutor
    @Mock
    private lateinit var processResult: ProcessResult

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var inputDir: File
    private lateinit var x86NativeLib: File
    private lateinit var armeabiNativeLib: File
    private lateinit var outputDir: File
    private lateinit var fakeExe: File
    private lateinit var objcopyExecutableMap: Map<Abi, File>
    private lateinit var workers: WorkerExecutorFacade

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(processExecutor.execute(any(), any())).thenReturn(processResult)
        Mockito.`when`(processResult.exitValue).thenReturn(1)

        // create input dir with lib/x86/foo.so and lib/armeabi/foo.so,
        inputDir = temporaryFolder.newFolder("inputDir")
        x86NativeLib = FileUtils.join(inputDir, "lib", "x86", "foo.so")
        FileUtils.createFile(x86NativeLib, "foo")
        assertThat(x86NativeLib).exists()
        armeabiNativeLib = FileUtils.join(inputDir, "lib", "armeabi", "foo.so")
        FileUtils.createFile(armeabiNativeLib, "foo")
        assertThat(armeabiNativeLib).exists()

        outputDir = temporaryFolder.newFolder("outputDir")

        fakeExe = temporaryFolder.newFile("fake.exe")
        objcopyExecutableMap = mapOf(Pair(Abi.X86, fakeExe), Pair(Abi.ARMEABI, fakeExe))

        workers = testWorkers
    }

    @Test
    fun fullTest() {
        ExtractNativeDebugMetadataDelegate(
            workers,
            inputDir,
            outputDir,
            objcopyExecutableMap,
            DebugSymbolLevel.FULL,
            processExecutor
        ).run()

        val processInfos = ArgumentCaptor.forClass(ProcessInfo::class.java)
        verify(processExecutor, Mockito.times(2)).execute(processInfos.capture(), any())
        assertThat(processInfos.allValues).hasSize(2)
        for (processInfo in processInfos.allValues) {
            assertThat(processInfo.executable).isEqualTo(fakeExe.absolutePath)
            val nativeLib = if (processInfo.args.contains(x86NativeLib.toString())) {
                x86NativeLib
            } else {
                armeabiNativeLib
            }
            val path = FileUtils.relativePossiblyNonExistingPath(nativeLib, File(inputDir, "lib"))
            val outputFile = File(outputDir, "$path.dbg")
            assertThat(processInfo.args)
                .containsExactly(
                    "--only-keep-debug",
                    nativeLib.toString(),
                    outputFile.toString()
                )
            // we don't expect the output file to exist because we're running a fake executable
            assertThat(outputFile).doesNotExist()
        }
    }

    @Test
    fun symbolTableTest() {
        ExtractNativeDebugMetadataDelegate(
            workers,
            inputDir,
            outputDir,
            objcopyExecutableMap,
            DebugSymbolLevel.SYMBOL_TABLE,
            processExecutor
        ).run()

        val processInfos = ArgumentCaptor.forClass(ProcessInfo::class.java)
        verify(processExecutor, Mockito.times(2)).execute(processInfos.capture(), any())
        assertThat(processInfos.allValues).hasSize(2)
        for (processInfo in processInfos.allValues) {
            assertThat(processInfo.executable).isEqualTo(fakeExe.absolutePath)
            val nativeLib = if (processInfo.args.contains(x86NativeLib.toString())) {
                x86NativeLib
            } else {
                armeabiNativeLib
            }
            val path = FileUtils.relativePossiblyNonExistingPath(nativeLib, File(inputDir, "lib"))
            val outputFile = File(outputDir, "$path.sym")
            assertThat(processInfo.args)
                .containsExactly(
                    "-j",
                    "symtab",
                    "-j",
                    "dynsym",
                    nativeLib.toString(),
                    outputFile.toString()
                )
            // we don't expect the output files to exist because we're running a fake executable
            assertThat(outputFile).doesNotExist()
        }
    }
}
