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

import com.android.build.api.variant.impl.TestVariantImpl
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.ManagedVirtualDeviceLockManager
import com.android.build.gradle.internal.dsl.EmulatorSnapshots
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.testing.utp.ManagedDeviceTestRunner
import com.android.build.gradle.internal.testing.utp.RetentionConfig
import com.android.build.gradle.internal.testing.utp.UtpDependencies
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.TestOptions
import com.android.repository.Revision
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.SystemPropertyOverrides
import com.android.utils.Environment
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.logging.Level
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.CALLS_REAL_METHODS
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

class ManagedDeviceInstrumentationTestTaskTest {
    private lateinit var mockVersionedSdkLoader: VersionedSdkLoader

    private lateinit var emulatorFile: File
    private lateinit var avdFolder: File
    private lateinit var resultsFolder: File
    private lateinit var codeCoverage: File
    private lateinit var reportsFolder: File

    @Mock
    lateinit var emulatorDirectory: Directory
    @Mock
    lateinit var avdDirectory: Directory
    @Mock
    lateinit var resultsDirectory: Directory
    @Mock
    lateinit var coverageDirectory: Directory
    @Mock
    lateinit var reportsDirectory: Directory

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock
    lateinit var avdService: AvdComponentsBuildService

    @Mock
    lateinit var sdkService: SdkComponentsBuildService

    @Mock(answer = RETURNS_DEEP_STUBS)
    lateinit var creationConfig: TestVariantImpl

    @Mock(answer = RETURNS_DEEP_STUBS)
    lateinit var globalConfig: GlobalTaskCreationConfigImpl

    @Mock(answer = RETURNS_DEEP_STUBS)
    lateinit var testData: AbstractTestDataImpl

    @Mock
    lateinit var runnerFactory: ManagedDeviceInstrumentationTestTask.TestRunnerFactory

    @Mock
    lateinit var intallOptions: ListProperty<String>

    @Mock
    lateinit var dependencies: ArtifactCollection

    private lateinit var project: Project
    private lateinit var workerExecutor: WorkerExecutor

    @Before
    fun setup() {
        Environment.initialize()

        `when`(creationConfig.computeTaskName(any(), any())).then {
            val prefix = it.getArgument<String>(0)
            val suffix = it.getArgument<String>(0)
            "${prefix}AndroidDebugTest$suffix"
        }
        `when`(creationConfig.name).thenReturn("AndroidDebugTest")

        // Setup Build Services for configuration.
        val mockGeneralRegistration = mock(BuildServiceRegistration::class.java, RETURNS_DEEP_STUBS)
        `when`(creationConfig.services.buildServiceRegistry.registrations.getByName(any()))
            .thenReturn(mockGeneralRegistration)

        mockVersionedSdkLoader = mock(VersionedSdkLoader::class.java)
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(false)

        `when`(sdkService.sdkLoader(any(), any())).thenReturn(mockVersionedSdkLoader)

        emulatorFile = temporaryFolderRule.newFolder("emulator")
        `when`(emulatorDirectory.asFile).thenReturn(emulatorFile)
        `when`(avdService.emulatorDirectory).thenReturn(FakeGradleProvider(emulatorDirectory))

        avdFolder = temporaryFolderRule.newFolder("gradle/avd")
        `when`(avdDirectory.asFile).thenReturn(avdFolder)
        `when`(avdService.avdFolder).thenReturn(FakeGradleProvider(avdDirectory))

        val lockManager = mock(ManagedVirtualDeviceLockManager::class.java)
        val lock = mock(ManagedVirtualDeviceLockManager.DeviceLock::class.java)
        `when`(lock.lockCount).thenReturn(1)
        `when`(lockManager.lock(any())).thenReturn(lock)
        `when`(avdService.lockManager).thenReturn(lockManager)

        reportsFolder = temporaryFolderRule.newFolder("reports")
        `when`(reportsDirectory.asFile).thenReturn(reportsFolder)

        resultsFolder = temporaryFolderRule.newFolder("results")
        `when`(resultsDirectory.asFile).thenReturn(resultsFolder)

        codeCoverage = temporaryFolderRule.newFolder("coverage")
        `when`(coverageDirectory.asFile).thenReturn(codeCoverage)

        project = ProjectBuilder.builder().withProjectDir(temporaryFolderRule.newFolder()).build()
        workerExecutor = FakeGradleWorkExecutor(project.objects, temporaryFolderRule.newFolder())
    }

