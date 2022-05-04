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
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.InstallException
import com.android.adblib.tools.PMClient
import com.android.adblib.tools.PMClient.Companion.cleanFilename
import com.android.adblib.tools.install
import com.android.fakeadbserver.services.PackageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

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
                val client = PMClient(deviceServices, deviceSelector)
                client.commit(deviceSelector, "12345")
                Assert.fail("Installation did not fail")
            } catch (e : Exception) {
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
                val client = PMClient(deviceServices, deviceSelector)
                client.commit(deviceSelector, PackageManager.FAIL_ME_SESSION_TEST_ONLY)
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

}
