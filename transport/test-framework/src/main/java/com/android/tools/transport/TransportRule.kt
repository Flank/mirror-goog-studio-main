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
package com.android.tools.transport

import com.android.tools.fakeandroid.FakeAndroidDriver
import com.android.tools.fakeandroid.ProcessRunner
import com.android.tools.profiler.proto.Agent.AgentConfig
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common.CommonConfig
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.Transport.DaemonConfig
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.transport.device.DeviceProperties
import com.android.tools.transport.device.SdkLevel
import com.android.tools.transport.device.TransportDaemonRunner
import com.android.tools.transport.device.supportsJvmti
import com.android.tools.transport.grpc.Grpc
import com.google.common.truth.Truth.assertThat
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

/**
 * A JUnit rule which handles management of test ports, processes, and basic rpc calls that are shared
 * between all transport tests.
 */
class TransportRule @JvmOverloads constructor(
        private val activityClass: String,
        val sdkLevel: SdkLevel,
        private val ruleConfig: Config = Config())
    : ExternalResource() {

    companion object {
        const val DUMMY_DEVICE_ID = 1234L
    }

    open class Config {
        open fun initDaemonConfig(daemonConfig: CommonConfig.Builder) {}
        open fun initAgentConfig(agentConfig: AgentConfig.Builder) {}
        open fun onBeforeActivityLaunched(transportRule: TransportRule) {}
    }

    /**
     * Folder to create temporary config files, which will be deleted at the test's end.
     */
    private val temporaryFolder = TemporaryFolder()

    /**
     * A class which provides access to gRPC, allowing tests to send and receive protobuf messages
     * between host and device device.
     */
    lateinit var grpc: Grpc
        private set

    /**
     * The class which talks to the fake Android device and allows us to issue commands directly
     * to it.
     */
    lateinit var androidDriver: FakeAndroidDriver
        private set

    val commonConfig: CommonConfig by lazy {
        CommonConfig.newBuilder()
                .setServiceAddress("$LOCAL_HOST:$serverPort")
                .apply { ruleConfig.initDaemonConfig(this) }
                .build()
    }

    /**
     * The target daemon that sits on the device and handles communication.
     */
    private lateinit var transportDaemon: TransportDaemonRunner

    var pid = -1
        private set

    private var serverPort = 0

    init {
        DeviceProperties("", sdkLevel.toString(), sdkLevel.toString())
                .apply { writeFile() }
    }

    override fun apply(base: Statement, description: Description): Statement {
        return RuleChain.outerRule(temporaryFolder).apply(super.apply(base, description), description)
    }

    override fun before() {
        // Logs in perf-test output to track the sdk level and test start.
        println("Start activity $activityClass with sdk level $sdkLevel")
        startDaemon()
        start(activityClass)
        startAgent()
    }

    override fun after() {
        androidDriver.stop()
        transportDaemon.stop()

        // Logs in perf-test output to track the sdk level and test end.
        println("Finish activity $activityClass with sdk level $sdkLevel")
    }

    /**
     * Function that launches the FakeAndroid framework.
     *
     * This function will wait until the framework has been loaded, at which point it will load the
     * proper dex(es) onto the device and launch the specified activity.
     */
    private fun start(activity: String) {
        val daemonPort = transportDaemon.port
        androidDriver = FakeAndroidDriver(LOCAL_HOST, arrayOf())
        androidDriver.start()
        grpc = Grpc(LOCAL_HOST, daemonPort)

        val supportsJvmti = sdkLevel.supportsJvmti()
        if (supportsJvmti) {
            copyFilesForJvmti()
        }
        val dexesProperty = if (supportsJvmti) SystemProperties.APP_DEXES_JVMTI else SystemProperties.APP_DEXES_NOJVMTI
        System.getProperty(dexesProperty).split(':').forEach { path ->
            androidDriver.loadDex(ProcessRunner.getProcessPathRoot() + path)
        }

        ruleConfig.onBeforeActivityLaunched(this)

        // Load our mock application, and launch our test activity.
        androidDriver.launchActivity(activity)
        // Retrieve the app's pid
        pid = androidDriver.waitForInput(Pattern.compile("(.*)(PID=)(?<result>.*)")).toInt()
    }

    private fun copyFilesForJvmti() {
        System.getProperty(SystemProperties.APP_LIBS).split(':').forEach { path ->
            val from = File(ProcessRunner.getProcessPathRoot() + path)
            // Copy to a file with the same name but in the current directory
            val to = File(from.name)
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** @return an available port to be used by the test framework. */
    private val availablePort: Int
        get() = ServerSocket(0).use { socket -> return socket.localPort }

    /**
     * Helper function to create and serialize AgentConfig for test to use, this is specific to each
     * test.
     */
    private fun buildDaemonConfig(): File {
        val file = temporaryFolder.newFile()
        val outputStream = FileOutputStream(file)
        val config = DaemonConfig.newBuilder().setCommon(commonConfig).build()
        config.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        return file
    }

    /**
     * Starts the daemon process that listens on the port assigned in the agent config file.
     *
     * If the process does not bind to the given port, it is restarted on a new port.
     * Because JVMTI will reuse the port from the agent config, we cannot set zero in the config.
     */
    private fun startDaemon() {
        while (!::transportDaemon.isInitialized || transportDaemon.port == 0) {
            serverPort = availablePort
            val daemonConfig = buildDaemonConfig()
            // Specify a custom root dir to ensure it is writable; otherwise, the daemon defaults
            // to a path which, under bazel tests, is read only
            transportDaemon =
                    TransportDaemonRunner(daemonConfig.absolutePath, "--file_system_root=${temporaryFolder.newFolder("daemon-root")}")
            transportDaemon.start()
        }
    }

    private fun startAgent() {
        if (sdkLevel.supportsJvmti()) {
            val transportStub = TransportServiceGrpc.newBlockingStub(grpc.channel)
            transportStub.execute(
                    Transport.ExecuteRequest.newBuilder()
                            .setCommand(
                                    Commands.Command.newBuilder()
                                            .setType(Commands.Command.CommandType.ATTACH_AGENT)
                                            .setPid(androidDriver.communicationPort)
                                            .setStreamId(DUMMY_DEVICE_ID)
                                            .setAttachAgent(
                                                    Commands.AttachAgent.newBuilder()
                                                            .setAgentLibFileName("libjvmtiagent.so")
                                                            .setAgentConfigPath(buildAgentConfig().absolutePath))
                                            .build())
                            .build())

            // Block until we can verify the agent was fully attached, which takes a while.
            assertThat(androidDriver.waitForInput("Transport agent connected to daemon.")).isTrue()
        }
    }

    private fun buildAgentConfig(): File {
        val file = temporaryFolder.newFile()
        FileOutputStream(file).use { outputStream ->
            val agentConfig: AgentConfig = AgentConfig.newBuilder()
                    .apply { ruleConfig.initAgentConfig(this) }
                    .setCommon(commonConfig)
                    .build()
            agentConfig.writeTo(outputStream)
            outputStream.flush()
        }
        return file
    }
}
