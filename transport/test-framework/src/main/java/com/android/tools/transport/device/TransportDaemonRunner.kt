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
package com.android.tools.transport.device

import com.android.tools.fakeandroid.ProcessRunner
import com.android.tools.transport.SystemProperties
import org.junit.Assert.fail
import java.util.regex.Pattern

private val DAEMON_PATH = ProcessRunner.getProcessPath(SystemProperties.TRANSPORT_DAEMON_LOCATION)
private val SERVER_LISTENING = Pattern.compile("(.*)(Server listening on.*port:)(?<result>.*)")

/**
 * Class responsible for starting up (and waiting for) the transport daemon that runs on the
 * device.
 */
class TransportDaemonRunner(
        configFilePath: String,
        vararg processArgs: String)
    : ProcessRunner(DAEMON_PATH, "--config_file=$configFilePath", *processArgs) {

    var port = 0
        private set

    override fun start() {
        port = 0
        super.start()
        if (!isAlive) {
            fail("Failed to start daemon. Exit code: ${exitValue()}")
        }

        val portStr = waitForInput(SERVER_LISTENING, SHORT_TIMEOUT_MS)
        if (portStr == null || portStr.isEmpty()) {
            fail("Failed to start daemon.${if (isAlive) "" else "Exit code: ${exitValue()}"}")
        }
        this.port = portStr!!.toInt()

        if (this.port == 0) {
            stop()
            fail("Failed to bind daemon to port.")
        }
    }
}