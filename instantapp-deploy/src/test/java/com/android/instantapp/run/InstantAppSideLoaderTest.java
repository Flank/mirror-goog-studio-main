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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.instantapp.utils.InstantAppTests;
import com.google.common.collect.Lists;
import java.io.File;
import org.junit.Test;

/** Unit tests for {@link InstantAppSideLoader}. */
public class InstantAppSideLoaderTest {
    @Test
    public void testUninstallApp() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setResponseToCommand("pm path applicationId", "path/to/applicationId")
                        .getDevice();
        when(device.uninstallPackage(eq("applicationId"))).thenReturn(null);
        InstantAppSideLoader installer = new InstantAppSideLoader("applicationId", new File("zip"));

        installer.uninstallAppIfInstalled(device);
        verify(device, times(1)).uninstallPackage(eq("applicationId"));
    }

    @Test
    public void testInstallFailsWhenUninstallAppFails() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setGoogleAccountLogged()
                        .setResponseToCommand("pm path applicationId", "path/to/applicationId")
                        .getDevice();
        when(device.uninstallPackage(eq("applicationId"))).thenThrow(InstallException.class);
        InstantAppSideLoader installer = new InstantAppSideLoader("applicationId", new File("zip"));

        try {
            installer.uninstallAppIfInstalled(device);
            fail();
        } catch (InstantAppRunException e) {
            assertEquals(InstantAppRunException.ErrorType.INSTALL_FAILED, e.getErrorType());
        }
    }

    @Test
    public void testDoesNotUninstallWhenNotInstalled() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setGoogleAccountLogged()
                        .setResponseToCommand("pm path applicationId", "")
                        .getDevice();
        InstantAppSideLoader installer = new InstantAppSideLoader("applicationId", new File("zip"));

        installer.uninstallAppIfInstalled(device);
        verify(device, times(0)).uninstallPackage(eq("applicationId"));
    }

    @Test
    public void testEntireInstallPostO() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setGoogleAccountLogged()
                        .setApiLevel(26, "O")
                        .setResponseToCommand("pm path applicationId", "path/to/applicationId")
                        .getDevice();
        InstantAppSideLoader installer =
                new InstantAppSideLoader("applicationId", Lists.newArrayList(new File("apk")));
        installer.install(device);
        // No exception is thrown
    }
}
