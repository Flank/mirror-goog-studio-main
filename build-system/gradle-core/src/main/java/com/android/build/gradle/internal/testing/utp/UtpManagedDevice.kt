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

package com.android.build.gradle.internal.testing.utp

import java.io.File

/**
 * Processed data from the Android Gradle Plugin to UTP regarding a specific Managed Virtual Device
 * as specified from the DSL.
 *
 * @param deviceName the device name, as specified from the DSL.
 * @param avdName the name of the avd inside the gradle avd folder corresponding to the device.
 *   This name is created from the contents of the managed device DSL.
 * @param id A unique identifier used to identify the emulator after device creation. This allows
 *   for devices to share the same AVD, or share the same device name in the case of multiple gradle
 *   modules.
 * @param logcatPath the path in the output directory to direct the logs from the emulated device
 *   to.
 * @param launchMetadataPath the path in the output directory to write the metadata of the emulator
 *   after the emulator is launched.
 * @param emulatorLauncherPath the path to the emulator launcher script, which will start the device
 *   for the Unified Test Platform.
 */
data class UtpManagedDevice(
    val deviceName: String,
    val avdName: String,
    val id: String,
    val logcatPath: String,
    val launchMetadataPath: String,
    /* TODO(b/141510559): emulatorLauncherPath needs to be generated. */
    val emulatorLauncherPath: String)
