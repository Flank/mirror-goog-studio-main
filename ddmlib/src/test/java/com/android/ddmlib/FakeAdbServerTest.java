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

package com.android.ddmlib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.adbserver.DeviceState;
import com.android.ddmlib.adbserver.FakeAdbServer;
import com.android.ddmlib.logcat.LogCatListener;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import com.android.testutils.TestResources;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class FakeAdbServerTest {

    private static final String SERIAL = "test_device_001";

    private static final String MANUFACTURER = "Google";

    private static final String MODEL = "Nexus Silver";

    private static final String RELEASE = "8.0";

    private static final String SDK = "26";

    private static final int REASONABLE_TIMEOUT_S = 5;

    private static final String ADDITIONAL_TEST_MESSAGE = "nope! fooled you!";

    /**
     * Very basic test example. Remove {@code @Ignore} if you wish to run an interactive server.
     *
     * TODO refactor ddmlib to accept a pre-constructed adb server connection and remove "@Ignore".
     */
    @Test
    @Ignore
    public void testInteractiveServer() throws IOException {
        // Build the server and configure it to use the default ADB command handlers.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.setPort(AndroidDebugBridge.DEFAULT_ADB_PORT);
        builder.installDefaultCommandHandlers();
        FakeAdbServer server = builder.build();

        // Connect a test device to simulate device connection before server bring-up.
        server.connectDevice(SERIAL, MANUFACTURER, MODEL, RELEASE, SDK,
                DeviceState.HostConnectionType.USB);

        // Start server execution.
        server.start();

        // Optional: Since the server lives on a separate thread, we can pause the test to poke at
        // the server.
        try {
            server.awaitServerTermination();
        } catch (InterruptedException ignored) {
            // Exiting, just ignore interruptions.
        }
    }

    /**
     * Sample test to ensure that the pipeline works. This also tests that logcat handling is
     * working.
     *
     * TODO refactor ddmlib to accept a pre-constructed adb server connection and remove "@Ignore".
     */
    @Test
    @Ignore
    public void testLogcatPipeline() throws IOException, ExecutionException, InterruptedException {
        // Setup the server to default configuration.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.setPort(AndroidDebugBridge.DEFAULT_ADB_PORT);
        builder.installDefaultCommandHandlers();
        FakeAdbServer server = builder.build();

        // Pre-connect a device before server startup.
        DeviceState device = server.connectDevice(SERIAL, MANUFACTURER, MODEL, RELEASE, SDK,
                DeviceState.HostConnectionType.USB).get();
        device.setDeviceStatus(IDevice.DeviceState.ONLINE);

        // Load a sample test logcat output and simulate a device with existing buffered logcat
        // output prior to starting the server.
        File f = TestResources.getFile(getClass(), "/logcat.txt");
        assertTrue(f.exists());
        device.addLogcatMessage(Files.toString(f, Charsets.UTF_8));

        try {
            server.start();

            // Start up ADB.
            File adbPath = TestUtils.getSdkFile("platform-tools/adb");
            AndroidDebugBridge.initIfNeeded(false);
            AndroidDebugBridge bridge = AndroidDebugBridge
                    .createBridge(adbPath.getCanonicalPath(), false);
            assertNotNull("Debug bridge", bridge);

            // Wait for the device to get recognized by ddmlib.
            CustomDeviceListener deviceListener = new CustomDeviceListener();
            AndroidDebugBridge.addDeviceChangeListener(deviceListener);
            assertTrue(
                    deviceListener.waitForDeviceConnection(REASONABLE_TIMEOUT_S, TimeUnit.SECONDS));

            IDevice[] iDevices = bridge.getDevices();
            assertEquals(iDevices.length, 1);
            IDevice iDevice = iDevices[0];
            assertEquals(iDevice.getSerialNumber(), SERIAL);

            // Retrieve logcat contents.
            LogCatReceiverTask logCatReceiverTask = new LogCatReceiverTask(iDevice);
            CustomLogCatListener listener = new CustomLogCatListener();
            logCatReceiverTask.addLogCatListener(listener);
            Thread logcatThread = new Thread(logCatReceiverTask);
            logcatThread.start();

            // Ensure all the content in the test file is retrieved.
            assertTrue(listener.waitForCompletion(REASONABLE_TIMEOUT_S, TimeUnit.SECONDS));

            // Do an additional check that the pipeline is still working after the file (second
            // send).
            listener.getReadyForNextMessage();
            device.addLogcatMessage("[ 08-29 14:37:30.000   779:  123 D/ShutdownTestService ]\n"
                    + ADDITIONAL_TEST_MESSAGE + "\n\n");
            LogCatMessage echo = listener
                    .waitForNextMessage(REASONABLE_TIMEOUT_S, TimeUnit.SECONDS);
            assertNotNull(echo);
            assertEquals(echo.getMessage(), ADDITIONAL_TEST_MESSAGE);

            logCatReceiverTask.stop();
        } finally {
            server.stop();
        }
    }

    private static final class CustomLogCatListener implements LogCatListener {

        // This is the index of the last message in logcat.txt.
        private static final int LAST_LOGCAT_MESSAGE_INDEX = 8;

        private boolean mReadTestStart = false;

        private CountDownLatch mReadTestEnd = new CountDownLatch(1);

        private int mMessagesRead = 0;

        private volatile CountDownLatch mNotifyNextMessageLatch = null;

        private volatile LogCatMessage mLastMessage = null;

        @Override
        public void log(List<LogCatMessage> msgList) {
            for (LogCatMessage message : msgList) {
                if (mMessagesRead < LAST_LOGCAT_MESSAGE_INDEX) {
                    if (!mReadTestStart) {
                        assertEquals(message.getMessage(), "starting fake adb server test");
                        mReadTestStart = true;
                    }
                } else if (mMessagesRead == LAST_LOGCAT_MESSAGE_INDEX) {
                    assertEquals(message.getMessage(), "ending fake adb server test");
                    mReadTestEnd.countDown();
                } else if (mNotifyNextMessageLatch != null) {
                    mLastMessage = message;
                    mNotifyNextMessageLatch.countDown();
                }

                mMessagesRead++;
            }
        }

        public boolean waitForCompletion(long timeout, @NonNull TimeUnit unit)
                throws InterruptedException {
            return mReadTestEnd.await(timeout, unit);
        }

        public void getReadyForNextMessage() {
            mNotifyNextMessageLatch = new CountDownLatch(1);
        }

        @Nullable
        public LogCatMessage waitForNextMessage(long timeout, @NonNull TimeUnit unit) {
            try {
                if (mNotifyNextMessageLatch.await(timeout, unit)) {
                    return mLastMessage;
                } else {
                    return null;
                }
            } catch (InterruptedException e) {
                return null;
            }
        }
    }

    private static final class CustomDeviceListener
            implements AndroidDebugBridge.IDeviceChangeListener {

        private CountDownLatch mDeviceConnectionCountdown = new CountDownLatch(1);

        @Override
        public void deviceConnected(@NonNull IDevice device) {
            mDeviceConnectionCountdown.countDown();
        }

        @Override
        public void deviceDisconnected(@NonNull IDevice device) {

        }

        @Override
        public void deviceChanged(@NonNull IDevice device, int changeMask) {

        }

        public boolean waitForDeviceConnection(long timeout, @NonNull TimeUnit unit) {
            try {
                return mDeviceConnectionCountdown.await(timeout, unit);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }
}
