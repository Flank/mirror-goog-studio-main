/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.instantapp.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.instantapp.utils.InstantAppTests;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.UUID;
import org.junit.Test;

/** Unit tests for {@link PreOSideLoader}. */
public class PreOSideLoaderTest {

    @Test
    public void testReadIapkSuccessful() throws Throwable {
        UUID installToken = UUID.randomUUID();
        File zip = new File("zip");
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setOnline()
                        .setLogcat(
                                Lists.newArrayList(
                                        "[ 05-31 21:21:37.562  2775: 3670 I/IapkLoadService ]\nLOAD_SUCCESS token="
                                                + installToken))
                        .getDevice();
        PreOSideLoader installer = new PreOSideLoader(zip, new RunListener.NullListener());
        installer.readIapk(device, "path/to/folder", installToken);
        // Checking no exception is thrown
    }

    @Test
    public void testReadIapkFailsWhenErrorMessage() throws Throwable {
        UUID installToken = UUID.randomUUID();
        File zip = new File("zip");
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setOnline()
                        .setLogcat(
                                Lists.newArrayList(
                                        "[ 05-31 21:21:37.562  2775: 3670 E/IapkLoadService ]\nDevice is not provisioned with Instant Apps SDK.token="
                                                + installToken))
                        .getDevice();
        PreOSideLoader installer = new PreOSideLoader(zip, new RunListener.NullListener());
        try {
            installer.readIapk(device, "path/to/folder", installToken);
            fail();
        } catch (InstantAppRunException e) {
            assertEquals(InstantAppRunException.ErrorType.READ_IAPK_FAILED, e.getErrorType());
            assertEquals(
                    "Failure when trying to read bundle. Device is not provisioned with Instant Apps SDK.",
                    e.getMessage());
        }
    }

    @Test
    public void testReadIapkTimesOutWhenNoMessage() throws Throwable {
        UUID installToken = UUID.randomUUID();
        File zip = new File("zip");
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setOnline()
                        .setLogcat(Lists.newArrayList())
                        .getDevice();
        PreOSideLoader installer = new PreOSideLoader(zip, new RunListener.NullListener());
        try {
            installer.readIapk(device, "path/to/folder", installToken);
            fail();
        } catch (InstantAppRunException e) {
            assertEquals(InstantAppRunException.ErrorType.READ_IAPK_TIMEOUT, e.getErrorType());
        }
    }

    @Test
    public void testInstallFailsWhenNotLoggedIn() throws Throwable {
        File zip = new File("zip");
        IDevice device = new InstantAppTests.DeviceGenerator().getDevice();
        PreOSideLoader installer = new PreOSideLoader(zip, new RunListener.NullListener());
        try {
            installer.install(device);
            fail();
        } catch (InstantAppRunException e) {
            assertEquals(InstantAppRunException.ErrorType.NO_GOOGLE_ACCOUNT, e.getErrorType());
        }
    }

    @Test
    public void testInstallFailsWhenPushFails() throws Throwable {
        File zip = new File("zip");
        IDevice device = new InstantAppTests.DeviceGenerator().setGoogleAccountLogged().getDevice();
        doThrow(AdbCommandRejectedException.class).when(device).pushFile(anyString(), anyString());
        PreOSideLoader installer = new PreOSideLoader(zip, new RunListener.NullListener());
        try {
            installer.install(device);
            fail();
        } catch (InstantAppRunException e) {
            assertEquals(InstantAppRunException.ErrorType.ADB_FAILURE, e.getErrorType());
        }
    }
}
