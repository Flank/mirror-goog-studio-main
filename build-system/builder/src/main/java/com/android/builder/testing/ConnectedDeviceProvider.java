/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * DeviceProvider for locally connected devices. Basically returns the list of devices that
 * are currently connected at the time {@link #init()} is called.
 */
public class ConnectedDeviceProvider extends DeviceProvider {

    @NonNull
    private final File adbLocation;

    private final long timeOut;

    @NonNull
    private final TimeUnit timeOutUnit;

    @NonNull
    private final ILogger iLogger;

    @NonNull
    private final List<ConnectedDevice> localDevices = Lists.newArrayList();

    @Nullable private LogAdapter logAdapter;

    /**
     * @param timeOutInMs The time out for each adb command, where 0 means wait forever.
     */
    public ConnectedDeviceProvider(@NonNull File adbLocation, int timeOutInMs,
            @NonNull ILogger logger) {
        this.adbLocation = adbLocation;
        this.timeOut = timeOutInMs;
        timeOutUnit = TimeUnit.MILLISECONDS;
        iLogger = logger;
    }

    @Override
    @NonNull
    public String getName() {
        return "connected";
    }

    @Override
    @NonNull
    public List<? extends DeviceConnector> getDevices() {
        return localDevices;
    }

    @Override
    public void init() throws DeviceException {
        // TODO: switch to devicelib
        if (timeOut > 0) {
            DdmPreferences.setTimeOut((int) timeOutUnit.toMillis(timeOut));
        } else {
            DdmPreferences.setTimeOut(Integer.MAX_VALUE);
        }
        logAdapter = new LogAdapter(iLogger);
        Log.addLogger(logAdapter);
        DdmPreferences.setLogLevel(Log.LogLevel.VERBOSE.getStringValue());
        AndroidDebugBridge.initIfNeeded(false /*clientSupport*/);

        try {

            AndroidDebugBridge bridge =
                    AndroidDebugBridge.createBridge(
                            adbLocation.getAbsolutePath(), false /*forceNewBridge*/);

            if (bridge == null) {
                throw new DeviceException(
                        "Could not create ADB Bridge. "
                                + "ADB location: "
                                + adbLocation.getAbsolutePath());
            }

            long getDevicesCountdown = timeOutUnit.toMillis(timeOut);
            final int sleepTime = 1000;
            while (!bridge.hasInitialDeviceList() && getDevicesCountdown >= 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    throw new DeviceException(e);
                }
                // If timeOut is 0, wait forever.
                if (timeOut != 0) {
                    getDevicesCountdown -= sleepTime;
                }
            }

            if (!bridge.hasInitialDeviceList()) {
                throw new DeviceException("Timeout getting device list.");
            }

            IDevice[] devices = bridge.getDevices();

            if (devices.length == 0) {
                throw new DeviceException("No connected devices!");
            }

            final String androidSerialsEnv = System.getenv("ANDROID_SERIAL");
            final boolean isValidSerial = androidSerialsEnv != null && !androidSerialsEnv.isEmpty();

            final Set<String> serials;
            if (isValidSerial) {
                serials = Sets.newHashSet(Splitter.on(',').split(androidSerialsEnv));
            } else {
                serials = Collections.emptySet();
            }

            final List<IDevice> filteredDevices = Lists.newArrayListWithCapacity(devices.length);
            for (IDevice iDevice : devices) {
                if (!isValidSerial || serials.contains(iDevice.getSerialNumber())) {
                    serials.remove(iDevice.getSerialNumber());
                    filteredDevices.add(iDevice);
                }
            }

            if (!serials.isEmpty()) {
                throw new DeviceException(
                        String.format(
                                "Connected device with serial%s '%s' not found!",
                                serials.size() == 1 ? "" : "s", Joiner.on("', '").join(serials)));
            }

            for (IDevice device : filteredDevices) {
                if (device.getState() == IDevice.DeviceState.ONLINE) {
                    localDevices.add(new ConnectedDevice(device, iLogger, timeOut, timeOutUnit));
                } else {
                    iLogger.lifecycle(
                            "Skipping device '%s' (%s): Device is %s%s.",
                            device.getName(),
                            device.getSerialNumber(),
                            device.getState(),
                            device.getState() == IDevice.DeviceState.UNAUTHORIZED
                                    ? ",\n"
                                            + "    see http://d.android.com/tools/help/adb.html#Enabling"
                                    : "");
                }
            }

            if (localDevices.isEmpty()) {
                if (isValidSerial) {
                    throw new DeviceException(
                            String.format(
                                    "Connected device with serial $1%s is not online.",
                                    androidSerialsEnv));
                } else {
                    throw new DeviceException("No online devices found.");
                }
            }
            // ensure device names are unique since many reports are keyed off of names.
            makeDeviceNamesUnique();
        } finally {
            terminate();
        }
    }

    private boolean hasDevicesWithDuplicateName() {
        Set<String> deviceNames = new HashSet<String>();
        for (ConnectedDevice device : localDevices) {
            if (!deviceNames.add(device.getName())) {
                return true;
            }
        }
        return false;
    }

    private void makeDeviceNamesUnique() {
        if (hasDevicesWithDuplicateName()) {
            for (ConnectedDevice device : localDevices) {
                device.setNameSuffix(device.getSerialNumber());
            }
        }
        if (hasDevicesWithDuplicateName()) {
            // still have duplicates :/ just use a counter.
            int counter = 0;
            for (ConnectedDevice device : localDevices) {
                device.setNameSuffix(device.getSerialNumber() + "-" + counter);
                counter ++;
            }
        }

    }

    @Override
    public void terminate() throws DeviceException {
        if (logAdapter != null) {
            Log.removeLogger(logAdapter);
            logAdapter = null;
        }
    }

    @Override
    public int getTimeoutInMs() {
        return (int) timeOutUnit.toMillis(timeOut);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    private static final class LogAdapter implements Log.ILogOutput {

        @NonNull private final ILogger logger;

        private LogAdapter(@NonNull ILogger logger) {
            this.logger = logger;
        }

        @Override
        public void printLog(Log.LogLevel logLevel, String tag, String message) {
            switch (logLevel) {
                case VERBOSE:
                    break;
                case DEBUG:
                    break;
                case INFO:
                    logger.info("[%1$s]: %2$s", tag, message);
                    break;
                case WARN:
                    logger.warning("[%1$s]: %2$s", tag, message);
                    break;
                case ERROR:
                    logger.error(null, "[%1$s]: %2$s", tag, message);
                    break;
                case ASSERT:
                    logger.error(null, "[%1$s]: %2$s", tag, message);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown log level " + logLevel);
            }
        }

        @Override
        public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
            printLog(logLevel, tag, message);
        }
    }
}
