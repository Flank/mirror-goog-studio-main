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
import com.android.adblib.tools.UninstallResult
import com.android.adblib.tools.uninstall
import com.android.fakeadbserver.shellcommandhandlers.ShellConstants
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class TestUninstall : TestInstallBase() {

    @Test
    fun testUninstallSuccess() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        var r : UninstallResult
        runBlocking {
           r = deviceServices.uninstall(deviceSelector, 0, "com.foo.bar" )
        }

        Assert.assertEquals("Success", r.output)
    }

    @Test
    fun testUninstallFailure() {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceServices = createDeviceServices(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        var r : UninstallResult
        runBlocking {
            r = deviceServices.uninstall(deviceSelector, 0, ShellConstants.NON_INSTALLED_APP_ID)
        }

        Assert.assertEquals(UninstallResult.Status.FAILURE, r.status)
    }

}
