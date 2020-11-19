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

@file:JvmName("EmulatorUtils")
package com.android.build.gradle.integration.connected.utils

import com.android.testutils.TestUtils
import com.android.tools.bazel.avd.Emulator
import org.junit.rules.ExternalResource

/**
 * Path to the executable that the avd rule generates.
 *
 * <p>The executable is the script that starts and stops emulators and must be used to launch
 * the emulator.
 */
private const val DEVICE = "tools/base/build-system/integration-test/connected/avd"

/**
 * Port at which to open the emulator.
 *
 * <p>On RBE, bazel launches the emulator in a sandbox, so you can use any port you want. If you
 * launch multiple emulators from the same test, then use different ports for each of those
 * emulators.
 */
private const val PORT = 5554

/**
 * Return an [Emulator] using default port 5554
 */
fun getEmulator(): ExternalResource {
    if (TestUtils.runningFromBazel()) {
        return Emulator(DEVICE, PORT)
    } else {
        // Don't manage the emulator when running from Gradle for now
        return object : ExternalResource() {}
    }
}
