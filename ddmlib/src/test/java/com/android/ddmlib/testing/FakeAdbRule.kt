/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ddmlib.testing

import com.android.ddmlib.AdbInitOptions
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.EmulatorConsole
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Uninterruptibles
import org.junit.rules.ExternalResource
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Rule that sets up and tears down a FakeAdbServer, and provides some convenience methods for interacting with it.
 */
class FakeAdbRule : ExternalResource() {
  /**
   * An [AndroidDebugBridge] that will be initialized unless [initAdbBridgeDuringSetup] is set to false, in which
   * case trying to access this value will throw an exception.
   */
  lateinit var bridge: AndroidDebugBridge
    private set

  private val isJdwpProxyEnabledDefault = DdmPreferences.isJdwpProxyEnabled()
  private var initAdbBridgeDuringSetup = true
  private var closeFakeAdbServerDuringCleanUp = true
  private lateinit var fakeAdbServer: FakeAdbServer
  private val startingDevices: MutableMap<String, CountDownLatch> = mutableMapOf()
  private var consoleFactory: (String, String) -> EmulatorConsole =
        { name, path -> FakeEmulatorConsole(name, path) }
  private val hostCommandHandlers: MutableMap<String, () -> HostCommandHandler> = mutableMapOf()
  private val deviceCommandHandlers: MutableList<DeviceCommandHandler> = mutableListOf(
    object : DeviceCommandHandler("track-jdwp") {
      override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
        startingDevices[device.deviceId]?.countDown()
        return false
      }
    }
  )

  /**
   * Add a [HostCommandHandler]. Must be called before @Before tasks are run.
   */
  fun withHostCommandHandler(command: String, handlerConstructor: () -> HostCommandHandler) = apply {
    hostCommandHandlers[command] = handlerConstructor
  }

  /**
   * Add a [DeviceCommandHandler]. Must be called before @Before tasks are run.
   */
  fun withDeviceCommandHandler(handler: DeviceCommandHandler) = apply {
    deviceCommandHandlers.add(handler)
  }

    /**
     * Add a [EmulatorConsole] factory.
     */
    fun withEmulatorConsoleFactory(factory: (String, String) -> EmulatorConsole) = apply {
        consoleFactory = factory
    }

    /**
   * Initialize the ADB bridge as part of the setup.
   *
   * Some tests may delay this step and call initialize the AdbBridge separately.
   */
  fun initAbdBridgeDuringSetup(initBridge: Boolean) = apply { initAdbBridgeDuringSetup = initBridge }

  /**
   * Close the fake adb server as part of the cleanup.
   *
   * Some tests may omit this part of the cleanup to avoid closing the server twice.
   */
  fun closeServerDuringCleanUp(closeServer: Boolean) = apply { closeFakeAdbServerDuringCleanUp = closeServer }

  fun attachDevice(
      deviceId: String,
      manufacturer: String,
      model: String,
      release: String,
      sdk: String,
      abi: String = "x86_64",
      properties: Map<String, String> = emptyMap(),
      hostConnectionType: DeviceState.HostConnectionType = DeviceState.HostConnectionType.USB,
      avdName: String? = null,
      avdPath: String? = null
  ): DeviceState {
    val startLatch = CountDownLatch(1)
    startingDevices[deviceId] = startLatch
    if (avdName != null && avdPath != null) {
      EmulatorConsole.registerConsoleForTest(deviceId, consoleFactory(avdName, avdPath))
    }
    val deviceFuture = fakeAdbServer.connectDevice(
            deviceId,
            manufacturer,
            model,
            release,
            sdk,
            abi,
            properties,
            hostConnectionType)
    val device = deviceFuture.get()
    device.deviceStatus = DeviceState.DeviceStatus.ONLINE
    assertThat(startLatch.await(30, TimeUnit.SECONDS)).isTrue()
    return device
  }

  fun disconnectDevice(deviceId: String) {
      fakeAdbServer.disconnectDevice(deviceId).get()
  }

  val fakeAdbServerPort: Int
    get() = fakeAdbServer.port

  override fun before() {
    val builder = FakeAdbServer.Builder().installDefaultCommandHandlers()
    hostCommandHandlers.forEach { (command, constructor) -> builder.setHostCommandHandler(command, constructor) }
    deviceCommandHandlers.forEach { builder.addDeviceHandler(it) }
    fakeAdbServer = builder.build()
    fakeAdbServer.start()

    if (initAdbBridgeDuringSetup) {
      AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.port)
      val options = AdbInitOptions.builder()
          .setClientSupportEnabled(true)
          .useJdwpProxyService(false)
          .build()
      AndroidDebugBridge.init(options)
      bridge = AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS) ?: error("Could not create ADB bridge")
      val startTime = System.currentTimeMillis()
      while ((!bridge.isConnected || !bridge.hasInitialDeviceList()) &&
             System.currentTimeMillis() - startTime < TimeUnit.SECONDS.toMillis(10)) {
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS)
      }
    }
  }

  override fun after() {
    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disableFakeAdbServerMode()
    DdmPreferences.enableJdwpProxyService(isJdwpProxyEnabledDefault)
    if (closeFakeAdbServerDuringCleanUp) {
      fakeAdbServer.close()
      if (fakeAdbServer.awaitServerTermination(30, TimeUnit.SECONDS) == false) {
        error("The adbServer didn't terminate in 30 seconds")
      }
    }
    EmulatorConsole.clearConsolesForTest()
  }

  fun stop()  {
    fakeAdbServer.stop()
  }

  /**
   * Add a [DeviceCommandHandler] to an already initialized  [FakeAdbServer].
   *
   * The handler is inserted as the first to be evaluated so handlers can override preexisting ones.
   */
  fun addDeviceCommandHandler(deviceCommandHandler: DeviceCommandHandler) {
    fakeAdbServer.handlers.add(0, deviceCommandHandler)
  }
}

class FakeEmulatorConsole(
    private val _avdName: String,
    private val _avdPath: String
) : EmulatorConsole() {

    override fun getAvdName(): String? {
        return _avdName
    }

    override fun getAvdPath(): String {
        return _avdPath
    }

    override fun close() {}

    override fun kill() {}

    override fun startEmulatorScreenRecording(args: String?): String {
        TODO("Not yet implemented")
    }

    override fun stopScreenRecording(): String {
        TODO("Not yet implemented")
    }
}
