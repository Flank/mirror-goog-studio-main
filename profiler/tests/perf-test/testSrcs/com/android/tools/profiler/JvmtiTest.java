package com.android.tools.profiler;

import static org.junit.Assert.assertEquals;

import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import org.junit.Rule;
import org.junit.Test;

public class JvmtiTest {

    @Rule
    public final DeviceProperties myPropertiesFile = new DeviceProperties("O+", "REL", "26");
    private PerfDriver myDriver = new PerfDriver();

    @Test
    public void testPerfABeforePerfDSocketConnection() throws Exception {
        // Start the driver.
        myDriver.start();
        FakeAndroidDriver fakeAndroid = myDriver.getFakeAndroidDriver();
        GrpcUtils grpc = myDriver.getGrpc();

        // Load our mock application, anad launch our test activity.
        fakeAndroid.loadDex(ProcessRunner.getProcessPath("app.dex.location"));
        fakeAndroid.launchActivity("com.activity.MyActivity");

        // Attach the jvmti agent.
        myDriver.attachAgent();

        // Verify we get our loaded activity.
        ActivityDataResponse response = grpc.getActivity(grpc.getProcessId());
        assertEquals(response.getData(0).getName(), "My Activity");
    }
}
