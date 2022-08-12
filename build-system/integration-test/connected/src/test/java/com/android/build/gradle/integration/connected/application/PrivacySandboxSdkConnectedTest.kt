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

package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit


/**
 * Connected tests using UTP test executor.
 */
class PrivacySandboxSdkConnectedTest {
    init {
        AndroidDebugBridge.init(true)
    }
    // private val adb: AndroidDebugBridge by lazy { AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS) }
    private val adb: AndroidDebugBridge =  AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS)
    private lateinit var device: IDevice

    @get:Rule var project = builder()
        .fromTestProject("privacySandboxSdk/libraryAndConsumer")
        .create()

    @Before
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout();
        adb.hasInitialDeviceList()
        assert(adb.devices.size == 1)
        device = adb.devices[0]

        device.uninstallPackage("com.example.rubidumconsumer")
        device.uninstallPackage("com.example.rubidumconsumer.test")
        device.uninstallPackage("com.myrbsdk_10000")
    }

    @Test
    fun `connectedAndroidTest task, SDK preinstalled`() {
        project.execute(":app:buildPrivacySandboxSdkApksForDebug")
        device.installPackage(
            project.getSubproject("app").getIntermediateFile(
                "extracted_apks_from_privacy_sandbox_sdks",
                "debug",
                "ads-sdk",
                "standalone.apk"
            ).path,
            /* reinstall */ true
        )
        project.execute("connectedAndroidTest")
    }

    @Test
    @Ignore // This doesn't work because deployment is not handled yet.
    fun `connectedAndroidTest task`() {
        project.execute("connectedAndroidTest")
    }

    @Test
    @Ignore // This doesn't work because deployment is not handled yet.
    fun `install and uninstall works for both SDK and APK`() {
        project.execute("installDebug")
        // Verify both APKs are installed here.
        project.execute("uninstallAll")
        // Verify both APKs are deleted
    }

    companion object {
        @JvmField @ClassRule val emulator = getEmulator()
    }
}
