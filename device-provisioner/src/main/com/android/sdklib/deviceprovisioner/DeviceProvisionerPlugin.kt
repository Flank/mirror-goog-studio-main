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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.scope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A component of [DeviceProvisioner] responsible for a particular class of device. */
interface DeviceProvisionerPlugin {

  /**
   * The relative priority of this plugin for claiming devices; the [DeviceProvisioner] will offer a
   * [ConnectedDevice] to plugins with higher [priority] values first.
   */
  val priority: Int

  /**
   * Offers the given [ConnectedDevice] to the plugin. If the plugin accepts, returns true, and adds
   * a new [DeviceHandle] to [devices].
   */
  suspend fun claim(device: ConnectedDevice): Boolean

  /**
   * The [device handles][DeviceHandle] that this [DeviceProvisionerPlugin] is aware of and manages.
   * These may come from a backend service or persistent storage managed by the plugin, or they may
   * be created based on a [ConnectedDevice] provided in [claim].
   */
  val devices: StateFlow<List<DeviceHandle>>

  /**
   * The [device templates][DeviceTemplate] that this [DeviceProvisionerPlugin] is aware of and
   * manages. These may come from a backend service or persistent storage managed by the plugin, or
   * they may be created by a [CreateDeviceTemplateAction].
   */
  val templates: StateFlow<List<DeviceTemplate>>
    get() = MutableStateFlow(emptyList<DeviceTemplate>()).asStateFlow()

  /** A [DeviceAction] that creates a device, typically based on input from the user. */
  val createDeviceAction: CreateDeviceAction?
    get() = null

  /** A [DeviceAction] that creates a template, typically based on input from the user. */
  val createDeviceTemplateAction: CreateDeviceTemplateAction?
    get() = null
}

/** On device disconnection, runs the given block in the [AdbSession] scope. */
fun ConnectedDevice.invokeOnDisconnection(block: suspend () -> Unit) {
  scope.coroutineContext[Job]?.invokeOnCompletion {
    // Run the block in the AdbSession scope, since this CompletionHandler must
    // be fast, non-blocking, and thread-safe, and not throw exceptions.
    session.scope.launch { block() }
  }
}
