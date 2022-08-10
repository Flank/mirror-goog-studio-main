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
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.deviceInfo
import com.android.adblib.thisLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Central access point for devices and device templates. [DeviceProvisionerPlugin] instances
 * provide collections of [DeviceHandle] and [DeviceTemplate] instances, which are collected by this
 * class and provided to consumers.
 */
class DeviceProvisioner
private constructor(
  private val adbSession: AdbSession,
  private val provisioners: List<DeviceProvisionerPlugin>
) {
  companion object {
    fun create(adbSession: AdbSession, provisioners: List<DeviceProvisionerPlugin>) =
      DeviceProvisioner(
        adbSession,
        (provisioners + DefaultProvisionerPlugin()).sortedByDescending { it.priority }
      )
  }

  private val logger = thisLogger(adbSession)

  private val combinedDevices = combine(provisioners.map { it.devices }) { it.flatMap { it } }
  private val combinedTemplates = combine(provisioners.map { it.templates }) { it.flatMap { it } }

  /** The [device handles][DeviceHandle] known to this class, provided by its plugins. */
  val devices: StateFlow<List<DeviceHandle>> =
    combinedDevices.stateIn(adbSession.scope, SharingStarted.Eagerly, emptyList())

  /** The [device templates][DeviceTemplate] known to this class, provided by its plugins. */
  val templates: StateFlow<List<DeviceTemplate>> =
    combinedTemplates.stateIn(adbSession.scope, SharingStarted.Eagerly, emptyList())

  init {
    // Track changes in ADB-connected devices
    adbSession.scope.launch {
      // We want to update whenever the ConnectedDevicesTracker updates, or when one of the
      // devices' deviceInfoFlow updates (to know when the device comes online, since it will
      // often appear initially in an offline state, and we ignore offline devices).
      adbSession.connectedDevicesTracker.connectedDevices
        .flatMapLatest { devices ->
          combine(devices.map { device -> device.deviceInfoFlow.map { Pair(device, it) } }) {
            it.mapNotNull { (device, info) ->
              when (info.deviceState) {
                com.android.adblib.DeviceState.ONLINE -> device
                else -> null
              }
            }
          }
        }
        .collect(::updateDevicesFromAdb)
    }
  }

  /** Indicates that the list of devices connected to ADB has changed. */
  private suspend fun updateDevicesFromAdb(connectedDevices: List<ConnectedDevice>) {
    // Do we have any new devices that we need to identify?
    val currentConnectedDevices = devices.value.mapNotNull { it.state.connectedDevice }.toSet()
    (connectedDevices - currentConnectedDevices).forEach { newDevice ->
      provisioners.firstOrNull {
        try {
          it.claim(newDevice)
        } catch (t: Throwable) {
          logger.error(t, "Offering ${newDevice.deviceInfo.serialNumber}")
          false
        }
      }
    }

    // We don't actually update the states here: the plugin updates its own list when
    // it claims a device
  }

  /**
   * A composite list of the [CreateDeviceAction]s from all plugins that support device creation.
   */
  fun createDeviceActions(): List<CreateDeviceAction> =
    provisioners.mapNotNull { it.createDeviceAction }

  /**
   * A composite list of the [CreateDeviceTemplateAction]s from all plugins that support template
   * creation.
   */
  fun createTemplateActions(): List<CreateDeviceTemplateAction> =
    provisioners.mapNotNull { it.createDeviceTemplateAction }
}
