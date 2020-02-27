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
package com.android.tools.deployer;

import static com.android.tools.deployer.ApkVerifierTracker.SKIP_VERIFICATION_OPTION;
import static com.android.tools.deployer.ApkVerifierTracker.getSkipVerificationInstallationFlag;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApkVerifierTrackerTest {
    private static final String FIRST_PACKAGE = "package 0";
    private static final String SECOND_PACKAGE = "package 1";

    private IDevice oDevice;
    private IDevice rDevice;

    @Before
    public void before() {
        oDevice = mock(IDevice.class);
        when(oDevice.supportsFeature(IDevice.Feature.REAL_PKG_NAME)).thenReturn(false);
        when(oDevice.getSerialNumber()).thenReturn("oDevice");
        rDevice = mock(IDevice.class);
        when(rDevice.supportsFeature(IDevice.Feature.REAL_PKG_NAME)).thenReturn(true);
        when(rDevice.getSerialNumber()).thenReturn("rDevice");
    }

    @After
    public void after() {
        ApkVerifierTracker.clear();
    }

    @Test
    public void verifyOnFirstInstall() {
        assertNull(getSkipVerificationInstallationFlag(oDevice, FIRST_PACKAGE));
        assertNull(getSkipVerificationInstallationFlag(oDevice, SECOND_PACKAGE));
        assertNull(getSkipVerificationInstallationFlag(rDevice, FIRST_PACKAGE));
        assertNull(getSkipVerificationInstallationFlag(rDevice, SECOND_PACKAGE));
    }

    @Test
    public void skipVerifyOnSecondInstall() {
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(oDevice, FIRST_PACKAGE, 0));
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(rDevice, FIRST_PACKAGE, 0));

        // Ensure that a fast followup install does not incur verification.
        assertNull(
                SKIP_VERIFICATION_OPTION,
                ApkVerifierTracker.getSkipVerificationInstallationFlag(oDevice, FIRST_PACKAGE, 1));
        assertEquals(
                SKIP_VERIFICATION_OPTION,
                ApkVerifierTracker.getSkipVerificationInstallationFlag(rDevice, FIRST_PACKAGE, 1));

        // Ensure skipping doesn't bleed over to a different package.
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(oDevice, SECOND_PACKAGE, 2));
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(rDevice, SECOND_PACKAGE, 2));
    }

    @Test
    public void reverifiesAfterAnHourThenNoVerification() {
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(oDevice, FIRST_PACKAGE, 0));
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(rDevice, FIRST_PACKAGE, 0));

        long hour = TimeUnit.HOURS.toMillis(1);

        // Ensure that an install before an hour skips verification.
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(
                        oDevice, FIRST_PACKAGE, hour - 1));
        assertEquals(
                SKIP_VERIFICATION_OPTION,
                ApkVerifierTracker.getSkipVerificationInstallationFlag(
                        rDevice, FIRST_PACKAGE, hour - 1));

        // Ensure that an install on or after an hour gets verified, and that timestamps don't get
        // updated erroneously.
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(
                        oDevice, FIRST_PACKAGE, hour));
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(
                        rDevice, FIRST_PACKAGE, hour));

        // Ensure that a fast subsequent install skips verification.
        assertNull(
                ApkVerifierTracker.getSkipVerificationInstallationFlag(
                        oDevice, FIRST_PACKAGE, hour + 1));
        assertEquals(
                SKIP_VERIFICATION_OPTION,
                ApkVerifierTracker.getSkipVerificationInstallationFlag(
                        rDevice, FIRST_PACKAGE, hour + 1));
    }
}
