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

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Agent.AgentConfig.MemoryConfig;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData;
import com.android.tools.profiler.proto.Transport;
import io.grpc.StatusRuntimeException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This class is the test rule for all perf test. The management of test ports, processes, and basic
 * rpc calls that are shared between all perf test (jvmti and instrumented) should live in this base
 * class.
 */
public class PerfDriver extends ExternalResource {
    // Folder to create temporary config files, which is chained in TestRule and will be deleted
    // at the test's end.
    private final TemporaryFolder myTemporaryFolder = new TemporaryFolder();
    private final String myActivityClass;

    private static final String LOCAL_HOST = "127.0.0.1";
    private int myPid = -1;
    private FakeAndroidDriver myMockApp;
    private PerfdDriver myPerfdDriver;
    private GrpcUtils myGrpc;
    private boolean myIsOPlusDevice;
    private boolean myUnifiedPipeline;
    private DeviceProperties myPropertiesFile;
    private int mySdkLevel;
    private Session mySession;
    private int myServerPort;
    private int myLiveAllocSamplingRate = 1;

    public PerfDriver(String activityClass, int sdkLevel) {
        this(activityClass, sdkLevel, false);
    }

    public PerfDriver(String activityClass, int sdkLevel, boolean unifiedPipeline) {
        myActivityClass = activityClass;
        mySdkLevel = sdkLevel;
        myUnifiedPipeline = unifiedPipeline;
        myIsOPlusDevice = TestUtils.isOPlusDevice(sdkLevel);
        myPropertiesFile =
                new DeviceProperties("", String.valueOf(mySdkLevel), String.valueOf(mySdkLevel));
        myPropertiesFile.writeFile();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return RuleChain.outerRule(myTemporaryFolder)
                .apply(super.apply(base, description), description);
    }

    @Override
    protected void before() throws Throwable {
        // Logs in perf-test output to track the sdk level and test start.
        System.out.println(
                String.format("Start activity %s with sdk level %d", myActivityClass, mySdkLevel));
        startPerfd();
        start(myActivityClass);
    }

    @Override
    protected void after() {
        if (mySession != null) {
            try {
                getGrpc().endSession(mySession.getSessionId());
            } catch (StatusRuntimeException e) {
                // TODO(b/112274301): fix "connection closed" error.
            }
        }
        myMockApp.stop();
        myPerfdDriver.stop();
        // Logs in perf-test output to track the sdk level and test end.
        System.out.println(
                String.format("Finish activity %s with sdk level %d", myActivityClass, mySdkLevel));
    }

    /**
     * Returns the port the app communicates over. Note: this will not be valid until after {@link
     * #start(String)} has finished.
     */
    public int getCommunicationPort() {
        return myMockApp.getCommunicationPort();
    }

    public FakeAndroidDriver getFakeAndroidDriver() {
        return myMockApp;
    }

    public GrpcUtils getGrpc() {
        return myGrpc;
    }

    private int getPid() {
        return myPid;
    }

    public Session getSession() {
        return mySession;
    }

    public void setLiveAllocSamplingRate(int samplingRate) {
        myLiveAllocSamplingRate = samplingRate;
    }

