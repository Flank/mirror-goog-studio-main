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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Identifies the state of a provisionable device with respect to ADB: disconnected, connecting,
 * connected, disconnecting.
 */
sealed interface DeviceState {
  val properties: DeviceProperties
  val connectedDevice: ConnectedDevice?
    get() = null
}

open class Disconnected(override val properties: DeviceProperties) : DeviceState

open class Activating(override val properties: DeviceProperties) : DeviceState

open class Connected(
  override val properties: DeviceProperties,
  override val connectedDevice: ConnectedDevice
) : DeviceState

open class Deactivating(
  override val properties: DeviceProperties,
  override val connectedDevice: ConnectedDevice,
) : DeviceState

class TimeoutTracker(private val duration: Duration) {
  private val stopwatch = Stopwatch.createStarted()

  fun isTimedOut() = stopwatch.elapsed() >= duration
}

/**
 * Utility method intended for advancing DeviceState to an intermediate state (e.g. Activating,
 * Deactivating), and then reverting back to the original state if it stays in that state for too
 * long.
 *
 * First, we conditionally and atomically update to the intermediate state using [updateState]: this
 * should return the new state given the current state, or null if the current state cannot be
 * updated (which aborts the entire operation).
 *
 * Then, we invoke [advanceAction]: this is an arbitrary action that should cause the state to
 * advance out of the intermediate state before the [timeout].
 *
 * We then listen for updates to the state: if the state advances before [timeout], we are done.
 * Otherwise, we attempt to reset the state to the original state, and cancel the [advanceAction].
 * If we reset the state back to the original state, [onAbort] is invoked.
 *
 * This uses atomic compareAndSet operations to ensure that we do not clobber concurrent state
 * updates from elsewhere.
 *
 * @return true if we advanced to the intermediate state
 */
fun <T> MutableStateFlow<T>.advanceStateWithTimeout(
  scope: CoroutineScope,
  updateState: (T) -> T?,
  timeout: Duration,
  advanceAction: suspend () -> Unit,
  onAbort: suspend () -> Unit = {}
): Boolean {
  while (true) {
    val originalState = value
    val intermediateState = updateState(originalState) ?: return false
    if (compareAndSet(originalState, intermediateState)) {
      scope.launch {
        val advanceJob = currentCoroutineContext().job
        launch {
          try {
            withTimeout(timeout.toMillis()) { takeWhile { it == intermediateState }.collect() }
          } catch (e: TimeoutCancellationException) {
            if (compareAndSet(intermediateState, originalState)) {
              onAbort()
            }
            advanceJob.cancel()
          }
        }
        advanceAction()
      }
      return true
    }
  }
}
