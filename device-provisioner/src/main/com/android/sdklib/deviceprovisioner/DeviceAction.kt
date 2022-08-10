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

import java.nio.file.Path
import kotlinx.coroutines.flow.StateFlow

/**
 * An action that a user might invoke on a device or a device provisioner. The actual action methods
 * are defined on subinterfaces, since their signatures may vary.
 *
 * TODO: These did not end up varying much; we perhaps don't need a distinct interface for every
 * action type
 */
interface DeviceAction {
  val label: String

  val isEnabled: StateFlow<Boolean>
}

interface CreateDeviceAction : DeviceAction {
  /**
   * Creates a device, based on input from the user.
   *
   * If creation is successful, this should have the side effect of adding the device to the
   * provisioner's list of devices.
   */
  suspend fun create()
}

interface CreateDeviceTemplateAction : DeviceAction {
  /**
   * Creates a device template, based on input from the user.
   *
   * If creation is successful, this should have the side effect of adding the device to the
   * provisioner's list of templates.
   */
  suspend fun create()
}

interface ActivationAction : DeviceAction {
  suspend fun activate(params: ActivationParams = ActivationParams.DefaultActivation)
}

sealed interface ActivationParams {
  object DefaultActivation : ActivationParams
  object ColdBoot : ActivationParams
  object QuickBoot : ActivationParams
  data class SnapshotBoot(val snapshot: Path) : ActivationParams
}

interface DeactivationAction : DeviceAction {
  suspend fun deactivate()
}

interface EditAction : DeviceAction {
  suspend fun edit()
}

interface EditTemplateAction : DeviceAction {
  suspend fun edit()
}

/** Deletes the given device from any persistent storage. */
interface DeleteAction : DeviceAction {
  suspend fun delete()
}