    /**
     * Function that launches the FakeAndroid framework, as well as perfd. This function will wait
     * until the framework has been loaded. Load the proper dex into the android framework and
     * launch the specified activity. After the activity is launched for JVMTI (O+) test the
     * function will attach the agent and wait until successful before returning.
     */
    private void start(String activity) throws IOException {
        if (myIsOPlusDevice) {
            copyFilesForJvmti();
        }

        int perfdPort = myPerfdDriver.getPort();

        myMockApp = new FakeAndroidDriver(LOCAL_HOST, new String[]{});
        myMockApp.start();

        myGrpc = new GrpcUtils(LOCAL_HOST, perfdPort, myMockApp);

        myMockApp.setProperty(
                "profiler.service.address", String.format("%s:%d", LOCAL_HOST, perfdPort));
        if (!myIsOPlusDevice) {
            myMockApp.loadDex(ProcessRunner.getProcessPath("profiler.service.location"));
            myMockApp.loadDex(ProcessRunner.getProcessPath("instrumented.app.dex.location"));
        } else {
            myMockApp.loadDex(ProcessRunner.getProcessPath("jvmti.app.dex.location"));
        }
        // Load our mock application, and launch our test activity.
        myMockApp.launchActivity(activity);

        // Retrieve the app's pid
        String pid = myMockApp.waitForInput(Pattern.compile("(.*)(PID=)(?<result>.*)"));
        try {
            myPid = Integer.parseInt(pid);
        } catch (NumberFormatException ignored) {
        }

        // Invoke beginSession to establish a session we can use to query data
        File agentConfig = buildAgentConfig();
        mySession =
                myIsOPlusDevice
                        ? getGrpc()
                                .beginSessionWithAgent(
                                        getPid(),
                                        getCommunicationPort(),
                                        agentConfig.getAbsolutePath())
                        : getGrpc().beginSession(getPid());
    }

    private void copyFilesForJvmti() {
        try {
            File libJvmtiAgentFile = new File(ProcessRunner.getProcessPath("perfa.location"));
            File perfaJarFile = new File(ProcessRunner.getProcessPath("perfa.jar.location"));
            if (libJvmtiAgentFile.exists()) {
                Files.copy(
                        libJvmtiAgentFile.toPath(),
                        new File("./libjvmtiagent.so").toPath(),
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

    /** @return an available port to be used by the test framework. */
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
    private File buildDaemonConfig() {
        File file = null;
        try {
            file = myTemporaryFolder.newFile();
            FileOutputStream outputStream = new FileOutputStream(file);
            Transport.DaemonConfig config =
                    Transport.DaemonConfig.newBuilder().setCommon(buildCommonConfig()).build();
            config.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException ex) {
            Assert.fail("Failed to write config file: " + ex);
        }
        return file;
    }

    /**
     * Helper function to create and serialize AgentConfig for test to use, this is specific to each
     * test.
     */
    private File buildAgentConfig() {
        File file = null;
        try {
            file = myTemporaryFolder.newFile();
            FileOutputStream outputStream = new FileOutputStream(file);
            Agent.AgentConfig.MemoryConfig memConfig =
                    MemoryConfig.newBuilder()
                            .setUseLiveAlloc(true)
                            .setTrackGlobalJniRefs(true)
                            .setAppDir("/")
                            .setMaxStackDepth(50)
                            .setSamplingRate(
                                    MemoryAllocSamplingData.newBuilder()
                                            .setSamplingNumInterval(myLiveAllocSamplingRate)
                                            .build())
                            .build();

            Agent.AgentConfig config =
                    Agent.AgentConfig.newBuilder()
                            .setMem(memConfig)
                            .setCommon(buildCommonConfig())
                            .build();
            config.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException ex) {
            Assert.fail("Failed to write config file: " + ex);
        }
        return file;
    }

    private Common.CommonConfig buildCommonConfig() {
        return Common.CommonConfig.newBuilder()
                .setServiceAddress(LOCAL_HOST + ":" + myServerPort)
                .setEnergyProfilerEnabled(true)
                .setProfilerUnifiedPipeline(myUnifiedPipeline)
                .build();
    }

    /**
     * Starts the perfd process that listens on the port assigned in the agent config file. If the
     * process does not bind to the given port, perfd is restarted on a new port. Because JVMTI will
     * reuse the port from the agent config, we cannot set zero in the config.
     */
    private void startPerfd() throws IOException {
        while (myPerfdDriver == null || myPerfdDriver.getPort() == 0) {
            myServerPort = getAvailablePort();
            File daemonConfig = buildDaemonConfig();
            myPerfdDriver = new PerfdDriver(daemonConfig.getAbsolutePath());
            myPerfdDriver.start();
        }
    }
}
