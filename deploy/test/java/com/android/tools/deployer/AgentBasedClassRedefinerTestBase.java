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
            ProcessRunner.getProcessPath("swap.server.location");

    protected static final String PACKAGE = "package.name";

    protected static final String LOCAL_HOST = "127.0.0.1";
    protected static final int RETURN_VALUE_TIMEOUT = 1000;

    protected FakeAndroidDriver android;

    protected LocalTestAgentBasedClassRedefiner redefiner;

    protected TemporaryFolder dexLocation;

    @Before
    public void setUp() throws Exception {
        dexLocation = new TemporaryFolder();
        dexLocation.create();

        File root = Files.createTempDirectory("root_dir").toFile();
        String[] env = new String[] {
            "FAKE_DEVICE_ROOT=" + root.getAbsolutePath()
        };
        File dotStudio = new File(root, "/data/data/" + PACKAGE + "/code_cache/.studio");
        dotStudio.mkdirs();
        android = new FakeAndroidDriver(LOCAL_HOST, env);
        android.start();

        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation);
    }

    @After
    public void tearDown() {
        android.stop();
        redefiner.stopServer();
    }

    protected Deploy.SwapRequest createRequest(String name, String dex, boolean restart)
            throws IOException {
        Deploy.ClassDef classDef =
                Deploy.ClassDef.newBuilder()
                        .setName(name)
                        .setDex(ByteString.copyFrom(getSplittedDex(dex)))
                        .build();
        Deploy.SwapRequest request =
                Deploy.SwapRequest.newBuilder()
                        .addModifiedClasses(classDef)
                        .setPackageName(PACKAGE)
                        .setRestartActivity(restart)
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
            redefine(request.toByteArray());
        }

        protected void redefine(byte[] message) {
            try {
                // Start a new agent server that will connect to a single agent.
                System.out.println("Starting agent server");

                String agentCount = "1";
                // Use stderr as the sync file descriptor.
                String syncFd = "2";
                server =
                        new ProcessBuilder(SERVER_LOCATION, agentCount, socketName, syncFd).start();

                int sync = server.getErrorStream().read();
                if (sync != -1) {
                    // Not an error per se, but we probably want to see it in logs.
                    System.err.println("Sync received unexpected response");
                }

                // Convert the request into bytes prepended by the request size.
                byte[] size =
                        ByteBuffer.allocate(4)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(message.length)
                                .array();

                // Send the swap request to the server.
                OutputStream stdin = server.getOutputStream();
                stdin.write(size);
                stdin.write(message);
                stdin.flush();
                android.attachAgent(AGENT_LOCATION + "=" + socketName);
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        protected Deploy.AgentSwapResponse getAgentResponse()
                throws IOException, InvalidProtocolBufferException {
            InputStream stdout = server.getInputStream();
            byte[] sizeBytes = new byte[4];

            int offset = 0;
            while (offset < sizeBytes.length) {
                offset += stdout.read(sizeBytes, offset, sizeBytes.length - offset);
            }

            int size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] messageBytes = new byte[size];

            offset = 0;
            while (offset < messageBytes.length) {
                offset += stdout.read(messageBytes, offset, messageBytes.length - offset);
            }
            return Deploy.AgentSwapResponse.parseFrom(messageBytes);
        }

        protected void stopServer() {
            try {
                System.out.println("Waiting for server to exit");
                server.waitFor();
                System.out.println("Server exited");
            } catch (InterruptedException e) {
                System.err.println(e);
            }
        }

        public void redefine(Deploy.SwapRequest request) {
            redefine(request, true);
        }
    }
}
