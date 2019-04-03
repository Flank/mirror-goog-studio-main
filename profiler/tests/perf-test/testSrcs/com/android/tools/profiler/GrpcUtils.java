package com.android.tools.profiler;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.proto.*;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import com.android.tools.profiler.proto.EventProfiler.EventDataRequest;
import com.android.tools.profiler.proto.Profiler.BeginSessionRequest;
import com.android.tools.profiler.proto.Profiler.BeginSessionResponse;
import com.android.tools.profiler.proto.Profiler.EndSessionRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * Test class for managing a connection to perfd.
 */
public class GrpcUtils {
    private final ManagedChannel myChannel;
    private final TransportServiceGrpc.TransportServiceBlockingStub myTransportServiceStub;
    private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerServiceStub;
    private final EventServiceGrpc.EventServiceBlockingStub myEventServiceStub;
    private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkServiceStub;
    private final MemoryServiceGrpc.MemoryServiceBlockingStub myMemoryServiceStub;
    private final EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyServiceStub;
    private final FakeAndroidDriver myMockApp;

    /** Connect to perfd using a socket and port, currently abstract sockets are not supported. */
    public GrpcUtils(String socket, int port, FakeAndroidDriver mockApp) {
        myChannel = connectGrpc(socket, port);
        myTransportServiceStub = TransportServiceGrpc.newBlockingStub(myChannel);
        myProfilerServiceStub = ProfilerServiceGrpc.newBlockingStub(myChannel);
        myEventServiceStub = EventServiceGrpc.newBlockingStub(myChannel);
        myNetworkServiceStub = NetworkServiceGrpc.newBlockingStub(myChannel);
        myMemoryServiceStub = MemoryServiceGrpc.newBlockingStub(myChannel);
        myEnergyServiceStub = EnergyServiceGrpc.newBlockingStub(myChannel);
        myMockApp = mockApp;
    }

    public TransportServiceGrpc.TransportServiceBlockingStub getTransportStub() {
        return myTransportServiceStub;
    }

    public ProfilerServiceGrpc.ProfilerServiceBlockingStub getProfilerStub() {
        return myProfilerServiceStub;
    }

    public EventServiceGrpc.EventServiceBlockingStub getEventStub() {
        return myEventServiceStub;
    }

    public NetworkServiceGrpc.NetworkServiceBlockingStub getNetworkStub() {
        return myNetworkServiceStub;
    }

    public MemoryServiceGrpc.MemoryServiceBlockingStub getMemoryStub() {
        return myMemoryServiceStub;
    }

    public EnergyServiceGrpc.EnergyServiceBlockingStub getEnergyStub() {
        return myEnergyServiceStub;
    }

    private ManagedChannel connectGrpc(String socket, int port) {
        ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ManagedChannelBuilder.class.getClassLoader());
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(socket, port).usePlaintext(true).build();
        Thread.currentThread().setContextClassLoader(stashedContextClassLoader);
        return channel;
    }

    /**
     * Support function to get the main activity. This function checks for the activity name, if it
     * is not the default activity an assert will be thrown.
     */
    public ActivityDataResponse getActivity(Session session) {
        ActivityDataResponse response =
                myEventServiceStub.getActivityData(
                        EventDataRequest.newBuilder().setSession(session).build());
        return response;
    }

    /** Begins the profiler session on the specified pid. */
    public Session beginSession(int pid) {
        BeginSessionRequest.Builder requestBuilder =
                BeginSessionRequest.newBuilder().setDeviceId(1234).setPid(pid);
        BeginSessionResponse response = myProfilerServiceStub.beginSession(requestBuilder.build());
        return response.getSession();
    }

    /**
     * Begins the profiler session on the specified pid and attach the JVMTI agent via the
     * agentAttachPort.
     */
    public Session beginSessionWithAgent(int pid, int agentAttachPort, String agentConfigPath) {
        myTransportServiceStub.execute(
                Transport.ExecuteRequest.newBuilder()
                        .setCommand(
                                Commands.Command.newBuilder()
                                        .setType(Commands.Command.CommandType.ATTACH_AGENT)
                                        .setPid(agentAttachPort)
                                        .setStreamId(1234)
                                        .setAttachAgent(
                                                Commands.AttachAgent.newBuilder()
                                                        .setAgentLibFileName("libjvmtiagent.so")
                                                        .setAgentConfigPath(agentConfigPath))
                                        .build())
                        .build());
        // Block until we can verify the agent was fully attached, which takes a while.
        assertThat(myMockApp.waitForInput("Transport agent connected to daemon.")).isTrue();
        Session session = beginSession(pid);
        assertThat(myMockApp.waitForInput("Profiler initialization complete on agent.")).isTrue();
        return session;
    }

    /** Ends the profiler session for the specified sessionId. */
    public void endSession(long sessionId) {
        myProfilerServiceStub.endSession(
                EndSessionRequest.newBuilder().setSessionId(sessionId).build());
    }
}
