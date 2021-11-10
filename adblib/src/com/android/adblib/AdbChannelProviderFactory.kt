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
package com.android.adblib

import com.android.adblib.impl.AdbChannelProviderConnectAddresses
import com.android.adblib.impl.AdbChannelProviderOpenLocalHost
import java.net.InetSocketAddress

private const val DEFAULT_ADB_HOST_PORT = 5037

class AdbChannelProviderFactory {
    companion object {

        fun createOpenLocalHost(
            host: AdbLibHost,
            portSupplier: suspend () -> Int = { DEFAULT_ADB_HOST_PORT }
        ): AdbChannelProvider {
            return AdbChannelProviderOpenLocalHost(host, portSupplier)
        }

        fun createConnectAddresses(
            host: AdbLibHost,
            socketAddressesSupplier: suspend () -> List<InetSocketAddress>
        ): AdbChannelProvider {
            return AdbChannelProviderConnectAddresses(host, socketAddressesSupplier)
        }
    }
}
