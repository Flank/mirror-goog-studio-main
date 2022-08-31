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
import com.android.adblib.DeviceSelector
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.deviceInfo
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeoutOrNull

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
        (provisioners + OfflineDeviceProvisionerPlugin() + DefaultProvisionerPlugin())
          .sortedByDescending { it.priority }
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

  /**
   * Finds the DeviceHandle of the connected device with the given serial number, waiting up to
   * [timeout] for it to appear.
   *
   * This is intended for interoperability with DDMLib, or AdbLib's ConnectedDevicesTracker. It's
   * possible for a device to be visible by these interfaces before it becomes visible via the
   * DeviceProvisioner; thus, we wait a short time for it to show up before giving up.
   *
   * This has the potential to introduce unnecessary delays if invoked for a device that no longer
   * exists, thus it should be used with care. Generally, it is preferable to obtain a
   * [DeviceHandle] by collecting the [devices] flow.
   */
  suspend fun findConnectedDeviceHandle(
    deviceSelector: DeviceSelector,
    timeout: Duration = Duration.ofSeconds(2)
  ): DeviceHandle? =
    withTimeoutOrNull(timeout) {
      val serialNumber = adbSession.hostServices.getSerialNo(deviceSelector)
      val connectedDevices = adbSession.connectedDevicesTracker.connectedDevices
      // If it's not present in the ConnectedDevicesTracker, make a call to ADB to be sure.
      val isPresent =
        connectedDevices.value.any { it.serialNumber == serialNumber } ||
          adbSession.hostServices.devices().any { it.serialNumber == serialNumber }
      when {
        isPresent ->
          // ADB is aware of this serial number; wait for it to appear in the provisioner.
          devices
            .mapNotNull { handles ->
              handles.firstOrNull { it.state.connectedDevice?.serialNumber == serialNumber }
            }
            .firstOrNull()
        else -> null
      }
    }

  init {
    // Track changes in ADB-connected devices: any time we have a ConnectedDevice from ADB
    // that is not owned by a DeviceHandle, offer it to the plugins. We need to track changes in
    // both the ConnectedDevicesTracker and in our device list (in case a DeviceHandle goes away).
    adbSession.scope.launch {
      adbSession
        .connectedDevicesTracker
        .connectedDevices
        .combine(claimedConnectedDevices()) { connectedDevices, claimedConnectedDevices ->
          (connectedDevices - claimedConnectedDevices).forEach { offer(it) }
        }
        .collect()
    }
  }

  /** Extracts all the ConnectedDevices from the current DeviceHandles. */
  private fun claimedConnectedDevices(): Flow<Set<ConnectedDevice>> =
    devices.flatMapLatest { handles ->
      when {
        handles.isEmpty() -> flowOf(emptySet())
        else ->
          combine(handles.map { it.stateFlow.map { it.connectedDevice } }) {
            it.filterNotNull().toSet()
          }
      }
    }

  /** Offers the device to all of the plugins in priority order, stopping once one claims it. */
  private suspend fun offer(newDevice: ConnectedDevice) {
    provisioners.firstOrNull {
      try {
        it.claim(newDevice)
      } catch (t: Throwable) {
        logger.error(t, "Offering ${newDevice.deviceInfo.serialNumber}")
        false
      }
    }
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
