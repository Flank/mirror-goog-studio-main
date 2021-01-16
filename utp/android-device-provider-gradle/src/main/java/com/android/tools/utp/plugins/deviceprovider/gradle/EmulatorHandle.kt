/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.utp.plugins.deviceprovider.gradle

/**
 * Interface for handling calls into an emulator instance from the Gradle
 * device provider.
 */
interface EmulatorHandle {
    /** Configure the path to the emulator executable. */
    fun configure(emulatorPath: String)

    /** Returns true if and only if handling an active instance of the emulator. */
    fun isAlive(): Boolean

    /**
     * Launches an instance of the emulator.
     *
     * @param avdName name of the android virtual device to be launched.
     * @param avdFolder the folder containing the given avd.
     * @param avdId unique identifier to identify the avd.
     * @param enableDisplay whether or not to display the emulator.
     */
    fun launchInstance(
            avdName: String,
            avdFolder: String,
            avdId: String,
            enableDisplay: Boolean
    )

    /** Closes the instance of the emulator associated with this handle */
    fun closeInstance()
}
