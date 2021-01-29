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

package com.android.tools.utp.plugins.host.icebox

import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto.Compression
import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto.IceboxPlugin as IceboxPluginConfig
import com.android.tools.utp.plugins.host.icebox.proto.IceboxOutputProto.IceboxOutput
import com.google.common.annotations.VisibleForTesting
import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream

/**
 * Implementation of the Icebox plugin. Used to trigger Icebox commands.
 *
 * Icebox is a tool to take emulator snapshot on test failures, which can be played back and
 * connected debuggers to.
 *
 * In implementation, icebox does 4 things:
 * (1) Appending -debug flag to the instrumentation command. (Currently done manually.)
 * (2) Querying pid after test starts.
 * (3) Sending an icebox gRPC command to the emulator.
 * (4) (TODO) On test failure, sending an gRPC command to the emulator to retrieve the emulator test
 * failure snapshot.
 */
class IceboxPlugin @VisibleForTesting constructor(
        private val iceboxCallerFactory:
            (ManagedChannelBuilder<*>, String, CoroutineScope) -> IceboxCaller
) : HostPlugin {
    /** No-arg primary constructor for [AutoService] */
    constructor() : this({ mcb, token, cs -> IceboxCaller(mcb, token, cs) })
    private companion object {
        @JvmStatic
        val logger = getLogger()
        const val defaultAndroidStudioDdmlibPort = 8599
        const val snapshotNamePrefix = "failure"
    }

    private lateinit var iceboxCaller: IceboxCaller
    private lateinit var deviceController: DeviceController
    private var androidStudioDdmlibPort = defaultAndroidStudioDdmlibPort
    @VisibleForTesting
    lateinit var iceboxPluginConfig: IceboxPluginConfig
    @VisibleForTesting
    lateinit var outputDir: File
    private var maxSnapshotNumber = 0
    private var remainSnapshotNumber = 0
    private var failureSnapshotId = 0
    private var printedWarning = false

    override fun configure(config: Config) {
        iceboxPluginConfig = IceboxPluginConfig.parseFrom((config as ProtoConfig).configProto!!.value)
        androidStudioDdmlibPort = iceboxPluginConfig.androidStudioDdmlibPort
        if (androidStudioDdmlibPort == 0) {
            androidStudioDdmlibPort = defaultAndroidStudioDdmlibPort
        }
        // The internal gRPC protobuf use max_snapshot_number=-1 for unlimited snapshot, while the
        // external Icebox config uses skipSnapshot=false and max_snapshot_number=0.
        if (iceboxPluginConfig.skipSnapshot) {
            maxSnapshotNumber = 0
        } else if (iceboxPluginConfig.maxSnapshotNumber <= 0) {
            maxSnapshotNumber = Int.MAX_VALUE
        } else {
            maxSnapshotNumber = iceboxPluginConfig.maxSnapshotNumber
        }
        remainSnapshotNumber = maxSnapshotNumber
        outputDir = File(config.environment.outputDirectory)
    }

    /** Creates the IceboxCaller, takes no actions on the device. */
    override fun beforeAll(deviceController: DeviceController) {
        this.deviceController = deviceController
        printedWarning = false
        iceboxCaller = iceboxCallerFactory(
                ManagedChannelBuilder.forAddress(
                        iceboxPluginConfig.emulatorGrpcAddress,
                        iceboxPluginConfig.emulatorGrpcPort
                ).usePlaintext(),
                iceboxPluginConfig.emulatorGrpcToken,
                CoroutineScope(Dispatchers.Default)
        )
    }

    /** Starts Icebox. */
    override fun beforeEach(
            testCase: TestCaseProto.TestCase?,
            deviceController: DeviceController
    ) {
        failureSnapshotId = 0
        iceboxCaller.runIcebox(
                deviceController,
                iceboxPluginConfig.appPackage,
                snapshotNamePrefix,
                remainSnapshotNumber,
                androidStudioDdmlibPort
        )
    }

    /** Finishes the icebox snapshot and saves it on [testResult]. */
    override fun afterEach(
            testResult: TestResult,
            deviceController: DeviceController
    ): TestResult {
        if (testResult.testStatus != TestStatus.FAILED
                || remainSnapshotNumber <= 0) {
            if (testResult.testStatus == TestStatus.FAILED && !printedWarning) {
                logger.warning("Number of failures exceeds maximum snapshot "
                    + "count. Only the first $maxSnapshotNumber failure(s) will "
                    + "have retention snapshots.")
                printedWarning = true
            }
            return testResult
        }
        val testClass = testResult.testCase.testClass
        val testMethod = testResult.testCase.testMethod
        val ext = when (iceboxPluginConfig.snapshotCompression) {
            Compression.NONE -> "tar"
            Compression.TARGZ -> "tar.gz"
            Compression.UNRECOGNIZED -> "tar"
        }
        val emulatorSnapshotName = "${snapshotNamePrefix}$failureSnapshotId"
        val snapshotFile = File(
                outputDir.absolutePath,
                "snapshot-$testClass-$testMethod-$emulatorSnapshotName.$ext"
        )
        iceboxCaller.fetchSnapshot(
                snapshotFile,
                iceboxPluginConfig.snapshotCompression,
                emulatorSnapshotName
        )
        // We need to count the failures so that we know which snapshot it corresponds to.
        failureSnapshotId = failureSnapshotId + 1

        // Add the artifact to testResult
        if (snapshotFile.exists()) {
            remainSnapshotNumber = remainSnapshotNumber - 1
            val iceboxInfo = IceboxOutput.newBuilder()
                    .setAppPackage(iceboxPluginConfig.appPackage)
                    .build()
            val iceboxInfoFile = File(outputDir, "icebox-info-$testClass-$testMethod-$emulatorSnapshotName.pb")
            FileOutputStream(iceboxInfoFile).use {
                iceboxInfo.writeTo(it)
            }
            return testResult.toBuilder().apply {
                addOutputArtifact(
                    TestArtifactProto.Artifact.newBuilder().apply {
                        labelBuilder.label = "icebox.info"
                        labelBuilder.namespace = "android"
                        sourcePathBuilder.path = iceboxInfoFile.getPath()
                    }
                )
                addOutputArtifact(
                    TestArtifactProto.Artifact.newBuilder().apply {
                        labelBuilder.label = "icebox.snapshot"
                        labelBuilder.namespace = "android"
                        sourcePathBuilder.path = snapshotFile.getPath()
                    }
                )
            }.build()
        } else {
            return testResult
        }
    }

    /** Shuts down the Icebox service. */
    override fun afterAll(
            testSuiteResult: TestSuiteResult,
            deviceController: DeviceController
    ): TestSuiteResult {
        iceboxCaller.shutdownGrpc()
        return testSuiteResult
    }

    override fun canRun(): Boolean = true
}
