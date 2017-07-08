package com.android.tools.profiler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.tools.profiler.Perfa;
import android.tools.profiler.Perfd;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.ManagedChannel;
import org.junit.Test;

public class PerfaTest {

    @Test
    public void testPerfABeforePerfDConnection() throws Exception {
        // TODO: Pass in XML config file to perfd/a service.
        Perfa perfa = new Perfa();
        Perfd perfd = new Perfd();
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
