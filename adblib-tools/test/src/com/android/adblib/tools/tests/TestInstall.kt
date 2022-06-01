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
package com.android.adblib.tools.tests

import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.INSTALL_APK_STAGING
import com.android.adblib.tools.InstallException
import com.android.adblib.tools.PMAbb
import com.android.adblib.tools.PMDriver
import com.android.adblib.tools.PMDriver.Companion.cleanFilename
import com.android.adblib.tools.PMLegacy
import com.android.adblib.tools.install
import com.android.fakeadbserver.services.PackageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TestInstall : TestInstallBase() {


    @Test
    fun testInstallSuccess() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }
    }

    @Test
    fun testInstallCommFailure() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 29)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            try {
                val client = PMAbb(deviceServices)
                val flow = client.commit(deviceSelector, "12345")
                flow.first()
                Assert.fail("Installation did not fail")
            } catch (e : Exception) {
                e.printStackTrace()
                // Expected
            }
        }
    }

    @Test
    fun testInstallBadParameterFailureCommit() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            try {
                val client = PMAbb(deviceServices)
                val flow = client.commit(deviceSelector, PackageManager.FAIL_ME_SESSION_TEST_ONLY)
                PMDriver.parseInstallResult(flow.first())
                Assert.fail("Installation did not fail")
            } catch (e : InstallException) {
                Assert.assertEquals("", PackageManager.SESSION_TEST_ONLY_CODE, e.errorCode)
            }
        }
    }

    @Test
    fun testFilenameCleaner() {
        val withSpace = "who does that.apk"
        var expected = "who_does_that.apk"
        var actual = cleanFilename(withSpace)
        Assert.assertEquals("Bad escape of '$withSpace'", expected, actual)

        val weirdName = "this'is\"a!bizar(name_.ap)k"
        expected = "this_is_a_bizar_name_.ap_k"
        actual = cleanFilename(weirdName)
        Assert.assertEquals("Bad escape for $weirdName", expected, actual)

        val validName = "A_file_Name-.apk"
        actual = cleanFilename(validName)
        Assert.assertEquals("Bad escape for $validName", validName, actual)
    }

    // Use API = 20, when streaming and multi-apk was not supported.
    @Test
    fun testLegacyStrategyMultipleApksFail() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 20)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apk1 = Files.createTempFile("adblib-tools_test.apk", null)
        val apk2 = Files.createTempFile("adblib-tools_test.apk", null)
        val apks = listOf(apk1, apk2)
        try {
            runBlocking {
                deviceServices.install(deviceSelector, apks, emptyList())
                Assert.fail("Installing multiple apks on API 20 should have failed")
            }
        } catch (e :IllegalStateException) {
            // Expected
        }
    }

    // Use API = 20, when streaming and multi-apk was not supported. this should be a remote install.
    @Test
    fun testLegacyStrategy() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 20)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apk = Files.createTempFile("adblib-tools_test.apk", null)
        val apks = listOf<Path>(apk)
        runBlocking {
          deviceServices.install(deviceSelector, apks, emptyList())
        }
        Assert.assertEquals(1, fakeDevice.pmLogs.size)
        Assert.assertEquals("install $INSTALL_APK_STAGING", fakeDevice.pmLogs[0])
    }

    // Use API = 23, just before CMD was introduced. This should use PM binary.
    @Test
    fun testPmStrategy() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 23)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.pmLogs.size)
        Assert.assertEquals("install-create", fakeDevice.pmLogs[0])
        Assert.assertEquals("install-commit 1234", fakeDevice.pmLogs[1])
    }

    // Use API = 24, just when CMD was introduced.
    @Test
    fun testCmdStrategy() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 24)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.cmdLogs.size)
        Assert.assertEquals("package install-create", fakeDevice.cmdLogs[0])
        Assert.assertEquals("package install-commit 1234", fakeDevice.cmdLogs[1])
    }

    // Use API = 29, just before ABB was introduced. This should be using CMD.
    @Test
    fun testCmdBeforeAbbStrategy() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 29)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.cmdLogs.size)
        Assert.assertEquals("package install-create", fakeDevice.cmdLogs[0])
        Assert.assertEquals("package install-commit 1234", fakeDevice.cmdLogs[1])
    }

    // Use API = 30 which should have ABB and ABB_EXEC
    @Test
    fun testAbbStrategy() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.abbLogs.size)
        Assert.assertEquals("package\u0000install-create", fakeDevice.abbLogs[0])
        Assert.assertEquals("package\u0000install-commit\u00001234", fakeDevice.abbLogs[1])
    }
}
