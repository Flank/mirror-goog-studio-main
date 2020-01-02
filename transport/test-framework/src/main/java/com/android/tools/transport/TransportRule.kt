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
import com.android.tools.profiler.proto.Common.CommonConfig
import com.android.tools.profiler.proto.Transport.DaemonConfig
import com.android.tools.transport.device.DeviceProperties
import com.android.tools.transport.device.SdkLevel
import com.android.tools.transport.device.TransportDaemonRunner
import com.android.tools.transport.device.supportsJvmti
import com.android.tools.transport.grpc.Grpc
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

private const val LOCAL_HOST = "127.0.0.1"

/**
 * A JUnit rule which handles management of test ports, processes, and basic rpc calls that are shared
 * between all transport tests.
 */

class TransportRule(private val activityClass: String, private val sdkLevel: SdkLevel) : ExternalResource() {

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

    /**
     * The target daemon that sits on the device and handles communication.
     */
    private lateinit var transportDaemon: TransportDaemonRunner

    /**
     * Folder to create temporary config files, which will be deleted at the test's end.
     */
    private val temporaryFolder = TemporaryFolder()

    private var pid = -1
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
        val isJvmtiDevice = sdkLevel.supportsJvmti()
        if (isJvmtiDevice) {
            copyFilesForJvmti()
        }
        val daemonPort = transportDaemon.port
        androidDriver = FakeAndroidDriver(LOCAL_HOST, arrayOf())
        androidDriver.start()
        grpc = Grpc(LOCAL_HOST, daemonPort)
        androidDriver.setProperty("transport.service.address", "${LOCAL_HOST}:$daemonPort")

        val dexesProperty = if (isJvmtiDevice) SystemProperties.APP_DEXES_JVMTI else SystemProperties.APP_DEXES_NOJVMTI
        System.getProperty(dexesProperty).split(':').forEach { path ->
            androidDriver.loadDex(ProcessRunner.getProcessPathRoot() + path)
        }

        // Load our mock application, and launch our test activity.
        androidDriver.launchActivity(activity)
        // Retrieve the app's pid
        pid = androidDriver.waitForInput(Pattern.compile("(.*)(PID=)(?<result>.*)")).toInt()
    }

    private fun copyFilesForJvmti() {
        val libJvmtiAgentFile = File(ProcessRunner.getProcessPath(SystemProperties.TRANSPORT_AGENT_LOCATION))
        Files.copy(
                libJvmtiAgentFile.toPath(),
                File("./libjvmtiagent.so").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
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
        val config = DaemonConfig.newBuilder().setCommon(buildCommonConfig()).build()
        config.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        return file
    }

    private fun buildCommonConfig(): CommonConfig {
        return CommonConfig.newBuilder()
                .setServiceAddress("$LOCAL_HOST:$serverPort")
                .build()
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
            transportDaemon = TransportDaemonRunner(daemonConfig.absolutePath)
            transportDaemon.start()
        }
    }
}
