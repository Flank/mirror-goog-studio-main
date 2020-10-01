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

package com.android.build.gradle.internal

import com.android.SdkConstants
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.repository.testframework.MockFileOp
import com.android.sdklib.repository.AndroidSdkHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

@RunWith(JUnit4::class)
class AvdManagerTest {

    @get:Rule
    val testFolder = TemporaryFolder()
    private lateinit var manager: AvdManager
    private lateinit var sdkFolder: File
    private lateinit var systemImageFolder: File
    private lateinit var emulatorFolder: File
    private lateinit var androidPrefsFolder: File
    private lateinit var avdFolder: File

    @Before
    fun setup() {
        sdkFolder = testFolder.newFolder("sdk")
        systemImageFolder = sdkFolder.resolve("system-images/android-29/default/x86")
        systemImageFolder.mkdirs()
        val vendorImage = systemImageFolder.resolve("system.img")
        vendorImage.createNewFile()
        vendorImage.writeText("""""")
        val userImg =
            systemImageFolder.resolve(com.android.sdklib.internal.avd.AvdManager.USERDATA_IMG)
        userImg.createNewFile()
        userImg.writeText("""""")
        emulatorFolder = sdkFolder.resolve("tools/lib/emulator")
        emulatorFolder.mkdirs()
        androidPrefsFolder = testFolder.newFolder("android-home")
        avdFolder = testFolder.newFolder()

        val sdkComponents = setupSdkComponents()
        val sdkHandler = setupSdkHandler()

        manager = AvdManager(avdFolder, sdkComponents, sdkHandler)
    }

    @Test
    fun noDevicesFromEmptyFolder() {
        //TODO(b/169661721): add support for windows.
        assumeTrue(SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS)
        assertThat(manager.allAvds()).hasSize(0)
    }

    @Test
    fun addSingleDevice() {
        //TODO(b/169661721): add support for windows.
        assumeTrue(SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS)
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")

        val allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(1)
        assertThat(allAvds.first()).isEqualTo("device1")
    }

    @Test
    fun addMultipleDevices() {
        //TODO(b/169661721): add support for windows.
        assumeTrue(SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS)
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device2",
            "Pixel 3")
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device3",
            "Pixel 2")

        val allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(3)
        assertThat(allAvds).contains("device1")
        assertThat(allAvds).contains("device2")
        assertThat(allAvds).contains("device3")
    }

    @Test
    fun addSameDeviceDoesNotDuplicate() {
        //TODO(b/169661721): add support for windows.
        assumeTrue(SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS)
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")

        val allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(1)
        assertThat(allAvds.first()).isEqualTo("device1")
    }

    @Test
    fun testDeleteDevices() {
        //TODO(b/169661721): add support for windows.
        assumeTrue(SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS)
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")
        manager.createOrRetrieveAvd(
            "system-images;android-29;default;x86",
            "device2",
            "Pixel 3")

        var allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(2)

        manager.deleteAvds(listOf("device1"))

        allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(1)
        assertThat(allAvds.first()).isEqualTo("device2")
    }

    private fun setupSdkComponents(): SdkComponentsBuildService {

        val sdkComponents = mock(SdkComponentsBuildService::class.java)
        `when`(sdkComponents.sdkDirectoryProvider)
            .thenReturn(FakeGradleProvider(FakeGradleDirectory(sdkFolder)))
        `when`(sdkComponents.sdkImageDirectoryProvider(anyString()))
            .thenReturn(FakeGradleProvider(FakeGradleDirectory(systemImageFolder)))
        `when`(sdkComponents.emulatorDirectoryProvider)
            .thenReturn(FakeGradleProvider(FakeGradleDirectory(emulatorFolder)))

        return sdkComponents
    }

    private fun setupSdkHandler(): AndroidSdkHandler {
        val fileOp = MockFileOp()
        fileOp.recordExistingFile(
            emulatorFolder.absolutePath + "/snapshots.img")
        fileOp.recordExistingFile(
            emulatorFolder.absolutePath +
                    "/" + SdkConstants.FD_LIB +
                    "/" + SdkConstants.FN_HARDWARE_INI,
            """""".trimIndent())
        recordSysImg(fileOp)

        return AndroidSdkHandler(sdkFolder, androidPrefsFolder, fileOp)
    }

    private fun recordSysImg(fileOp: MockFileOp) {
        fileOp.recordExistingFile(systemImageFolder.absolutePath + "/system.img")
        fileOp.recordExistingFile(
            systemImageFolder.absolutePath +
                    "/" +
                    com.android.sdklib.internal.avd.AvdManager.USERDATA_IMG)
        fileOp.recordExistingFile(systemImageFolder.absolutePath + "/skins/res1/layout")
        fileOp.recordExistingFile(systemImageFolder.absolutePath + "/skins/sample")
        fileOp.recordExistingFile(systemImageFolder.absolutePath + "/skins/res2/layout")
        fileOp.recordExistingFile(
            systemImageFolder.absolutePath + "/package.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <ns3:sdk-sys-img
                    xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                    xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                    xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                    xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
                    <license id="license" type="text">A Very Valid License
                    </license><localPackage path="system-images;android-29;default;x86"
                    obsolete="false">
                    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:type="ns3:sysImgDetailsType"><api-level>29</api-level>
                    <tag><id>default</id><display>Default</display></tag><abi>x86</abi>
                    </type-details><revision><major>5</major></revision>
                    <display-name>Intel x86 Atom System Image</display-name>
                    <uses-license ref="license"/></localPackage>
                    </ns3:sdk-sys-img>
                    """.trimIndent())
    }
}