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
package com.android.adblib.tools.debugging

import com.android.adblib.DeviceSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * A JDWP process tracked by [JdwpProcessTracker]. Each instance has a [pid] and a [StateFlow]
 * of [JdwpProcessProperties], corresponding to the changes made to the process during a
 * JDWP session (e.g. [JdwpProcessProperties.packageName]).
 *
 * A [JdwpProcess] instance becomes invalid once the corresponding process on the device
 * is terminated.
 */
interface JdwpProcess {

    /**
     * The device this process runs on.
     */
    val device: DeviceSelector

    /**
     * The process ID
     */
    val pid: Int

    /**
     * The [CoroutineScope] whose lifetime matches the lifetime of the process on the device.
     * This [scope] can be used for example when collecting the [processPropertiesFlow]
     */
    val scope: CoroutineScope

    /**
     * A [StateFlow] that describes the current process information as well
     * as changes over time.
     */
    val processPropertiesFlow: StateFlow<JdwpProcessProperties>
}
