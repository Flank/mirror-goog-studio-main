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
package com.android.adblib.tools

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

/**
 * An abstraction layer for the many ways the PackageManager service can be accessed on Android.
 * Non-exhaustive list:
 *     - Invoke pm executable
 *     - Invoke cmd executable with "package parameter"
 *     - Invoke abb binder client
 *     - Invoke legacy non-stream support pm executable
 */
internal abstract class PM(val deviceService : AdbDeviceServices) {

    /**
     * Request an install session from the Package Manager.
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [options] the install options. e.g.: {"-t", "-r"}.
     * @param [timeout] the total amount of time allowed to install, including all sub-commands
     */
    abstract suspend fun createSession(device: DeviceSelector, options: List<String>) : Flow<String>

    /**
     * Stream a split APK to the Package Manager.
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [sessionID] the session returned from [createSession].
     * @param [apk] the split/apk to send to the package manager
     * @param [filename] the filename to provide to the package manager upon streaming
     * @param [size] the filesize of [apk]
     */
    abstract suspend fun streamApk(device: DeviceSelector, sessionID: String, apk: Path, filename: String, size: Long) : Flow<String>

    /**
     * Commit the app installation with the Package Manager. The session is destroyed.
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [sessionID] the session returned from [createSession].
     */
    abstract suspend fun commit(device: DeviceSelector, sessionID: String): Flow<String>

    /**
     * Abandon the app installation with the Package Manager. The session is destroyed.
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [sessionID] the session returned from [createSession].
     */
    abstract suspend fun abandon(device: DeviceSelector, sessionID: String): Flow<String>

    /**
     * Inform the called of how the createSession/stream/commit|abandon will executed
     * (a.k.a strategy).
     */
    abstract suspend fun getStrategy() : String
}
