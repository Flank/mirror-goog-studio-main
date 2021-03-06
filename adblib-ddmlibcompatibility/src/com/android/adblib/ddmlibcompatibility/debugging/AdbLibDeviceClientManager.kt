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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.createDeviceScope
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpTracker
import com.android.adblib.trackDeviceInfo
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.clientmanager.DeviceClientManager
import com.android.ddmlib.clientmanager.DeviceClientManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

internal class AdbLibDeviceClientManager(
    private val clientManager: AdbLibClientManager,
    private val bridge: AndroidDebugBridge,
    private val iDevice: IDevice,
    private val listener: DeviceClientManagerListener
) : DeviceClientManager {

    private val logger = thisLogger(clientManager.session)

    private val deviceSelector = DeviceSelector.fromSerialNumber(iDevice.serialNumber)

    private val session: AdbLibSession
        get() = clientManager.session

    private val retryDelay = Duration.ofSeconds(2)

    private val clientList = AtomicReference<List<Client>>(emptyList())

    /**
     * The [CoroutineScope] that is active as long as [iDevice] is connected.
     */
    val deviceScope = session.createDeviceScope(deviceSelector)

    override fun getDevice(): IDevice {
        return iDevice
    }

    override fun getClients(): MutableList<Client> {
        return clientList.get().toMutableList()
    }

    fun startDeviceTracking() {
        deviceScope.launch(session.host.ioDispatcher) {
            session.trackDeviceInfo(deviceSelector)
                .filter {
                    // Wait until device is "ONLINE"
                    it.deviceState == DeviceState.ONLINE
                }.map {
                    // Run the 'jdwp-track' service and collect PIDs
                    trackProcesses()
                }.retryWhen { throwable, _ ->
                    logger.warn(
                        throwable,
                        "Device process tracking failed for device $deviceSelector " +
                                "($throwable), retrying in ${retryDelay.seconds} sec"
                    )
                    // We retry as long as the device is valid
                    if (deviceScope.isActive) {
                        delay(retryDelay.toMillis())
                    }
                    deviceScope.isActive
                }.collect()
        }
    }

    private suspend fun trackProcesses() {
        val processEntryMap = mutableMapOf<Int, AdblibClientWrapper>()
        try {
            JdwpTracker(clientManager.session, deviceSelector).createFlow()
                .collect { processList ->
                    updateProcessList(processEntryMap, processList)
                }
        } finally {
            updateProcessList(processEntryMap, emptyList())
        }
    }

    /**
     * Update our list of processes and invoke listeners.
     */
    private fun updateProcessList(
        currentProcessEntryMap: MutableMap<Int, AdblibClientWrapper>,
        newJdwpProcessList: List<JdwpProcess>
    ) {
        val knownPids = currentProcessEntryMap.keys.toHashSet()
        val effectivePids = newJdwpProcessList.map { it.pid }.toHashSet()
        val addedPids = effectivePids - knownPids
        val removePids = knownPids - effectivePids

        // Remove old pids
        removePids.forEach { pid ->
            logger.debug { "Removing PID $pid from list of Client processes" }
            currentProcessEntryMap.remove(pid)
        }

        // Add new pids
        addedPids.forEach { pid ->
            logger.debug { "Adding PID $pid to list of Client processes" }
            val jdwpProcess = newJdwpProcessList.first { it.pid == pid }
            val clientWrapper = AdblibClientWrapper(this, iDevice, jdwpProcess)
            currentProcessEntryMap[pid] = clientWrapper
            launchProcessInfoTracking(clientWrapper)
        }

        assert(currentProcessEntryMap.keys.size == newJdwpProcessList.size)
        clientList.set(currentProcessEntryMap.values.toList())
        invokeListener {
            listener.processListUpdated(bridge, this@AdbLibDeviceClientManager)
        }
    }

    private fun launchProcessInfoTracking(clientWrapper: AdblibClientWrapper) {
        // Track process changes as long as process coroutine scope is active
        clientWrapper.jdwpProcess.scope.launch {
            trackProcessInfo(clientWrapper)
        }
    }

    private suspend fun trackProcessInfo(clientWrapper: AdblibClientWrapper) {
        var lastProcessInfo = clientWrapper.jdwpProcess.processPropertiesFlow.value
        clientWrapper.jdwpProcess.processPropertiesFlow.collect { processInfo ->
            try {
                updateProcessInfo(clientWrapper, lastProcessInfo, processInfo)
            } finally {
                lastProcessInfo = processInfo
            }
        }
    }

    private fun updateProcessInfo(
        clientWrapper: AdblibClientWrapper,
        previousProcessInfo: JdwpProcessProperties,
        newProcessInfo: JdwpProcessProperties
    ) {
        fun <T> hasChanged(x: T?, y: T?): Boolean {
            return x != y
        }

        // Always update "Client" wrapper data
        val names =
            ClientData.Names(
                newProcessInfo.processName ?: "",
                newProcessInfo.userId,
                newProcessInfo.packageName
            )
        clientWrapper.clientData.setNames(names)
        clientWrapper.clientData.vmIdentifier = newProcessInfo.vmIdentifier
        clientWrapper.clientData.abi = newProcessInfo.abi
        clientWrapper.clientData.jvmFlags = newProcessInfo.jvmFlags
        clientWrapper.clientData.isNativeDebuggable = newProcessInfo.isNativeDebuggable

        // Check if anything related to process info has changed
        if (hasChanged(previousProcessInfo.processName, newProcessInfo.processName) ||
            hasChanged(previousProcessInfo.userId, newProcessInfo.userId) ||
            hasChanged(previousProcessInfo.packageName, newProcessInfo.packageName) ||
            hasChanged(previousProcessInfo.vmIdentifier, newProcessInfo.vmIdentifier) ||
            hasChanged(previousProcessInfo.abi, newProcessInfo.abi) ||
            hasChanged(previousProcessInfo.jvmFlags, newProcessInfo.jvmFlags) ||
            hasChanged(previousProcessInfo.isNativeDebuggable, newProcessInfo.isNativeDebuggable)
        ) {
            invokeListener {
                // Note that "name" is really "any property"
                listener.processNameUpdated(
                    bridge,
                    this@AdbLibDeviceClientManager,
                    clientWrapper
                )
            }
        }

        // TODO: Invoke debugger status updated once implemented
        //invokeListener {
        //    listener.processDebuggerStatusUpdated(
        //        bridge,
        //        this@AdbLibDeviceClientManager,
        //        clientWrapper
        //    )
        //}
    }

    private fun invokeListener(block: () -> Unit) {
        deviceScope.launch(session.host.ioDispatcher) {
            kotlin.runCatching {
                block()
            }.onFailure { throwable ->
                logger.warn(throwable, "Invoking ddmlib listener threw an exception: $throwable")
            }
        }
    }
}
