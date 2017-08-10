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

package com.android.tools.profiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.junit.After;
import org.junit.Assert;

/**
 * This class is the base class for all perf test. The management of test ports, processes, and
 * basic rpc calls that are shared between all perf test (jvmti and instrumented) should live in
 * this base class.
 */
public class PerfDriver {

    public static final String LOCAL_HOST = "127.0.0.1";
    private File myConfigFile;
    private int myPort;
    private int myCommunicationPort;
    private FakeAndroidDriver myMockApp;
    private PerfdDriver myPerfdDriver;
    private GrpcUtils myGrpc;
    private boolean myIsOPlusDevice;
    private DeviceProperties myPropertiesFile;

    public PerfDriver(boolean isOPlusDevice) {
        buildAndSaveConfig();
        if (isOPlusDevice) {
            myPropertiesFile = new DeviceProperties("O+", "26", "26");
        } else {
            myPropertiesFile = new DeviceProperties("Pre-O", "24", "24");
        }
        myPropertiesFile.writeFile();
        myIsOPlusDevice = isOPlusDevice;
        myCommunicationPort = getAvailablePort();
        myMockApp = new FakeAndroidDriver(LOCAL_HOST, myPort, myCommunicationPort);
        myPerfdDriver = new PerfdDriver(myConfigFile.getAbsolutePath());
        myGrpc = new GrpcUtils(LOCAL_HOST, myPort);
    }

    @After
    public void tearDown() {
        myMockApp.stop();
        myPerfdDriver.stop();
    }

    public int getCommunicationPort() {
        return myCommunicationPort;
    }

    public FakeAndroidDriver getFakeAndroidDriver() {
        return myMockApp;
    }

    public GrpcUtils getGrpc() {
        return myGrpc;
    }

    public int getPort() {
        return myPort;
    }

    /**
     * Function that launches the FakeAndroid framework, as well as perfd. This function will wait
     * until the framework has been loaded. Load the proper dex into the android framework and
     * launch the specified activity. After the activity is launched for JVMTI (O+) test the
     * function will attach the agent and wait until successful before returning.
     */
    public void start(String activity) throws IOException {
        if (myIsOPlusDevice) {
            copyFilesForJvmti();
        }
        myMockApp.start();
        myPerfdDriver.start();

        //Block until we are in a state for the test to continue.
        assertTrue(myMockApp.waitForInput("Test Framework Server Listening"));
        myMockApp.setProperty(
                "profiler.service.address", String.format("%s:%d", PerfDriver.LOCAL_HOST, myPort));
        if (!myIsOPlusDevice) {
            myMockApp.loadDex(ProcessRunner.getProcessPath("profiler.service.location"));
            myMockApp.loadDex(ProcessRunner.getProcessPath("instrumented.app.dex.location"));
        } else {

            myMockApp.loadDex(ProcessRunner.getProcessPath("jvmti.app.dex.location"));
        }
        // Load our mock application, anad launch our test activity.
        myMockApp.launchActivity(activity);

        if (myIsOPlusDevice) {
            attachAgent();
        }
    }

    private void copyFilesForJvmti() {
        try {
            File libPerfaFile = new File(ProcessRunner.getProcessPath("perfa.location"));
            File perfaJarFile = new File(ProcessRunner.getProcessPath("perfa.jar.location"));
            if (libPerfaFile.exists()) {
                Files.copy(
                    libPerfaFile.toPath(),
                    new File("./libperfa.so").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }
            if (perfaJarFile.exists()) {
                Files.copy(
                    perfaJarFile.toPath(),
                    new File("./perfa.jar").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Assert.fail("Failed to copy required file: " + ex);
        }
    }

    public void attachAgent() {
        ProfilerServiceGrpc.ProfilerServiceBlockingStub stub = myGrpc.getProfilerStub();
        Profiler.AgentAttachRequest attachRequest = Profiler.AgentAttachRequest.newBuilder()
            .setProcessId(getCommunicationPort())
            .setAgentLibFileName("libperfa.so")
            .build();
        Profiler.AgentAttachResponse attachResponse = stub.attachAgent(attachRequest);
        assertEquals(attachResponse.getStatus(), Profiler.AgentAttachResponse.Status.SUCCESS);
        // Block until we can verify the agent was fully attached.
        assertTrue(myMockApp.waitForInput("Memory control stream started."));
    }

    /**
     * @return an available port to be used by the test framework.
     */
    private int getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            Assert.fail("Unable to find available port: " + ex);
        }
        return -1;
    }

    /**
     * Helper function to create and serialize AgentConfig for test to use, this is specific to each
     * test.
     */
    private void buildAndSaveConfig() {
        try {
            myPort = getAvailablePort();
            myConfigFile = File.createTempFile("agent_config", ".data");
            myConfigFile.deleteOnExit();
            FileOutputStream outputStream = new FileOutputStream(myConfigFile);
            Agent.AgentConfig config =
                Agent.AgentConfig.newBuilder()
                    // The test below are using JVMTI, however this flag controls if we are
                    // using an abstract unix socket or if we are using a host and port.
                    // TODO: Update framework to support abstract sockets.
                    .setUseJvmti(false)
                    .setServiceAddress(LOCAL_HOST + ":" + myPort)
                    .setSocketType(Agent.SocketType.UNSPECIFIED_SOCKET)
                    .build();
            config.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException ex) {
            Assert.fail("Failed to write config file: " + ex);
        }
    }
}