    private fun <T> mockEmptyProperty(): Property<T> {
        @Suppress("UNCHECKED_CAST")
        return mock(Property::class.java) as Property<T>
    }

    private fun mockDirectoryProperty(directory: Directory): DirectoryProperty {
        val property = mock(DirectoryProperty::class.java)
        `when`(property.get()).thenReturn(directory)
        return property
    }

    private fun basicTaskSetup(): ManagedDeviceInstrumentationTestTask {

        val task = mock(
            ManagedDeviceInstrumentationTestTask::class.java,
            CALLS_REAL_METHODS)

        doReturn(FakeGradleProperty(mock(AnalyticsService::class.java)))
            .`when`(task).analyticsService
        doReturn(FakeGradleProperty("project_path")).`when`(task).projectPath

        doReturn("path").`when`(task).path
        doReturn(mock(TaskOutputsInternal::class.java, RETURNS_DEEP_STUBS))
            .`when`(task).outputs
        doReturn(MockitoKt.mock<Logger>()).`when`(task).logger

        doReturn(runnerFactory).`when`(task).testRunnerFactory
        doReturn(FakeGradleProperty(avdService)).`when`(runnerFactory).avdComponents
        doReturn(FakeGradleProperty(testData)).`when`(task).testData
        doReturn(mock(ListProperty::class.java)).`when`(task).installOptions
        doReturn(FakeGradleProperty("testDevice1")).`when`(task).deviceName
        doReturn(FakeGradleProperty("avd_for_test_device")).`when`(task).avdName
        doReturn(FakeGradleProperty(29)).`when`(task).apiLevel
        doReturn(FakeGradleProperty("x86")).`when`(task).abi
        doReturn(FakeGradleProperty(false)).`when`(task).enableEmulatorDisplay
        doReturn(FakeGradleProperty(false)).`when`(task).getAdditionalTestOutputEnabled()
        doReturn(dependencies).`when`(task).dependencies

        doReturn(FakeGradleProperty(true))
            .`when`(testData).hasTests(any(), any(), any())
        doReturn(FakeGradleProperty("flavor_name"))
            .`when`(testData).flavorName

        val buddyApks = mock(ConfigurableFileCollection::class.java)
        `when`(buddyApks.files).thenReturn(setOf())
        `when`(task.buddyApks).thenReturn(buddyApks)

        `when`(task.installOptions).thenReturn(intallOptions)
        `when`(intallOptions.getOrElse(any())).thenReturn(listOf())

        doReturn(mockDirectoryProperty(resultsDirectory)).`when`(task).resultsDir
        doReturn(mockDirectoryProperty(coverageDirectory)).`when`(task).getCoverageDirectory()
        doReturn(mockDirectoryProperty(reportsDirectory)).`when`(task).getReportsDir()

        doReturn(workerExecutor).`when`(task).workerExecutor

        return task
    }

    @Test
    fun testRunnerFactory_testCreateTestRunner() {
        val factory = mock(
            ManagedDeviceInstrumentationTestTask.TestRunnerFactory::class.java,
            CALLS_REAL_METHODS)

        `when`(factory.unifiedTestPlatform).thenReturn(FakeGradleProperty(true))
        `when`(factory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))
        `when`(factory.retentionConfig)
            .thenReturn(FakeGradleProperty(mock(RetentionConfig::class.java)))
        `when`(factory.compileSdkVersion).thenReturn(FakeGradleProperty("sdkVersion"))
        `when`(factory.buildToolsRevision)
            .thenReturn(FakeGradleProperty(mock(Revision::class.java)))
        `when`(factory.testShardsSize).thenReturn(FakeGradleProperty(null))
        `when`(factory.sdkBuildService).thenReturn(FakeGradleProperty(sdkService))
        `when`(factory.avdComponents).thenReturn(FakeGradleProperty(avdService))
        `when`(factory.utpDependencies).thenReturn(mock(UtpDependencies::class.java))
        `when`(factory.utpLoggingLevel)
            .thenReturn(FakeGradleProperty(Level.OFF))
        `when`(factory.emulatorGpuFlag).thenReturn(FakeGradleProperty("auto-no-window"))
        `when`(factory.showEmulatorKernelLoggingFlag).thenReturn(FakeGradleProperty(false))

        val testRunner = factory.createTestRunner(workerExecutor, null)
        assertThat(testRunner).isInstanceOf(ManagedDeviceTestRunner::class.java)

        // If Utp is not enabled, then the factory should fail
        `when`(factory.unifiedTestPlatform).thenReturn(FakeGradleProperty(false))

