package com.android.tools.profiler;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.proto.Common.*;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import com.android.tools.profiler.proto.EventProfiler.EventDataRequest;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.android.tools.profiler.proto.TransportServiceGrpc;
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
    public Session beginSessionWithAgent(int pid, int agentAttachPort) {
        // The test infra calls attach-agent via the communication port instead of the app's pid.
        // So here we are making an extra beginSession call with the attachPid (aka communication
        // port) to allow the agent to attach.
        BeginSessionRequest.Builder requestBuilder =
                BeginSessionRequest.newBuilder()
                        .setDeviceId(1234)
                        .setPid(agentAttachPort)
                        .setJvmtiConfig(
                                BeginSessionRequest.JvmtiConfig.newBuilder()
                                        .setAttachAgent(true)
                                        .setAgentLibFileName("libperfa.so")
                                        .build());
        myProfilerServiceStub.beginSession(requestBuilder.build());

        // Actually begin the session with the pid. Note that a beginSession call would end the previous
        // active session, so we put this after the beginSession call that was used specifically to attach the
        // agent.
        Session session = beginSession(pid);
        // Block until we can verify the agent was fully attached, which takes a while.
        assertThat(myMockApp.waitForInput("Perfa connected to Perfd.")).isTrue();
        return session;
    }

    /** Ends the profiler session for the specified sessionId. */
    public void endSession(long sessionId) {
        myProfilerServiceStub.endSession(
                EndSessionRequest.newBuilder().setSessionId(sessionId).build());
    }
}
