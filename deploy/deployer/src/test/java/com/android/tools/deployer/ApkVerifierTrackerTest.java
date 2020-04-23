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

import static com.android.ddmlib.IDevice.CHANGE_STATE;
import static com.android.ddmlib.IDevice.PROP_BUILD_CODENAME;
import static com.android.tools.deployer.ApkVerifierTracker.SKIP_VERIFICATION_OPTION;
import static com.android.tools.deployer.ApkVerifierTracker.getSkipVerificationInstallationFlag;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.shell.GetProp;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApkVerifierTrackerTest {
    private static final long WAIT_TIME_MS = TimeUnit.SECONDS.toMillis(10);
    private static final String FIRST_PACKAGE = "package 0";
    private static final String SECOND_PACKAGE = "package 1";

    private FakeAdbServer fakeAdbServer;
    private final FakeDeviceHandler handler = new FakeDeviceHandler();
    private AndroidDebugBridge bridge;

    private Set<IDevice> disabledDevices;
    private final List<IDevice> devices = new ArrayList<>();

    @Before
    public void before() throws Exception {
        // Build the server and configure it to use the default ADB command handlers.
        fakeAdbServer =
                new FakeAdbServer.Builder()
                        .installDefaultCommandHandlers()
                        .addDeviceHandler(handler)
                        .build();

        // Start server execution.
        fakeAdbServer.start();

        // Start ADB with fake server and its port.
        AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());

        FakeDevice oDevice = new FakeDevice("8.0", 26);
        oDevice.getShell().addCommand(new GetProp());

        FakeDevice rDeviceDp1 = new FakeDevice("10.0", 29);
        rDeviceDp1.getProps().put(PROP_BUILD_CODENAME, "R");
        rDeviceDp1.getProps().put("ro.build.version.preview_sdk", "1");
        rDeviceDp1.getShell().addCommand(new GetProp());

        FakeDevice rDeviceDp2 = new FakeDevice("10.0", 29);
        rDeviceDp2.getProps().put(PROP_BUILD_CODENAME, "R");
        rDeviceDp2.getProps().put("ro.build.version.preview_sdk", "2");
        rDeviceDp2.getShell().addCommand(new GetProp());

        FakeDevice rDevice = new FakeDevice("11.0", 30);
        rDevice.getShell().addCommand(new GetProp());

        List<FakeDevice> fakeDevices = Lists.newArrayList(oDevice, rDeviceDp1, rDeviceDp2, rDevice);

        // Get the bridge synchronously, since we're in test mode.
        AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
        bridge = AndroidDebugBridge.createBridge();

        // Wait for ADB.
        waitFor(() -> bridge.isConnected() && bridge.hasInitialDeviceList());

        for (FakeDevice device : fakeDevices) {
            handler.connect(device, fakeAdbServer);
        }

        // Wait until all our devices are recognized by FakeAdb.
        waitFor(() -> fakeAdbServer.getDeviceListCopy().get().size() == fakeDevices.size());

        List<DeviceState> deviceStates = fakeAdbServer.getDeviceListCopy().get();
        Map<FakeDevice, IDevice> devicesMap = new HashMap<>();
        // Wait until all devices are recognized by ADB/ddmlib.
        waitUntilAdbHasAllDevices();
        // Map FakeDevices to their corresponding IDevices.
        for (FakeDevice device : fakeDevices) {
            DeviceState state =
                    deviceStates.stream().filter(device::isDevice).findFirst().orElse(null);
            assertNotNull(state);
            IDevice iDevice =
                    Arrays.stream(AndroidDebugBridge.getBridge().getDevices())
                            .filter(d -> state.getDeviceId().equals(d.getSerialNumber()))
                            .findFirst()
                            .orElse(null);
            assertNotNull(iDevice);
            devicesMap.put(device, iDevice);
        }

        disabledDevices = Sets.newHashSet(devicesMap.get(oDevice), devicesMap.get(rDeviceDp1));
        devices.addAll(devicesMap.values());
    }

    @After
    public void after() throws InterruptedException {
        fakeAdbServer.stop();
        boolean status = fakeAdbServer.awaitServerTermination(WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        assertTrue(status);

        ApkVerifierTracker.clear();
        AndroidDebugBridge.terminate();
        AndroidDebugBridge.disableFakeAdbServerMode();
    }

    @Test
    public void verifyOnFirstInstall() {
        for (IDevice device : devices) {
            assertNull(getSkipVerificationInstallationFlag(device, FIRST_PACKAGE));
            assertNull(getSkipVerificationInstallationFlag(device, SECOND_PACKAGE));
        }
    }

    @Test
    public void skipVerifyOnSecondInstall() {
        for (IDevice device : devices) {
            assertNull(getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, 0));

            // Ensure that a fast followup install does not incur verification.
            if (disabledDevices.contains(device)) {
                assertNull(getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, 1));
            } else {
                assertEquals(
                        SKIP_VERIFICATION_OPTION,
                        getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, 1));
            }

            // Ensure skipping doesn't bleed over to a different package.
            assertNull(getSkipVerificationInstallationFlag(device, SECOND_PACKAGE, 2));
        }
    }

    @Test
    public void reverifiesAfterAnHourThenNoVerification() {
        for (IDevice device : devices) {
            assertNull(getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, 0));

            long hour = TimeUnit.HOURS.toMillis(1);

            // Ensure that an install before an hour skips verification.
            if (disabledDevices.contains(device)) {
                assertNull(getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, hour - 1));
            } else {
                assertEquals(
                        SKIP_VERIFICATION_OPTION,
                        getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, hour - 1));
            }

            // Ensure that an install on or after an hour gets verified, and that timestamps don't
            // get updated erroneously.
            assertNull(getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, hour));

            // Ensure that a fast subsequent install skips verification.
            if (disabledDevices.contains(device)) {
                assertNull(getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, hour + 1));
            } else {
                assertEquals(
                        SKIP_VERIFICATION_OPTION,
                        getSkipVerificationInstallationFlag(device, FIRST_PACKAGE, hour + 1));
            }
        }
    }

    private static void waitFor(@NonNull Callable<Boolean> callable) throws Exception {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_TIME_MS) {
            if (callable.call()) {
                return;
            }
            //noinspection BusyWait
            Thread.sleep(100);
        }
    }

    private void waitUntilAdbHasAllDevices() throws Exception {
        List<DeviceState> deviceStates = fakeAdbServer.getDeviceListCopy().get();
        for (DeviceState deviceState : deviceStates) {
            waitUntilAdbHasDevice(deviceState.getDeviceId());
        }
    }

    private static void waitUntilAdbHasDevice(@NonNull String deviceId) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AndroidDebugBridge.IDeviceChangeListener deviceChangeListener =
                new AndroidDebugBridge.IDeviceChangeListener() {
                    @Override
                    public void deviceConnected(@NonNull IDevice device) {
                        deviceChanged(device, CHANGE_STATE);
                    }

                    @Override
                    public void deviceDisconnected(@NonNull IDevice device) {
                        deviceChanged(device, CHANGE_STATE);
                    }

                    @Override
                    public void deviceChanged(@NonNull IDevice device, int changeMask) {
                        if (deviceId.equals(device.getSerialNumber()) && device.isOnline()) {
                            AndroidDebugBridge.removeDeviceChangeListener(this);
                            countDownLatch.countDown();
                        }
                    }
                };

        AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener);
        waitFor(() -> AndroidDebugBridge.getBridge().hasInitialDeviceList());
        for (IDevice device : AndroidDebugBridge.getBridge().getDevices()) {
            deviceChangeListener.deviceConnected(device);
        }
        countDownLatch.await();
    }
}