        try {
            factory.createTestRunner(workerExecutor, null)

            assert(false)
        } catch (e: IllegalArgumentException) {
            assertThat(e.message)
                .isEqualTo(
                    "android.experimental.androidTest.useUnifiedTestPlatform must be enabled.")
        }
    }

    @Test
    fun creationConfig_testTaskConfiguration() {
        try {
            // Need to use a custom set up environment to ensure deterministic behavior.
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                // This will ensure the config believes we are running on an x86_64 Linux machine.
                // This will guarantee the x86 system-image is selected.
                systemPropertyOverrides.setProperty("os.name", "Linux")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86_64" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86_64")

                // Parameters for config class.
                val resultsDir = temporaryFolderRule.newFolder("resultsDir")
                val reportsDir = temporaryFolderRule.newFolder("reportsDir")
                val additionalTestOutputDir = temporaryFolderRule.newFolder("additionalTestOutputDir")
                val coverageOutputDir = temporaryFolderRule.newFolder("coverageOutputDir")
                val device = ManagedVirtualDevice("someNameHere").also {
                    it.device = "Pixel 2"
                    it.apiLevel = 27
                    it.systemImageSource = "aosp"
                }
                // Needed for cast from api class to internal class
                val snapshots = mock(EmulatorSnapshots::class.java)
                `when`(creationConfig.global.testOptions.emulatorSnapshots).thenReturn(snapshots)
                // Needed to ensure that UTP is active
                `when`(
                    creationConfig.services
                        .projectOptions[BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM])
                    .thenReturn(true)
                // Needed to ensure the ExecutionEnum
                `when`(creationConfig.global.testOptionExecutionEnum)
                    .thenReturn(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR)
                val config = ManagedDeviceInstrumentationTestTask.CreationAction(
                    creationConfig,
                    device,
                    testData,
                    resultsDir,
                    reportsDir,
                    additionalTestOutputDir,
                    coverageOutputDir,
                    ""
                )

                val task =
                    mock(ManagedDeviceInstrumentationTestTask::class.java, RETURNS_DEEP_STUBS)

                // We need to create mock properties to verify/capture values in the task as
                // RETURNS_DEEP_STUBS does not work as expected with verify. Also, we can't use
                // FakeGradleProperties because they do not support disallowChanges().

                val unifiedTestPlatform = mockEmptyProperty<Boolean>()
                `when`(task.testRunnerFactory.unifiedTestPlatform).thenReturn(unifiedTestPlatform)
                val executionEnum = mockEmptyProperty<TestOptions.Execution>()
                `when`(task.testRunnerFactory.executionEnum).thenReturn(executionEnum)
                val sdkBuildService = mockEmptyProperty<SdkComponentsBuildService>()
                `when`(task.testRunnerFactory.sdkBuildService).thenReturn(sdkBuildService)
                val avdComponents = mockEmptyProperty<AvdComponentsBuildService>()
                `when`(task.testRunnerFactory.avdComponents).thenReturn(avdComponents)

                val deviceName = mockEmptyProperty<String>()
                `when`(task.deviceName).thenReturn(deviceName)
                val avdName = mockEmptyProperty<String>()
                `when`(task.avdName).thenReturn(avdName)
                val apiLevel = mockEmptyProperty<Int>()
                `when`(task.apiLevel).thenReturn(apiLevel)
                val abi = mockEmptyProperty<String>()
                `when`(task.abi).thenReturn(abi)

                config.configure(task)

                verify(unifiedTestPlatform).set(true)
                verify(unifiedTestPlatform).disallowChanges()
                verifyNoMoreInteractions(unifiedTestPlatform)

                verify(executionEnum).set(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR)
                verify(executionEnum).disallowChanges()
                verifyNoMoreInteractions(executionEnum)

                verify(sdkBuildService).set(any<Provider<SdkComponentsBuildService>>())
                verify(sdkBuildService).disallowChanges()
                verifyNoMoreInteractions(sdkBuildService)

                verify(deviceName).set("someNameHere")
                verify(deviceName).disallowChanges()
                verifyNoMoreInteractions(deviceName)

                // Should be x86, as require64Bit is not set and api is 27
                verify(avdName).set("dev27_default_x86_Pixel_2")
                verify(avdName).disallowChanges()
                verifyNoMoreInteractions(avdName)

                verify(apiLevel).set(27)
                verify(apiLevel).disallowChanges()
                verifyNoMoreInteractions(apiLevel)

                verify(abi).set("x86")
                verify(abi).disallowChanges()
                verifyNoMoreInteractions(abi)

                verify(avdComponents).set(any<Provider<AvdComponentsBuildService>>())
                verify(avdComponents).disallowChanges()
                verifyNoMoreInteractions(avdComponents)
            }
        } finally {
            Environment.instance = Environment.SYSTEM
        }
    }

    @Test
    fun taskAction_basicTaskPath() {
        val task = basicTaskSetup()

        val testRunner = mock(ManagedDeviceTestRunner::class.java)
        doReturn(true).`when`(testRunner).runTests(
            managedDevice = any(),
            outputDirectory = any(),
            coverageOutputDirectory = any(),
            additionalTestOutputDir = eq(null),
            projectPath = any(),
            variantName = any(),
            testData = any(),
            additionalInstallOptions = any(),
            helperApks = any(),
            logger = any()
        )
        println("TestRunner: $testRunner")

        doReturn(FakeGradleProperty<Int>()).`when`(runnerFactory).testShardsSize
        doReturn(testRunner).`when`(runnerFactory).createTestRunner(any(), eq(null))
        `when`(runnerFactory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))

        task.doTaskAction()

        verify(testRunner).runTests(
            managedDevice = argThat {
                it.deviceName == "testDevice1"
                        && it.avdName == "avd_for_test_device"
                        && it.api == 29
                        && it.abi == "x86"
            },
            outputDirectory = eq(resultsFolder),
            coverageOutputDirectory = eq(codeCoverage),
            additionalTestOutputDir = eq(null),
            projectPath = eq("project_path"),
            variantName = eq("flavor_name"),
            testData = any(),
            additionalInstallOptions = eq(listOf()),
            helperApks = any(),
            logger = any()
        )
        verifyNoMoreInteractions(testRunner)

        assertThat(task.getTestFailed()).isFalse()
    }

    @Test
    fun taskAction_testFailuresPath() {
        val task = basicTaskSetup()

        val testRunner = mock(ManagedDeviceTestRunner::class.java)
        doReturn(false).`when`(testRunner).runTests(
            managedDevice = any(),
            outputDirectory = any(),
            coverageOutputDirectory = any(),
            additionalTestOutputDir = eq(null),
            projectPath = any(),
            variantName = any(),
            testData = any(),
            additionalInstallOptions = any(),
            helperApks = any(),
            logger = any()
        )

        doReturn(FakeGradleProperty<Int>()).`when`(runnerFactory).testShardsSize

        doReturn(testRunner).`when`(runnerFactory).createTestRunner(any(), eq(null))
        `when`(runnerFactory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))

        task.setIgnoreFailures(false)

        try {
            task.doTaskAction()

            error("Should not reach.")
        } catch (e: GradleException) {
            assertThat(e.message).startsWith("There were failing tests")
        }

        verify(testRunner).runTests(
            managedDevice = argThat {
                it.deviceName == "testDevice1"
                        && it.avdName == "avd_for_test_device"
                        && it.api == 29
                        && it.abi == "x86"
            },
            outputDirectory = eq(resultsFolder),
            coverageOutputDirectory = eq(codeCoverage),
            additionalTestOutputDir = eq(null),
            projectPath = eq("project_path"),
            variantName = eq("flavor_name"),
            testData = any(),
            additionalInstallOptions = eq(listOf()),
            helperApks = any(),
            logger = any()
        )
        verifyNoMoreInteractions(testRunner)

        assertThat(task.getTestFailed()).isTrue()
    }

    @Test
    fun taskAction_noTestsPath() {
        val task = basicTaskSetup()

        val testRunner = mock(ManagedDeviceTestRunner::class.java)
        doReturn(false).`when`(testRunner).runTests(
            managedDevice = any(),
            outputDirectory = any(),
            coverageOutputDirectory = any(),
            additionalTestOutputDir = eq(null),
            projectPath = any(),
            variantName = any(),
            testData = any(),
            additionalInstallOptions = any(),
            helperApks = any(),
            logger = any()
        )

        doReturn(FakeGradleProperty<Int>()).`when`(runnerFactory).testShardsSize

        doReturn(testRunner).`when`(runnerFactory).createTestRunner(any(), eq(null))
        `when`(runnerFactory.executionEnum)
            .thenReturn(FakeGradleProperty(TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR))

        // When the data has no Tests, the testRunner should not be run.
        doReturn(FakeGradleProperty(false))
            .`when`(testData).hasTests(any(), any(), any())

        task.doTaskAction()

        verifyNoInteractions(testRunner)

        assertThat(task.getTestFailed()).isFalse()
    }
}
