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
package com.android.tools.deploy.swapper;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deploy.proto.Deploy.AgentConfig;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public abstract class AgentBasedClassRedefinerTestBase extends ClassRedefinerTestBase {
    protected static final String ACTIVITY_CLASS =
            "com.android.tools.deploy.swapper.testapp.TestActivity";

    // Location of the initial test-app that has the ACTIVITY_CLASS
    protected static final String DEX_LOCATION = ProcessRunner.getProcessPath("app.dex.location");

    protected static final String PACKAGE = "package.name.does.matter.in.this.test.";

    protected static final String LOCAL_HOST = "127.0.0.1";
    protected static final int RETURN_VALUE_TIMEOUT = 1000;

    protected FakeAndroidDriver android;

    protected LocalTestAgentBasedClassRedefiner redefiner;

    protected TemporaryFolder dexLocation;

    @Before
    public void setUp() throws Exception {
        dexLocation = new TemporaryFolder();
        dexLocation.create();

        android = new FakeAndroidDriver(LOCAL_HOST);
        android.start();

        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation, false);
    }

    @After
    public void tearDown() {
        android.stop();
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
                        .addClasses(classDef)
                        .setPackageName(PACKAGE)
                        .setRestartActivity(restart)
                        .build();
        return request;
    }

    protected static class LocalTestAgentBasedClassRedefiner extends ClassRedefiner {
        private final TemporaryFolder messageDir;
        private final FakeAndroidDriver android;
        private String messageLocation;

        protected LocalTestAgentBasedClassRedefiner(
                FakeAndroidDriver android, TemporaryFolder messageDir, boolean shouldRestart) {
            this.android = android;
            this.messageDir = messageDir;
        }

        protected void redefine(Deploy.SwapRequest request, boolean shouldSucceed) {
            try {
                AgentConfig.Builder agentConfig = AgentConfig.newBuilder();
                agentConfig.setSwapRequest(request);

                File pb = Files.createTempFile("messageDir", "msg.pb").toFile();
                FileOutputStream out = new FileOutputStream(pb);
                agentConfig.build().writeTo(out);

                android.attachAgent(
                        ProcessRunner.getProcessPath("swap.agent.location")
                                + "="
                                + pb.getAbsolutePath(),
                        shouldSucceed);
                // TODO(acleung): We have no way to two way communicate with the Agent for now
                // so we are just going wait for a log statement.
                if (shouldSucceed) {
                    android.waitForInput("Done HotSwapping!");
                } else {
                    android.waitForError("Hot swap failed.", RETURN_VALUE_TIMEOUT);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void redefine(Deploy.SwapRequest request) {
            redefine(request, true);
        }
    }
}
