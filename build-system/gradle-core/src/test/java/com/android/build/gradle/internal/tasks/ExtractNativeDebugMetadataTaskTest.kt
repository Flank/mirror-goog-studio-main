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
import com.android.build.gradle.internal.fixtures.FakeGradleExecOperations
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeInjectableService
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.reflect.jvm.javaMethod

/**
 * Unit tests for [ExtractNativeDebugMetadataTask].
 */
class ExtractNativeDebugMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private val factory=  ProjectFactory.project.objects

    private lateinit var inputDir: File
    private lateinit var strippedNativelibs: File
    private lateinit var x86NativeLib: File
    private lateinit var armeabiNativeLib: File
    private lateinit var outputDir: File
    private lateinit var fakeExe: File
    private lateinit var objcopyExecutableMap: Map<Abi, File>
    private lateinit var workers: WorkerExecutor
    private lateinit var project: Project
    private lateinit var objectFactory: ObjectFactory
    private val fakeExecOperations = FakeGradleExecOperations()

    @Before
    fun setUp() {
        // create input dir with lib/x86/foo.so and lib/armeabi/foo.so,
        inputDir = temporaryFolder.newFolder("inputDir")
        x86NativeLib = FileUtils.join(inputDir, "lib", "x86", "foo.so")
        FileUtils.createFile(x86NativeLib, "foo")
        assertThat(x86NativeLib).exists()
        armeabiNativeLib = FileUtils.join(inputDir, "lib", "armeabi", "foo.so")
        FileUtils.createFile(armeabiNativeLib, "foo")
        assertThat(armeabiNativeLib).exists()

        // create empty strippedNativeLibs dir
        strippedNativelibs = temporaryFolder.newFolder("strippedNativeLibs")

        outputDir = temporaryFolder.newFolder("outputDir")

        fakeExe = temporaryFolder.newFile("fake.exe")
        objcopyExecutableMap = mapOf(Pair(Abi.X86, fakeExe), Pair(Abi.ARMEABI, fakeExe))

        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        objectFactory = project.objects

        workers = FakeGradleWorkExecutor(
            objectFactory, temporaryFolder.newFolder(), listOf(
                FakeInjectableService(
                    ExtractNativeDebugMetadataRunnable::execOperations.getter.javaMethod!!,
                    fakeExecOperations
                )
            )
        )
    }

    @Test
    fun fullTest() {

        object : ExtractNativeDebugMetadataWorkAction() {
            override val workerExecutor = workers

            override fun getParameters(): Parameters {
                return object : Parameters() {
                    override val inputDir = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.inputDir)
                    override val strippedNativeLibs = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.strippedNativelibs)
                    override val outputDir = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.outputDir)
                    override val objcopyExecutableMap =
                        objectFactory.mapProperty(Abi::class.java, File::class.java)
                            .value(this@ExtractNativeDebugMetadataTaskTest.objcopyExecutableMap)
                    override val debugSymbolLevel = FakeGradleProperty(DebugSymbolLevel.FULL)
                    override val maxWorkerCount = FakeGradleProperty(2)
                    override val projectPath = factory.property(String::class.java).value("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                      get() = project.objects.property(AnalyticsService::class.java).also {
                          it.set(FakeNoOpAnalyticsService())
                      }
                }
            }
        }.execute()

        assertThat(fakeExecOperations.capturedExecutions).named("number of process invocations")
            .hasSize(2)
        for (processInfo in fakeExecOperations.capturedExecutions) {
            assertThat(processInfo.executable).isEqualTo(fakeExe.absolutePath)
            val nativeLib = if (processInfo.args.contains(x86NativeLib.toString())) {
                x86NativeLib
            } else {
                armeabiNativeLib
            }
            val path = nativeLib.toRelativeString(File(inputDir, "lib"))
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
    fun fullTestWorkersLessThanFiles() {

        object : ExtractNativeDebugMetadataWorkAction() {
            override val workerExecutor = workers

            override fun getParameters(): Parameters {
                return object : Parameters() {
                    override val inputDir = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.inputDir)
                    override val strippedNativeLibs = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.strippedNativelibs)
                    override val outputDir = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.outputDir)
                    override val objcopyExecutableMap =
                        objectFactory.mapProperty(Abi::class.java, File::class.java)
                            .value(this@ExtractNativeDebugMetadataTaskTest.objcopyExecutableMap)
                    override val debugSymbolLevel = FakeGradleProperty(DebugSymbolLevel.FULL)
                    override val maxWorkerCount = FakeGradleProperty(1)
                    override val projectPath = factory.property(String::class.java).value("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = project.objects.property(AnalyticsService::class.java).also {
                            it.set(FakeNoOpAnalyticsService())
                        }
                }
            }
        }.execute()

        assertThat(fakeExecOperations.capturedExecutions).named("number of process invocations")
            .hasSize(2)
        for (processInfo in fakeExecOperations.capturedExecutions) {
            assertThat(processInfo.executable).isEqualTo(fakeExe.absolutePath)
            val nativeLib = if (processInfo.args.contains(x86NativeLib.toString())) {
                x86NativeLib
            } else {
                armeabiNativeLib
            }
            val path = nativeLib.toRelativeString(File(inputDir, "lib"))
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
        object : ExtractNativeDebugMetadataWorkAction() {
            override val workerExecutor = workers

            override fun getParameters(): Parameters {
                return object : Parameters() {
                    override val inputDir = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.inputDir)
                    override val strippedNativeLibs = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.strippedNativelibs)
                    override val outputDir = objectFactory.directoryProperty()
                        .fileValue(this@ExtractNativeDebugMetadataTaskTest.outputDir)
                    override val objcopyExecutableMap =
                        objectFactory.mapProperty(Abi::class.java, File::class.java)
                            .value(this@ExtractNativeDebugMetadataTaskTest.objcopyExecutableMap)
                    override val debugSymbolLevel =
                        FakeGradleProperty(DebugSymbolLevel.SYMBOL_TABLE)
                    override val maxWorkerCount = FakeGradleProperty(2)
                    override val projectPath = factory.property(String::class.java).value("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = project.objects.property(AnalyticsService::class.java).also {
                            it.set(FakeNoOpAnalyticsService())
                        }
                }
            }
        }.execute()

        assertThat(fakeExecOperations.capturedExecutions).named("number of process invocations")
            .hasSize(2)
        for (processInfo in fakeExecOperations.capturedExecutions) {
            assertThat(processInfo.executable).isEqualTo(fakeExe.absolutePath)
            val nativeLib = if (processInfo.args.contains(x86NativeLib.toString())) {
                x86NativeLib
            } else {
                armeabiNativeLib
            }
            val path = nativeLib.toRelativeString(File(inputDir, "lib"))
            val outputFile = File(outputDir, "$path.sym")
            assertThat(processInfo.args)
                .containsExactly(
                    "--strip-debug",
                    nativeLib.toString(),
                    outputFile.toString()
                )
            // we don't expect the output files to exist because we're running a fake executable
            assertThat(outputFile).doesNotExist()
        }
    }
}
