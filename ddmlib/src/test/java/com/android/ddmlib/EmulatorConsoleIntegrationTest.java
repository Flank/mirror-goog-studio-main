/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.ddmlib.IDevice.DeviceState.ONLINE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.ddmlib.internal.DeviceImpl;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.testutils.SystemPropertyOverrides;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import junit.framework.TestCase;

/** Integration tests for {@link EmulatorConsole} using a fake emulator device. */
public class EmulatorConsoleIntegrationTest extends TestCase {
    private final SystemPropertyOverrides mySystemPropertyOverrides = new SystemPropertyOverrides();
    private Path mFakeUserHomeDirectory = null;
    private Path fakeAuthToken = null;

    @Override
    protected void setUp() throws Exception {
        try {
            // In bazel AndroidLication.getUserHomeFolder is obtained through TEST_TMPDIR environment
            // variable that's set by bazel.  Outside of bazel we'll need to set a fake user.home directory
            // to avoid overwriting the real emulator console auth token.
            if (System.getenv("TEST_TMPDIR") == null) {
                mFakeUserHomeDirectory = Files.createTempDirectory("fake_user_home");
                mySystemPropertyOverrides.setProperty(
                        "user.home", mFakeUserHomeDirectory.toString());
            }
            Path homeLocation = AndroidLocationsSingleton.INSTANCE.getUserHomeLocation();
            Truth.assertThat((Iterable<?>) homeLocation).named("Home Location").isNotNull();
            //noinspection ConstantConditions
            Files.createDirectories(homeLocation);
            Path authTokenPath = homeLocation.resolve(".emulator_console_auth_token");
            fakeAuthToken = Files.createFile(authTokenPath);
        } catch (Throwable ex) {
            fail("Couldn't setup fake auth token file.  Error: " + ex.getMessage());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // Clean up files and system properties used for setting up fake auth token.
        if (fakeAuthToken != null) {
            Files.deleteIfExists(fakeAuthToken);
        }
        if (mFakeUserHomeDirectory != null) {
            Files.deleteIfExists(mFakeUserHomeDirectory);
        }
        mySystemPropertyOverrides.close();
    }

    public void testGetEmulatorConsole() throws Exception {
        try (FakeEmulatorDevice fakeEmulator = FakeEmulatorDevice.createStarted(false, false)) {
            DeviceImpl device = new DeviceImpl(null, fakeEmulator.getName(), ONLINE);
            EmulatorConsole console = EmulatorConsole.getConsole(device);
            Truth.assertThat(console).isNotNull();
        }
    }

    public void testGetEmulatorConsole_requiringAuthAndSucceeds() throws Exception {
        try (FakeEmulatorDevice fakeEmulator = FakeEmulatorDevice.createStarted(true, false)) {
            DeviceImpl device = new DeviceImpl(null, fakeEmulator.getName(), ONLINE);
            EmulatorConsole console = EmulatorConsole.getConsole(device);
            Truth.assertThat(console).isNotNull();
        }
    }

    public void testGetEmulatorConsole_requiringAuthButAuthFails() throws Exception {
        try (FakeEmulatorDevice fakeEmulator = FakeEmulatorDevice.createStarted(true, true)) {
            DeviceImpl device = new DeviceImpl(null, fakeEmulator.getName(), ONLINE);
            EmulatorConsole console = EmulatorConsole.getConsole(device);
            Truth.assertThat(console).isNull();
        }
    }

    /**
     * A fake emulator that responds to basic commands.
     *
     * <p>To see what commands this fake emulator responds to see {@link
     * FakeEmulatorDevice#processRequest(String)}.
     */
    private static class FakeEmulatorDevice implements AutoCloseable {
        private final boolean mAuthRequired;
        private final boolean mFailAuth;
        private ServerSocket mDeviceSocket;

        private static class Response {
            @NonNull public final String text;
            public final boolean shouldTerminate;

            Response(@NonNull String text, boolean shouldTerminate) {
                this.text = text;
                this.shouldTerminate = shouldTerminate;
            }
        }

        /**
         * Constructs a fake emulator that will respond to the given combination of auth
         * requirements and starts the device thread. Note: If not called in a try-with-resources
         * block, make sure to close the device by calling {@link #close}.
         *
         * @param authRequired if true, emulator will request authentication from client in init
         *     output (a.k.a. the welcome message).
         * @param failAuth if true, emulator will always respond to authentication requests with
         *     failure.
         */
        @NonNull
        public static FakeEmulatorDevice createStarted(boolean authRequired, boolean failAuth)
                throws IOException {
            FakeEmulatorDevice device = new FakeEmulatorDevice(authRequired, failAuth);
            device.start();
            return device;
        }

        @Override
        public void close() throws Exception {
            stop();
        }

        public String getName() {
            assert (mDeviceSocket.isBound())
                    : "Device socket is unbound. Call FakeEmulatorDevice#start before #getName.";
            return "emulator-" + mDeviceSocket.getLocalPort();
        }

        private FakeEmulatorDevice(boolean authRequired, boolean failAuth) throws IOException {
            mAuthRequired = authRequired;
            mFailAuth = failAuth;
            mDeviceSocket = new ServerSocket();
        }

        private void start() throws IOException {
            InetSocketAddress socketAddress =
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            mDeviceSocket.bind(socketAddress);
            new Thread(() -> listenAndProcessCommands()).start();
        }

        private void stop() throws IOException {
            mDeviceSocket.close();
        }

        private void listenAndProcessCommands() {
            try (Socket socket = mDeviceSocket.accept()) {
                // Emulators always output a welcome message upon init.
                String welcomeMsg =
                        mAuthRequired ? "Android Console: Authentication required\r\n" : "";
                writeReponse(socket, welcomeMsg);

                while (true) {
                    String request = readRequest(socket);
                    if (request.isEmpty()) {
                        continue;
                    }
                    Response response = processRequest(request);
                    if (response.shouldTerminate) {
                        fail("does this stop the test?");
                        break;
                    }
                    writeReponse(socket, response.text);
                }
            } catch (IOException ex) {
                fail("FakeEmulatorDevice encountered an error: " + ex.getMessage());
            }
        }

        private void writeReponse(@NonNull Socket client, @NonNull String response)
                throws IOException {
            response += "OK\r\n";
            client.getOutputStream().write(response.getBytes(UTF_8));
            client.getOutputStream().flush();
        }

        @NonNull
        private String readRequest(@NonNull Socket client) throws IOException {
            StringBuilder request = new StringBuilder();
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[64];
            int byteCount;
            while ((byteCount = in.read(buffer)) > 0) {
                request.append(new String(buffer, 0, byteCount, UTF_8));
                if (request.toString().endsWith("\r\n")) {
                    break;
                }
            }

            return request.toString().trim();
        }

        @NonNull
        private Response processRequest(@NonNull String request) {
            if (request.equals("help")) {
                return new Response("help is here!", false);
            } else if (request.startsWith("auth")) {
                return new Response(mFailAuth ? "KO: AUTH FAILED" : "SUCCESS", false);
            } else if (request.equals("kill")) {
                return new Response("", true);
            }

            fail("Command \"" + request + "\" is not a supported fake emulator console command.");
            return new Response("", true);
        }
    }
}
