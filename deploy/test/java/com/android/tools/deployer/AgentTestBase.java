/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.junit.rules.TemporaryFolder;

/**
 * A unity class for setting up a minimal Activity running inside FakeAndroid as well as the a
 * install-server + agent combo to interact with the processing.
 *
 * <p>Since FakeAndroid relies on a HOST build of ART, this class will only function in Linux.
 */
public class AgentTestBase {

    protected static final String ACTIVITY_CLASS = "app.TestActivity";

    // Location of the initial test-app that has the ACTIVITY_CLASS
    protected static final String DEX_LOCATION = ProcessRunner.getProcessPath("app.dex.location");

    protected static final String AGENT_LOCATION =
            ProcessRunner.getProcessPath("swap.agent.location");

    protected static final String SERVER_LOCATION =
            ProcessRunner.getProcessPath("install.server.location");

    protected static final String PACKAGE = "package.name";

    protected static final String LOCAL_HOST = "127.0.0.1";

    protected static final int RETURN_VALUE_TIMEOUT = 1000;

    protected static final Collection<String> ALL_ART_FLAGS =
            Arrays.asList(null, "-Xopaque-jni-ids:true");

    protected FakeAndroidDriver android;

    protected TemporaryFolder dexLocation;

    protected File dataDir;

    protected final String artFlag;

    public AgentTestBase(String flag) {
        artFlag = flag;
    }

    public void setUp() throws Exception {
        dexLocation = new TemporaryFolder();
        dexLocation.create();

        File root = Files.createTempDirectory("root_dir").toFile();
        String[] env = new String[] {"FAKE_DEVICE_ROOT=" + root.getAbsolutePath()};
        dataDir = new File(root, "/data/data/" + PACKAGE);
        File dotStudio = new File(dataDir, "/code_cache/.studio");
        dotStudio.mkdirs();
        android = new FakeAndroidDriver(LOCAL_HOST, -1, artFlag, env);
        android.start();
    }

    public void tearDown() {
        android.stop();
    }

    protected void startupAgent() {
        // Ideally, we modify FakeAndroidDriver to do this, but this suffices to exercise the code
        // path for now.
        android.attachAgent(AGENT_LOCATION + "=" + dataDir.toString());
    }

    /**
     * A helper class to start an install-server and use it to communicate with the agent
     * with @{InstallServerRequest}
     */
    protected static class InstallServerTestClient {
        private static final byte[] MAGIC_NUMBER = {
            (byte) 0xAC,
            (byte) 0xA5,
            (byte) 0xAC,
            (byte) 0xA5,
            (byte) 0xAC,
            (byte) 0xA5,
            (byte) 0xAC,
            (byte) 0xA5
        };

        protected final FakeAndroidDriver android;
        protected final String socketName;
        private final TemporaryFolder messageDir;
        private Process server;

        protected InstallServerTestClient(FakeAndroidDriver android, TemporaryFolder messageDir) {
            this.android = android;
            this.messageDir = messageDir;
            this.socketName = "irsocket:" + UUID.randomUUID();
        }

        protected void attachAgent() {
            android.attachAgent(AGENT_LOCATION + "=" + socketName);
        }

        protected Deploy.AgentResponse getAgentResponse() throws IOException {
            return getServerResponse().getSendResponse().getAgentResponses(0);
        }

        protected void sendMessage(byte[] message) throws IOException {

            byte[] size =
                    ByteBuffer.allocate(MAGIC_NUMBER.length + Integer.BYTES)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .put(MAGIC_NUMBER)
                            .putInt(message.length)
                            .array();

            OutputStream stdin = server.getOutputStream();
            stdin.write(size);
            stdin.write(message);
            stdin.flush();
        }

        void startServer() throws IOException {
            System.out.println("Starting install server");
            server = new ProcessBuilder(SERVER_LOCATION, "fakeAppId").start();
            Deploy.OpenAgentSocketRequest socketRequest =
                    Deploy.OpenAgentSocketRequest.newBuilder().setSocketName(socketName).build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder()
                            .setSocketRequest(socketRequest)
                            .build();
            sendMessage(serverRequest.toByteArray());
            if (getServerResponse().getSocketResponse().getStatus()
                    != Deploy.OpenAgentSocketResponse.Status.OK) {
                System.err.println("Agent socket could not be opened");
            }
        }

        private static boolean readIntoBuffer(byte[] array, InputStream stream) throws IOException {
            ReadableByteChannel channel = Channels.newChannel(stream);
            ByteBuffer b = ByteBuffer.wrap(array);
            while (b.remaining() != 0 && channel.read(b) != -1) {}
            return b.remaining() == 0;
        }

        protected Deploy.InstallServerResponse getServerResponse()
                throws IOException, InvalidProtocolBufferException {
            InputStream stdout = server.getInputStream();

            byte[] magicBytes = new byte[MAGIC_NUMBER.length];
            if (!readIntoBuffer(magicBytes, stdout)) {
                return null;
            }

            if (!Arrays.equals(magicBytes, MAGIC_NUMBER)) {
                return null;
            }

            byte[] sizeBytes = new byte[Integer.BYTES];
            if (!readIntoBuffer(sizeBytes, stdout)) {
                return null;
            }

            int size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] messageBytes = new byte[size];
            if (!readIntoBuffer(messageBytes, stdout)) {
                return null;
            }

            return Deploy.InstallServerResponse.parseFrom(messageBytes);
        }

        protected void stopServer() {
            try {
                System.out.println("Waiting for server to exit");
                server.getInputStream().close();
                System.out.println("Server exited");
            } catch (IOException e) {
                System.err.println(e);
            }
        }
    }
}
