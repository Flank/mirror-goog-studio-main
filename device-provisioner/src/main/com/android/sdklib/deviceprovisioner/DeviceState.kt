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

import com.android.adblib.ConnectedDevice
import com.google.common.base.Stopwatch
import java.time.Duration
import kotlinx.coroutines.Job

/**
 * Identifies the state of a provisionable device with respect to ADB: disconnected, connecting,
 * connected, disconnecting.
 */
sealed interface DeviceState {
  val properties: DeviceProperties
  val connectedDevice: ConnectedDevice?
    get() = null
}

class Disconnected(override val properties: DeviceProperties) : DeviceState

class Activating(
  override val properties: DeviceProperties,
  val job: Job,
  val timeoutTracker: TimeoutTracker
) : DeviceState

class Connected(
  override val properties: DeviceProperties,
  override val connectedDevice: ConnectedDevice
) : DeviceState

class Deactivating(
  override val properties: DeviceProperties,
  override val connectedDevice: ConnectedDevice,
  val job: Job,
  val timeoutTracker: TimeoutTracker
) : DeviceState

class TimeoutTracker(private val duration: Duration) {
  private val stopwatch = Stopwatch.createStarted()

  fun isTimedOut() = stopwatch.elapsed() >= duration
}

private const val CONNECTION_TIMEOUT_MINUTES: Long = 5
val CONNECTION_TIMEOUT = Duration.ofMinutes(CONNECTION_TIMEOUT_MINUTES)

private const val DISCONNECTION_TIMEOUT_MINUTES: Long = 1
val DISCONNECTION_TIMEOUT = Duration.ofMinutes(DISCONNECTION_TIMEOUT_MINUTES)
