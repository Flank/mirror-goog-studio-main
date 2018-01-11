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
package com.android.instantapp.provision;

import static com.android.instantapp.sdk.InstantAppSdkTests.getInstantAppSdk;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.instantapp.utils.InstantAppTests;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ProvisionRunner}. */
public class ProvisionRunnerTest {
    private ProvisionRunner myProvisionRunner;

    @Before
    public void setUp() throws Exception {
        myProvisionRunner = new ProvisionRunner(getInstantAppSdk());
    }

    @Test
    public void testRunnerCreatedCorrectly() {
        assertNotNull(myProvisionRunner.getMetadata());
        assertNotNull(myProvisionRunner.getCache());
        assertTrue(myProvisionRunner.getCache().isEmpty());
        assertNotNull(myProvisionRunner.getListener());
    }

    @Test
    public void testSucceedsPostO() throws Throwable {
        IDevice device = new InstantAppTests.DeviceGenerator().setApiLevel(25, "O").getDevice();
        myProvisionRunner.runProvision(device);
        assertEquals(
                ProvisionRunner.ProvisionState.Step.FINISHED,
                myProvisionRunner.getCache().get(device).lastSucceeded);
    }

    @Test
    public void testFailsWhenArchNotSupported() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setApiLevel(23, null)
                        .setArchitectures("mips")
                        .getDevice();
        assertProvisionException(device, ProvisionException.ErrorType.ARCH_NOT_SUPPORTED);
        assertEquals(
                ProvisionRunner.ProvisionState.Step.CHECK_POSTO,
                myProvisionRunner.getCache().get(device).lastSucceeded);
    }

    @Test
    public void testDeviceEnabled() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setApiLevel(23, null)
                        .setArchitectures("x86")
                        .setHardware("fakeRanchu")
                        .setGoogleAccountLogged()
                        .getDevice();
        myProvisionRunner.runProvision(device);
        assertEquals(
                ProvisionRunner.ProvisionState.Step.FINISHED,
                myProvisionRunner.getCache().get(device).lastSucceeded);
    }

    @Test
    public void testFailsWhenNotLoggedInGoogleAccount() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setApiLevel(23, null)
                        .setArchitectures("x86")
                        .setHardware("ranchu")
                        .getDevice();
        assertProvisionException(device, ProvisionException.ErrorType.NO_GOOGLE_ACCOUNT);
        assertEquals(
                ProvisionRunner.ProvisionState.Step.CHECK_DEVICE,
                myProvisionRunner.getCache().get(device).lastSucceeded);
    }

    @Test
    public void testFailsWhenInstallFails() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setApiLevel(23, null)
                        .setArchitectures("x86")
                        .setHardware("ranchu")
                        .setGoogleAccountLogged()
                        .getDevice();
        doThrow(new InstallException("Failed."))
                .when(device)
                .installPackage(any(), eq(true), eq("-d"));
        assertProvisionException(device, ProvisionException.ErrorType.INSTALL_FAILED);
        assertEquals(
                ProvisionRunner.ProvisionState.Step.GSERVICES,
                myProvisionRunner.getCache().get(device).lastSucceeded);
    }

    @Test
    public void testSucceedsIfNoProblem() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setApiLevel(23, null)
                        .setArchitectures("x86")
                        .setHardware("ranchu")
                        .setGoogleAccountLogged()
                        .getDevice();
        myProvisionRunner.runProvision(device);
        assertEquals(
                ProvisionRunner.ProvisionState.Step.FINISHED,
                myProvisionRunner.getCache().get(device).lastSucceeded);
    }

    @Test
    public void testPlayStoreEmulatorsCheckIn() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setApiLevel(24, null)
                        .setArchitectures("x86")
                        .setHardware("ranchu")
                        .setOsBuildType("release-keys")
                        .setIsEmulator(true)
                        .setGoogleAccountLogged()
                        .getDevice();
        myProvisionRunner.runProvision(device);

        verify(device, times(1))
                .executeShellCommand(eq("am broadcast -a android.server.checkin.CHECKIN"), any());

        assertEquals(
                ProvisionRunner.ProvisionState.Step.FINISHED,
                myProvisionRunner.getCache().get(device).lastSucceeded);
    }

    private void assertProvisionException(
            @NonNull IDevice device, @NonNull ProvisionException.ErrorType errorType) {
        try {
            myProvisionRunner.runProvision(device);
            fail("No exception thrown.");
        } catch (ProvisionException e) {
            assertEquals(errorType, e.getErrorType());
        }
    }
}
