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

import kotlinx.coroutines.flow.StateFlow

/**
 * A specific device known to the DeviceProvisioner. It may or may not be connected.
 *
 * This is stateful and has identity: if a device corresponding to this DeviceHandle is connected,
 * disconnected, and reconnected, this DeviceHandle should remain linked to it.
 */
interface DeviceHandle {
  val state: DeviceState
    get() = stateFlow.value

  val stateFlow: StateFlow<DeviceState>

  /** An action that allows activating the device, or null if activation is not supported. */
  val activationAction: ActivationAction?
    get() = null

  /** An action that allows deactivating the device, or null if deactivation is not supported. */
  val deactivationAction: DeactivationAction?
    get() = null

  /** An action that allows editing the device, or null if editing is not supported. */
  val editAction: EditAction?
    get() = null
}
