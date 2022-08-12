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
import com.android.adblib.deviceProperties
import com.android.adblib.serialNumber
import com.android.adblib.tools.EmulatorConsole
import com.android.adblib.tools.localConsoleAddress
import com.android.adblib.tools.openEmulatorConsole
import com.android.annotations.concurrency.GuardedBy
import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdInfo
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides access to emulators running on the local machine from the standard AVD directory.
 * Supports creating, editing, starting, and stopping AVD instances.
 *
 * This plugin creates device handles for all AVDs present in the standard AVD directory, running or
 * not. The AVD path is used to identify devices and establish the link between connected devices
 * and their handles. The directory is periodically rescanned to find new devices, and immediately
 * rescanned after an edit is made via a device action.
 */
class LocalEmulatorProvisionerPlugin(
  private val adbSession: AdbSession,
  private val avdManager: AvdManager,
  rescanPeriod: Duration = Duration.ofSeconds(10)
) : DeviceProvisionerPlugin {

  /**
   * An abstraction of the AvdManager / AvdManagerConnection classes to be injected, allowing for
   * testing and decoupling from Studio.
   */
  interface AvdManager {
    suspend fun rescanAvds(): List<AvdInfo>
    suspend fun createAvd(): Boolean
    suspend fun editAvd(avdInfo: AvdInfo): Boolean
    suspend fun startAvd(avdInfo: AvdInfo)
    suspend fun stopAvd(avdInfo: AvdInfo)
  }

  private val scope = adbSession.scope
  init {
    scope.coroutineContext.job.invokeOnCompletion {
      avdScanner.cancel()
      emulatorConsoles.values.forEach { it.close() }
    }
  }

  // We can identify local emulators reliably, so this can be relatively high priority.
  override val priority = 100

  private val mutex = Mutex()
  @GuardedBy("mutex") private val deviceHandles = HashMap<Path, LocalEmulatorDeviceHandle>()

  private val _devices = MutableStateFlow<List<DeviceHandle>>(emptyList())
  override val devices: StateFlow<List<DeviceHandle>> = _devices.asStateFlow()

  private val emulatorConsoles = ConcurrentHashMap<ConnectedDevice, EmulatorConsole>()

  // TODO: Consider if it would be better to use a filesystem watcher here instead of polling.
  private val avdScanner =
    PeriodicAction(scope, rescanPeriod) {
      val avdsOnDisk = avdManager.rescanAvds().associateBy { it.dataFolderPath }
      mutex.withLock {
        for ((path, handle) in deviceHandles) {
          // Remove any current DeviceHandles that are no longer present on disk, unless they are
          // connected.
          if (!avdsOnDisk.containsKey(path) && handle.state is Disconnected) {
            // What if a client holds on to the device, and then it gets recreated? It will be a
            // different device then, which is OK.
            deviceHandles.remove(path)
          }
        }

        for ((path, avdInfo) in avdsOnDisk) {
          when (val handle = deviceHandles[path]) {
            null ->
              deviceHandles[path] =
                LocalEmulatorDeviceHandle(Disconnected(toDeviceProperties(avdInfo)), avdInfo)
            else ->
              // Update the avdInfo if we're not currently running. If we are running, the old
              // values are probably still in effect, but we will update on the next scan after
              // shutdown.
              if (handle.avdInfo != avdInfo && handle.state is Disconnected) {
                handle.avdInfo = avdInfo
                handle.stateFlow.value = Disconnected(toDeviceProperties(avdInfo))
              }
          }
        }

        _devices.value = deviceHandles.values.toList()
      }
    }

  override suspend fun claim(device: ConnectedDevice): Boolean {
    val result = LOCAL_EMULATOR_REGEX.matchEntire(device.serialNumber) ?: return false
    val port = result.groupValues[1].toIntOrNull() ?: return false

    val emulatorConsole =
      adbSession.openEmulatorConsole(
        localConsoleAddress(port),
        AndroidLocationsSingleton.userHomeLocation.resolve(".emulator_console_auth_token")
      )
    emulatorConsoles[device] = emulatorConsole

    // This will fail on emulator versions prior to 30.0.18.
    val pathResult = kotlin.runCatching { emulatorConsole.avdPath() }
    val path = pathResult.getOrNull()

    if (path == null) {
      // If we can't connect to the emulator console, this isn't operationally a local emulator
      emulatorConsoles.remove(device)?.close()
      return false
    }

    // Try to link this device to an existing handle.
    var handle = tryConnect(path, port, device)
    if (handle == null) {
      // We didn't read this path from disk yet. Rescan and try again.
      avdScanner.runNow().join()
      handle = tryConnect(path, port, device)
    }
    if (handle == null) {
      // Apparently this emulator is not on disk, or it is not in the directory that we scan for
      // AVDs. (Perhaps GMD or Crow failed to pick it up.)
      emulatorConsoles.remove(device)?.close()
      return false
    }

    // We need to make sure that emulators change to Disconnected state once they are terminated.
    device.invokeOnDisconnection {
      handle.stateFlow.value = Disconnected(handle.state.properties)
      emulatorConsoles.remove(device)?.close()
    }

    return true
  }

  private suspend fun tryConnect(
    path: Path,
    port: Int,
    device: ConnectedDevice
  ): LocalEmulatorDeviceHandle? =
    mutex.withLock {
      val handle = deviceHandles[path] ?: return@withLock null
      // For the offline device, we got most properties from the AvdInfo, though we had to
      // compute androidRelease. Now read them from the device.
      val deviceProperties = device.deviceProperties().allReadonly()
      val properties =
        LocalEmulatorProperties.build {
          readCommonProperties(deviceProperties)
          avdName = handle.avdInfo.name
          displayName = handle.avdInfo.displayName
          disambiguator = port.toString()
        }
      handle.stateFlow.value = Connected(properties, device)
      handle
    }

  private fun toDeviceProperties(avdInfo: AvdInfo) =
    LocalEmulatorProperties.build {
      manufacturer = avdInfo.deviceManufacturer
      model = avdInfo.deviceName
      androidVersion = avdInfo.androidVersion
      androidRelease = SdkVersionInfo.getVersionString(avdInfo.androidVersion.apiLevel)
      abi = Abi.getEnum(avdInfo.abiType)
      avdName = avdInfo.name
      displayName = avdInfo.displayName
    }

  private fun refreshDevices() {
    avdScanner.runNow()
  }

  override val createDeviceAction =
    object : CreateDeviceAction {
      override val label = "Create AVD"
      override val isEnabled = MutableStateFlow(true).asStateFlow()

      override suspend fun create() {
        if (avdManager.createAvd()) {
          refreshDevices()
        }
      }
    }

  /**
   * These can be created in two circumstances: when reading the AVD off the disk, or when a
   * connection is made and identified as a local device.
   */
  private inner class LocalEmulatorDeviceHandle(
    initialState: DeviceState,
    initialAvdInfo: AvdInfo
  ) : DeviceHandle {
    override val stateFlow = MutableStateFlow(initialState)

    private val _avdInfo = AtomicReference(initialAvdInfo)

    /** AvdInfo can be updated when the device is edited on-disk and rescanned. */
    var avdInfo: AvdInfo
      get() = _avdInfo.get()
      set(value) = _avdInfo.set(value)

    /** The emulator console is present when the device is connected. */
    val emulatorConsole: EmulatorConsole?
      get() = emulatorConsoles[state.connectedDevice]

    override val activationAction =
      object : ActivationAction {
        override val label: String = "Start"

        override val isEnabled =
          stateFlow
            .map { it is Disconnected }
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

        override suspend fun activate(params: ActivationParams) {
          // Note: the original DeviceManager does this in UI thread, but this may call
          // @Slow methods so switch
          stateFlow.value =
            Activating(
              stateFlow.value.properties,
              scope.launch { avdManager.startAvd(avdInfo) },
              TimeoutTracker(CONNECTION_TIMEOUT)
            )
        }
      }

    override val editAction =
      object : EditAction {
        override val label = "Edit"

        override val isEnabled = MutableStateFlow(true).asStateFlow()

        override suspend fun edit() {
          if (avdManager.editAvd(avdInfo)) {
            refreshDevices()
          }
        }
      }

    override val deactivationAction: DeactivationAction =
      object : DeactivationAction {
        override val label = "Stop"

        // We could check this with AvdManagerConnection.isAvdRunning, but that's expensive, and if
        // it's not running we should see it from ADB anyway
        override val isEnabled =
          stateFlow.map { it is Connected }.stateIn(scope, SharingStarted.WhileSubscribed(), false)

        override suspend fun deactivate() {
          when (val state = state) {
            // TODO: In theory, we could cancel from the Connecting state, but it's hard to make
            // that work without rewriting AvdManagerConnection.
            is Connected -> {
              stateFlow.value =
                Deactivating(
                  state.properties,
                  state.connectedDevice,
                  scope.launch(Dispatchers.IO) {
                    // We can either use the emulator console or AvdManager (which uses a shell
                    // command to kill the process)
                    emulatorConsole?.kill() ?: avdManager.stopAvd(avdInfo)
                  },
                  TimeoutTracker(DISCONNECTION_TIMEOUT)
                )
            }
            else -> {}
          }
        }
      }
  }
}

class LocalEmulatorProperties(
  base: DeviceProperties,
  val avdName: String,
  val displayName: String
) : DeviceProperties by base {
  class Builder : DeviceProperties.Builder() {
    var avdName: String? = null
    var displayName: String? = null
  }

  override fun title() = displayName

  companion object {
    fun build(block: Builder.() -> Unit) =
      Builder().apply(block).run {
        LocalEmulatorProperties(buildBase(), checkNotNull(avdName), checkNotNull(displayName))
      }
  }
}

private val LOCAL_EMULATOR_REGEX = "emulator-(\\d+)".toRegex()
