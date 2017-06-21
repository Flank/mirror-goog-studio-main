package com.android.tools.profiler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.tools.profiler.Perfa;
import android.tools.profiler.Perfd;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.ManagedChannel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class PerfaTest {

    @Rule public final TestName myTestName = new TestName();
    private final int myPort = 12389;
    private File myConfigFile;

    @Before
    public void setup() {
        buildAndSaveConfig();
    }

    @After
    public void tearDown() {
        myConfigFile.deleteOnExit();
    }

    private void buildAndSaveConfig() {
        try {
            myConfigFile = File.createTempFile(myTestName.getMethodName(), ".data");
            FileOutputStream outputStream = new FileOutputStream(myConfigFile);
            Agent.AgentConfig config = Agent.AgentConfig.newBuilder().setUseJvmti(false).build();
            config.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException ex) {
            Assert.fail("Failed to write config file: " + ex);
        }
    }
    @Test
    public void testPerfABeforePerfDConnection() throws Exception {
        // TODO: Pass in XML config file to perfd/a service.
        Perfa perfa = new Perfa();
        Perfd perfd = new Perfd(myPort, myConfigFile.getAbsolutePath());
        perfa.start();
        perfd.start();
        // TODO: Wrap RPC connection for each service.
        ManagedChannel channel = perfd.connectGrpc();
        ProfilerServiceGrpc.ProfilerServiceBlockingStub stub =
                ProfilerServiceGrpc.newBlockingStub(channel);
        Profiler.AgentStatusResponse response =
                stub.getAgentStatus(Profiler.AgentStatusRequest.getDefaultInstance());
        assertTrue(perfd.isAlive());
        assertTrue(perfa.isAlive());
        assertNotEquals(response, Profiler.AgentStatusResponse.getDefaultInstance());
        perfa.stop();
        assertTrue(perfd.isAlive());
        assertFalse(perfa.isAlive());
        perfd.stop();
        assertFalse(perfd.isAlive());
    }
}
