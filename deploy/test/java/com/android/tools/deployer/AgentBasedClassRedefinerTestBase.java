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
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public abstract class AgentBasedClassRedefinerTestBase extends ClassRedefinerTestBase {
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

    protected LocalTestAgentBasedClassRedefiner redefiner;

    protected TemporaryFolder dexLocation;

    protected File dataDir;

    protected final String artFlag;

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

    public AgentBasedClassRedefinerTestBase(String artFlag) {
        this.artFlag = artFlag;
    }

    @Before
    public void setUp() throws Exception {
        dexLocation = new TemporaryFolder();
        dexLocation.create();

        File root = Files.createTempDirectory("root_dir").toFile();
        String[] env = new String[] {
            "FAKE_DEVICE_ROOT=" + root.getAbsolutePath()
        };
        dataDir = new File(root, "/data/data/" + PACKAGE);
        File dotStudio = new File(dataDir, "/code_cache/.studio");
        dotStudio.mkdirs();
        android = new FakeAndroidDriver(LOCAL_HOST, -1, artFlag, env);
        android.start();

        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation);
        redefiner.startServer();
    }

    @After
    public void tearDown() {
        android.stop();
        redefiner.stopServer();
    }

    protected void startupAgent() {
        // Ideally, we modify FakeAndroidDriver to do this, but this suffices to exercise the code
        // path for now.
        android.attachAgent(AGENT_LOCATION + "=" + dataDir.toString());
    }

    protected Deploy.SwapRequest createRequest(
            String name, String dex, boolean restart, Deploy.ClassDef.FieldReInitState... states)
            throws IOException {
        Deploy.ClassDef.Builder classBuilder =
                Deploy.ClassDef.newBuilder()
                        .setName(name)
                        .setDex(ByteString.copyFrom(getSplittedDex(dex)));
        for (Deploy.ClassDef.FieldReInitState state : states) {
            classBuilder.addFields(state);
        }

        Deploy.ClassDef classDef = classBuilder.build();

        Deploy.SwapRequest request =
                Deploy.SwapRequest.newBuilder()
                        .addModifiedClasses(classDef)
                        .setPackageName(PACKAGE)
                        .setRestartActivity(restart)
                        .setStructuralRedefinition(true)
                        .setVariableReinitialization(true)
                        .build();
        return request;
    }

    protected Deploy.SwapRequest createRequest(
            Map<String, String> modifiedClasses, Map<String, String> newClasses, boolean restart)
            throws IOException {

        Deploy.SwapRequest.Builder request =
                Deploy.SwapRequest.newBuilder().setPackageName(PACKAGE).setRestartActivity(restart);

        for (String name : modifiedClasses.keySet()) {
            request.addModifiedClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(name)
                            .setDex(
                                    ByteString.copyFrom(
                                            getSplittedDex(modifiedClasses.get(name)))));
        }

        for (String name : newClasses.keySet()) {
            request.addNewClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(name)
                            .setDex(ByteString.copyFrom(getSplittedDex(newClasses.get(name)))));
        }

        return request.build();
    }

    protected static class LocalTestAgentBasedClassRedefiner {
        private final TemporaryFolder messageDir;
        private final FakeAndroidDriver android;
        private final String socketName;
        private Process server;

        protected LocalTestAgentBasedClassRedefiner(
                FakeAndroidDriver android, TemporaryFolder messageDir) {
            this.android = android;
            this.messageDir = messageDir;
            this.socketName = "irsocket:" + UUID.randomUUID();
        }

        protected void redefine(Deploy.SwapRequest request, boolean unused) {
            Deploy.SendAgentMessageRequest agentRequest =
                    Deploy.SendAgentMessageRequest.newBuilder()
                            .setAgentCount(1)
                            .setSwapRequest(request)
                            .build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder()
                            .setType(Deploy.InstallServerRequest.Type.HANDLE_REQUEST)
                            .setSendRequest(agentRequest)
                            .build();
            redefine(serverRequest.toByteArray());
        }

        protected void redefine(byte[] message) {
            try {
                sendMessage(message);
                android.attachAgent(AGENT_LOCATION + "=" + socketName);
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        protected Deploy.AgentSwapResponse getAgentResponse()
                throws IOException, InvalidProtocolBufferException {
            return getServerResponse().getSendResponse().getAgentResponses(0);
        }

        private void sendMessage(byte[] message) throws IOException {
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

        private void startServer() throws IOException {
            System.out.println("Starting install server");
            server = new ProcessBuilder(SERVER_LOCATION).start();
            if (getServerResponse().getStatus()
                    != Deploy.InstallServerResponse.Status.SERVER_STARTED) {
                System.err.println("Did not receive startup ack from install server");
            }
            Deploy.OpenAgentSocketRequest socketRequest =
                    Deploy.OpenAgentSocketRequest.newBuilder().setSocketName(socketName).build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder()
                            .setType(Deploy.InstallServerRequest.Type.HANDLE_REQUEST)
                            .setSocketRequest(socketRequest)
                            .build();
            sendMessage(serverRequest.toByteArray());
            if (getServerResponse().getSocketResponse().getStatus()
                    != Deploy.OpenAgentSocketResponse.Status.OK) {
                System.err.println("Agent socket could not be opened");
            }
        }

        protected Deploy.InstallServerResponse getServerResponse()
                throws IOException, InvalidProtocolBufferException {
            InputStream stdout = server.getInputStream();

            byte[] magicBytes = new byte[MAGIC_NUMBER.length];

            int offset = 0;
            while (offset < magicBytes.length) {
                offset += stdout.read(magicBytes, offset, magicBytes.length - offset);
            }

            byte[] sizeBytes = new byte[Integer.BYTES];

            offset = 0;
            while (offset < sizeBytes.length) {
                offset += stdout.read(sizeBytes, offset, sizeBytes.length - offset);
            }

            int size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] messageBytes = new byte[size];

            offset = 0;
            while (offset < messageBytes.length) {
                offset += stdout.read(messageBytes, offset, messageBytes.length - offset);
            }
            return Deploy.InstallServerResponse.parseFrom(messageBytes);
        }

        protected void stopServer() {
            try {
                System.out.println("Waiting for server to exit");
                if (server != null) {
                    Deploy.InstallServerRequest request =
                            Deploy.InstallServerRequest.newBuilder()
                                    .setType(Deploy.InstallServerRequest.Type.SERVER_EXIT)
                                    .build();
                    sendMessage(request.toByteArray());
                    server.waitFor();
                }
                System.out.println("Server exited");
            } catch (IOException | InterruptedException e) {
                System.err.println(e);
            }
        }

        public void redefine(Deploy.SwapRequest request) {
            redefine(request, true);
        }
    }
}
