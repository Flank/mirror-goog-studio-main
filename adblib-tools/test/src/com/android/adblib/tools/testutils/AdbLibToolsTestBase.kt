/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.testutils

import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbHostServices
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbSession
import com.android.adblib.SOCKET_CONNECT_TIMEOUT_MS
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbLibHost
import com.android.fakeadbserver.DeviceState
import org.junit.Rule
import org.junit.rules.ExpectedException
import java.time.Duration

open class AdbLibToolsTestBase {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    protected fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    protected fun createDeviceServices(fakeAdb: FakeAdbServerProvider): AdbDeviceServices {
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val session =
            AdbSession.create(
                host,
                channelProvider,
                Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
            )
        return session.deviceServices
    }

    protected fun addFakeDevice(fakeAdb: FakeAdbServerProvider, api: Int): DeviceState {
        val fakeDevice =
            fakeAdb.connectDevice(
                "1234",
                "test1",
                "test2",
                "model",
                api.toString(),
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return fakeDevice
    }

    internal fun createHostServices(fakeAdb: FakeAdbServerProvider): AdbHostServices {
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val session = registerCloseable(createSession(host, channelProvider))
        return session.hostServices
    }

    private fun createSession(
        host: AdbLibHost,
        channelProvider: AdbChannelProvider
    ): AdbSession {
        return AdbSession.create(
            host,
            channelProvider,
            Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
        )

    }
}
