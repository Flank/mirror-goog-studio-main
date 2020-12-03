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

package com.android.ddmlib;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.logcat.LogCatListener;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.PortForwarder;
import com.android.fakeadbserver.devicecommandhandlers.ExecCommandHandler;
import com.android.testutils.TestResources;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class IntegrationTest {

    private static final String LOCAL_HOST = "127.0.0.1";

    private static final int MAX_PORT = 65535;

    private static final String SERIAL = "test_device_001";

    private static final String MANUFACTURER = "Google";

    private static final String MODEL = "Nexus Silver";

    private static final String RELEASE = "8.0";

    private static final String SDK = "26";

    private static final int REASONABLE_TIMEOUT_S = 5;

    private static final String ADDITIONAL_TEST_MESSAGE = "nope! fooled you!";

    private static final int TEST_BYTE = 0xAB;

    /**
     * Returns the path to the adb present in the SDK used for tests.
     */
    @NonNull
    public static Path getPathToAdb() {
        return TestUtils.getSdk().resolve("platform-tools").resolve(SdkConstants.FN_ADB);
    }

    @Test
    public void testRawExec() throws Exception {
        // Build the server and configure it to use the default ADB command handlers.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();

        try (FakeAdbServer server = builder.build()) {
            server.connectDevice(
              SERIAL,
              MANUFACTURER,
              MODEL,
              RELEASE,
              SDK,
              DeviceState.HostConnectionType.USB)
              .get();

            server.start();

            // Test that we obtain 1 device via the ddmlib APIs
            AndroidDebugBridge.enableFakeAdbServerMode(server.getPort());
            AndroidDebugBridge.initIfNeeded(false);
            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
            assertNotNull("Debug bridge", bridge);

            long startTime = System.currentTimeMillis();
            while (!bridge.isConnected()
                   && (System.currentTimeMillis() - startTime) < TimeUnit.SECONDS.toMillis(10)) {
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }

            IDevice[] devices = bridge.getDevices();
            IDevice device = null;
            for (IDevice prospect : devices) {
                if (!prospect.getSerialNumber().equals(SERIAL)) {
                    continue;
                }
                device = prospect;
                break;
            }

            assertNotNull("Device serial=" + SERIAL, device);

            String cmd = ExecCommandHandler.PING_EXEC;
            String[] parameters = new String[0];
            String expectedResponse = ExecCommandHandler.PING_EXEC_OUTPUT;
            try (SocketChannel channel = device.rawExec(cmd, parameters)) {
                channel.configureBlocking(true);
                byte[] bytes = new byte[expectedResponse.getBytes(Charsets.UTF_8).length];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                channel.read(buffer);
                String response = new String(bytes, Charsets.UTF_8);
                assertEquals("rawExec response", expectedResponse, response);
            }

        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    // ignored because we shouldn't be relying on a hard coded port (5037): fakeadbserver should
    // be changed to use any available port, and that selected port should be passed on to ddmlib
    @Test
    public void testListDevices() throws Exception {
        // Build the server and configure it to use the default ADB command handlers.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();

        try (FakeAdbServer server = builder.build()) {
            // Connect a test device to simulate device connection before server bring-up.
            server.connectDevice(
                    SERIAL,
                    MANUFACTURER,
                    MODEL,
                    RELEASE,
                    SDK,
                    DeviceState.HostConnectionType.USB)
                    .get();

            // Start server execution.
            server.start();

            // Test that we obtain 1 device via the ddmlib APIs
            AndroidDebugBridge.enableFakeAdbServerMode(server.getPort());
            AndroidDebugBridge.initIfNeeded(false);
            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
            assertNotNull("Debug bridge", bridge);

            long startTime = System.currentTimeMillis();
            while (!bridge.isConnected()
                    && (System.currentTimeMillis() - startTime) < TimeUnit.SECONDS.toMillis(10)) {
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }

            assertThat(bridge.isConnected()).isTrue();

            // should we rather be waiting for the initial device list to become available?
            assume().that(bridge.hasInitialDeviceList()).isTrue();

            IDevice[] devices = bridge.getDevices();
            assertThat(devices.length).isEqualTo(1);
            assertThat(devices[0].getName()).isEqualTo(SERIAL);
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    @Test
    public void testGetFeatures() throws Exception {
        // Build the server and configure it to use the default ADB command handlers.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();

        try (FakeAdbServer server = builder.build()) {
            // Connect a test device to simulate device connection before server bring-up.
            server.connectDevice(
                            SERIAL,
                            MANUFACTURER,
                            MODEL,
                            RELEASE,
                            SDK,
                            DeviceState.HostConnectionType.USB)
                    .get();

            // Start server execution.
            server.start();

            // Test that we obtain 1 device via the ddmlib APIs
            AndroidDebugBridge.enableFakeAdbServerMode(server.getPort());
            AndroidDebugBridge.initIfNeeded(false);
            AndroidDebugBridge bridge =
                    AndroidDebugBridge.createBridge(getPathToAdb().toString(), false);
            assertNotNull("Debug bridge", bridge);

            long startTime = System.currentTimeMillis();
            while (!bridge.isConnected()
                    && (System.currentTimeMillis() - startTime) < TimeUnit.SECONDS.toMillis(10)) {
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }

            assertThat(bridge.isConnected()).isTrue();

            // should we rather be waiting for the initial device list to become available?
            assume().that(bridge.hasInitialDeviceList()).isTrue();

            IDevice[] devices = bridge.getDevices();
            assertThat(devices.length).isEqualTo(1);
            assertThat(devices[0].getName()).isEqualTo(SERIAL);

            assertThat(devices[0].supportsFeature(IDevice.Feature.ABB_EXEC)).isTrue();
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    @Test
    public void testPortForward() throws Exception {
        // Build the server and configure it to use the default ADB command handlers.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();

        try (FakeAdbServer server = builder.build()) {
            DeviceState deviceState = server.connectDevice(
                    SERIAL,
                    MANUFACTURER,
                    MODEL,
                    RELEASE,
                    SDK,
                    DeviceState.HostConnectionType.USB)
                    .get();

            // Start server execution.
            server.start();

            // Test that we obtain 1 device via the ddmlib APIs
            AndroidDebugBridge.enableFakeAdbServerMode(server.getPort());
            AndroidDebugBridge.initIfNeeded(false);
            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
            assertNotNull("Debug bridge", bridge);

            long startTime = System.currentTimeMillis();
            while (!bridge.isConnected()
                    && (System.currentTimeMillis() - startTime) < TimeUnit.SECONDS.toMillis(10)) {
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }

            assertThat(bridge.isConnected()).isTrue();

            // should we rather be waiting for the initial device list to become available?
            assume().that(bridge.hasInitialDeviceList()).isTrue();

            IDevice[] devices = bridge.getDevices();
            assertThat(devices.length).isEqualTo(1);
            assertThat(devices[0].getName()).isEqualTo(SERIAL);

            try (ServerSocket serverSocket = new ServerSocket(0)) {
                serverSocket.setReuseAddress(true);
                CountDownLatch serverConnected = new CountDownLatch(1);
                ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
                Future<Boolean> serverResult = serverExecutor.submit(() -> {
                    try (Socket socket = serverSocket.accept()) {
                        serverConnected.countDown();
                        return socket.getInputStream().read() == TEST_BYTE;
                    } catch (IOException e) {
                        return false;
                    }
                });

                // Port forwarding is basically just a no-op. Run the stubbed/mocked/fake
                // device-side service on the source port of the forward directly. That way, when
                // the component that's attempting to connect to the device through the source port,
                // the component will connect directly to the service.
                int destinationPort = new Random(System.currentTimeMillis()).nextInt(MAX_PORT) + 1;
                devices[0].createForward(serverSocket.getLocalPort(), destinationPort);
                ImmutableMap<Integer, PortForwarder> forwarders = deviceState
                        .getAllPortForwarders();
                assertEquals(1, forwarders.size());
                assertTrue(forwarders.containsKey(serverSocket.getLocalPort()));
                assertEquals(serverSocket.getLocalPort(),
                        forwarders.get(serverSocket.getLocalPort()).getSource().getPort());
                assertEquals(destinationPort,
                        forwarders.get(serverSocket.getLocalPort()).getDestination().getPort());
                assertEquals(PortForwarder.INVALID_PORT,
                        forwarders.get(serverSocket.getLocalPort()).getDestination().getJdwpPid());
                assertNull(forwarders.get(serverSocket.getLocalPort()).getDestination()
                        .getUnixDomain());

                try (Socket sourceSocket = new Socket(LOCAL_HOST, serverSocket.getLocalPort())) {
                    sourceSocket.setReuseAddress(true);
                    sourceSocket.getOutputStream().write(TEST_BYTE);
                }
                serverConnected.await();
                assertTrue(serverResult.get());

                // Test removeForward.
                devices[0].removeForward(serverSocket.getLocalPort(), 0);
                assertEquals(0, deviceState.getAllPortForwarders().size());
            }
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    /**
     * Sample test to ensure that the pipeline works. This also tests that logcat handling is
     * working.
     *
     * <p>TODO refactor ddmlib to accept a pre-constructed adb server connection and remove
     * "@Ignore".
     */
    @Test
    public void testLogcatPipeline() throws Exception {
        // Setup the server to default configuration.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();

        try (FakeAdbServer server = builder.build()) {
            // Pre-connect a device before server startup.
            DeviceState device =
                    server.connectDevice(
                            SERIAL,
                            MANUFACTURER,
                            MODEL,
                            RELEASE,
                            SDK,
                            DeviceState.HostConnectionType.USB)
                            .get();
            device.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

            // Load a sample test logcat output and simulate a device with existing buffered logcat
            // output prior to starting the server.
            File f = TestResources.getFile(getClass(), "/logcat.txt");
            assertTrue(f.exists());
            device.addLogcatMessage(Files.toString(f, Charsets.UTF_8));

            server.start();

            // Start up ADB.
            AndroidDebugBridge.enableFakeAdbServerMode(server.getPort());
            AndroidDebugBridge.initIfNeeded(false);
            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
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
            device.addLogcatMessage(
                    "[ 08-29 14:37:30.000   779:  123 D/ShutdownTestService ]\n"
                            + ADDITIONAL_TEST_MESSAGE
                            + "\n\n");
            LogCatMessage echo =
                    listener.waitForNextMessage(REASONABLE_TIMEOUT_S, TimeUnit.SECONDS);
            assertNotNull(echo);
            assertEquals(echo.getMessage(), ADDITIONAL_TEST_MESSAGE);

            logCatReceiverTask.stop();
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    /**
     * Test that when a command timeout is exceeded, a {@link TimeoutException} is thrown in order
     * to show the correct state of the requests.
     */
    @Test
    public void testAdbCommandTimeout() throws Exception {
        // Setup the server to default configuration.
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();

        try (FakeAdbServer server = builder.build()) {
            // Pre-connect a device before server startup.
            DeviceState device =
                    server.connectDevice(
                            SERIAL,
                            MANUFACTURER,
                            MODEL,
                            RELEASE,
                            SDK,
                            DeviceState.HostConnectionType.USB)
                            .get();
            device.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

            server.start();

            // Start up ADB.
            AndroidDebugBridge.enableFakeAdbServerMode(server.getPort());
            // A devices are added async. So we add a listener before setting up the bridge to ensure
            // we don't have timing issues.
            CustomDeviceListener deviceListener = new CustomDeviceListener();
            AndroidDebugBridge.addDeviceChangeListener(deviceListener);
            AndroidDebugBridge.initIfNeeded(false);
            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
            assertNotNull("Debug bridge", bridge);

            // Wait for the device to get recognized by ddmlib.
            assertTrue(
                    deviceListener.waitForDeviceConnection(REASONABLE_TIMEOUT_S, TimeUnit.SECONDS));

            IDevice[] iDevices = bridge.getDevices();
            assertEquals(iDevices.length, 1);
            IDevice iDevice = iDevices[0];
            assertEquals(iDevice.getSerialNumber(), SERIAL);

            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            try {
                // Use a long enough timeout to ensure we receive at least one message from the
                // command to verify it executed.
                iDevice.executeShellCommand(
                        "write-no-stop", receiver, 500L, 0L, TimeUnit.MILLISECONDS);
                fail("Should have thrown an exception.");
            } catch (TimeoutException expected) {
                assertEquals("executeRemoteCommand timed out after 500ms", expected.getMessage());
                assertTrue(receiver.getOutput().contains("write-no-stop test in progress"));
            }
        } finally {
            AndroidDebugBridge.terminate();
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
