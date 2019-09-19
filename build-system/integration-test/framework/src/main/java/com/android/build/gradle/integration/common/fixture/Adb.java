/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture;

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.utils.AbiMatcher;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.internal.core.Abi;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.test.multidevice.DevicePoolClient;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;


/**
 * Utilities for handling real devices.
 *
 * <p>To request a device use {@link #getDevice(Matcher, Matcher)}. This reserves the device from
 * the device pool, allowing the connected integration tests to run in parallel without interfering
 * with each other.
 *
 * <p>At the end of the test method, the device is returned to the pool.
 */
public class Adb implements TestRule {

    private List<String> devicesToReturn = Lists.newArrayList();
    private boolean exclusiveAccess = false;
    private String displayName;

    @Override
    public Statement apply(@NonNull final Statement base, @NonNull final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (Closeable ignored = Adb.this::close) {
                    displayName = description.getDisplayName();
                    base.evaluate();
                }
            }
        };
    }

    public void close() throws IOException {
        if (!devicesToReturn.isEmpty()) {
            DevicePoolClient.returnDevices(devicesToReturn, displayName);
            devicesToReturn.clear();
        } else if (exclusiveAccess) {
            DevicePoolClient.returnAllDevices(displayName);
        }
    }

    /** Reserves and returns a connected device that has a version that satisfies the matcher. */
    @NonNull
    public IDevice getDevice(
            @NonNull Matcher<AndroidVersion> versionMatcher,
            @NonNull Matcher<List<Abi>> abiMatcher) {
        IDevice device =
                getDevice(
                        versionMatcher,
                        abiMatcher,
                        error -> {
                            throw new AssertionError(error);
                        });
        assert device != null;
        return device;
    }

    private StringDescription getMismatchDescription(
            IDevice device,
            @NonNull Matcher<AndroidVersion> versionMatcher,
            @NonNull Matcher<List<Abi>> abiMatcher) {
        StringDescription description = new StringDescription();

        String indent = "    ";

        description.appendText(indent).appendText(device.toString()).appendText(": ");

        // Since no device was found, it is guaranteed that one of these will be true
        if (!versionMatcher.matches(device.getVersion())) {
            description.appendText("\n" + indent + indent);

            versionMatcher.describeMismatch(device.getVersion(), description);
        }

        List<Abi> abis = device.getAbis().stream().map(Abi::getByName).collect(Collectors.toList());

        if (!abiMatcher.matches(abis)) {
            description.appendText("\n" + indent + indent);

            abiMatcher.describeMismatch(abis, description);
        }

        return description;
    }

    /**
     * Reserves and returns a connected device that has a version that satisfies the matcher.
     *
     * @param errorHandler called with a descriptive error message if a device cannot be allocated.
     */
    @Nullable
    public IDevice getDevice(
            @NonNull Matcher<AndroidVersion> versionMatcher,
            @NonNull Matcher<List<Abi>> abiMatcher,
            @NonNull Consumer<String> errorHandler) {
        if (exclusiveAccess) {
            throw new IllegalStateException("Cannot call both getDevice() and exclusiveAccess() "
                    + "in one test");
        }

        IDevice[] devices = getBridge().getDevices();

        List<String> possibleDeviceSerials = Lists.newArrayList();
        for (IDevice device: devices) {
            if (versionMatcher.matches(device.getVersion())
                    && abiMatcher.matches(
                            device.getAbis()
                                    .stream()
                                    .map(Abi::getByName)
                                    .collect(Collectors.toList()))) {
                possibleDeviceSerials.add(device.getSerialNumber());
            }
        }

        if (!possibleDeviceSerials.isEmpty()) {
            String deviceSerial;
            try {
                deviceSerial = DevicePoolClient.reserveDevice(possibleDeviceSerials, displayName);
                devicesToReturn.add(deviceSerial);
            } catch (IOException e) {
                throw new AssertionError("Failed to reserve device" + Throwables
                        .getStackTraceAsString(e));
            }

            for (IDevice device : devices) {
                if (device.getSerialNumber().equals(deviceSerial)) {
                    return device;
                }
            }
        }

        // Failed to find, make a pretty error message.
        StringBuilder errorMessage =
                new StringBuilder("Test requires device that matches\n")
                        .append("    " + StringDescription.toString(versionMatcher) + "\n")
                        .append("    " + StringDescription.toString(abiMatcher));

        errorMessage.append("\nConnected Devices:\n");

        for (IDevice device: devices) {
            errorMessage
                    .append(getMismatchDescription(device, versionMatcher, abiMatcher).toString())
                    .append("\n");
        }

        errorHandler.accept(errorMessage.toString());
        return null;
    }

    public IDevice getDevice(int version) {
        return getDevice(AndroidVersionMatcher.exactly(version), AbiMatcher.anyAbi());
    }

    /**
     * Reserves all of the devices.
     *
     * This is useful for integration tests that exercise the ordinary connectedDeviceProvider,
     * either through the connectedCheck, install or uninstall tasks.
     */
    public void exclusiveAccess() throws IOException {
        if (!devicesToReturn.isEmpty()) {
            throw new IllegalStateException("Cannot call both getDevice() and exclusiveAccess() "
                    + "in one test");
        }
        exclusiveAccess = true;
        DevicePoolClient.reserveAllDevices(displayName);

    }

    private static Supplier<AndroidDebugBridge> sAdbGetter = Suppliers.memoize(
            () -> {
                AndroidDebugBridge.initIfNeeded(false /*clientSupport*/);
                AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
                        SdkHelper.getAdb().getAbsolutePath(), false /*forceNewBridge*/);
                assertNotNull("Debug bridge", bridge);
                long timeOut = DeviceHelper.DEFAULT_ADB_TIMEOUT_MSEC;
                int sleepTime = 1000;
                while (!bridge.hasInitialDeviceList() && timeOut > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    timeOut -= sleepTime;
                }

                if (timeOut <= 0 && !bridge.hasInitialDeviceList()) {
                    throw new RuntimeException("Timeout getting device list.");
                }
                return bridge;
            });


    private static AndroidDebugBridge getBridge() {
        return sAdbGetter.get();
    }
}
