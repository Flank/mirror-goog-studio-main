/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.device.internal.adb

import com.android.tools.device.internal.OsProcessRunner
import com.android.tools.device.internal.adb.commands.DaemonFeatures
import com.android.tools.device.internal.adb.commands.HostFeatures
import com.android.tools.device.internal.adb.commands.ListDevices
import com.android.tools.device.internal.adb.commands.ServerVersion
import com.google.common.collect.ImmutableMap
import com.google.common.primitives.UnsignedInteger
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ServerSocket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * This is an integration test for the [AdbServerService] class. It ensures that the service
 * works properly with a real adb instance.
 *
 * It does however test an internal API and will likely be removed or migrated to use the public
 * API when it becomes available.
 */
class AdbServerServiceIntegrationTest {
    companion object {
        lateinit private var logger: Logger
        lateinit private var handler: ConsoleHandler

        /**
         * Sets up the logging system to print out logs at all levels, but in a simplified format. For
         * an integration test, doing this step is entirely optional and the chosen format is entirely
         * subjective.
         */
        @BeforeClass @JvmStatic
        fun setupLogger() {
            logger = Logger.getLogger(AdbServerService::class.java.`package`.name)
            logger.useParentHandlers = false

            handler = ConsoleHandler()
            handler.formatter = object : Formatter() {
                override fun format(record: LogRecord): String {
                    val sb = StringBuilder()
                    sb.append(String.format(Locale.US, "%10d", record.millis % 100000))
                            .append(' ')
                            .append(record.level.name[0])
                            .append(' ')
                            .append(formatMessage(record))
                            .append('\n')
                    record.thrown?.appendTo(sb)
                    return sb.toString()
                }
            }
            handler.level = Level.ALL
            logger.level = Level.ALL
            logger.addHandler(handler)
        }

        @AfterClass @JvmStatic
        fun removeLogger() {
            logger.removeHandler(handler)
        }

        private fun Throwable.appendTo(sb: StringBuilder) {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    printStackTrace(pw)
                    sb.append(sw.toString())
                }
            }
        }
    }

    // When run under the Bazel sandbox, $HOME may not exist. adb however relies on $HOME
    // being present and valid. So we explicitly set $HOME to some temporary folder
    // Note that this means that adb generates a new key every time, and devices will show the
    // authorization dialog every time this is used.
    @Rule @JvmField val temporaryHome = TemporaryFolder()

    @Test
    fun integration() {
        val executor = Executors.newCachedThreadPool()
        val options = AdbServerOptions(findFreePort(), AdbConstants.DEFAULT_HOST, true)
        val launcher = AdbServerLauncher(
                AdbTestUtils.getPathToAdb(),
                OsProcessRunner(executor),
                ImmutableMap.of("HOME", temporaryHome.root.absolutePath))

        val service = AdbServerService(options, launcher, SocketProbe(), executor)
        service.startAsync().awaitRunning()

        try {
            val future = service.execute(ServerVersion())
            assertThat(future.get()).isGreaterThan(UnsignedInteger.valueOf(30))

            val hostFeatures = service.execute(HostFeatures())
            assertThat(hostFeatures.get()).isNotEmpty()
            assertThat(hostFeatures.get()).doesNotContain(AdbFeature.UNKNOWN)

            val deviceHandles = service.execute(ListDevices()).get()
            if (deviceHandles.isEmpty()) {
                logger.info("No devices connected")
                return
            }

            deviceHandles.forEach { handle ->
                logger.info("${handle.serial}: ${handle.connectionState}")
                if (handle.connectionState != ConnectionState.DEVICE) {
                    logger.info("Skipping tests on ${handle.serial} since it is not online")
                    return
                }

                val features = service.execute(DaemonFeatures(handle)).get()
                logger.info("${handle.serial} supports the features: $features")
            }
        } finally {
            service.stopAsync().awaitTerminated()
            executor.shutdownNow()
        }
    }

    // TODO https://code.google.com/p/android/issues/detail?id=221925#c5
    // TODO Two instances of this test may be run in parallel, and we need them to not interact
    // with each other's server instances. Picking a port this way is kludgy because there is
    // no guarantee that the port is still free when it is actually used. Eventually, we should
    // move to the correct fix which is to have the adb server automatically pick up a free
    // port when it starts up.
    private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
}