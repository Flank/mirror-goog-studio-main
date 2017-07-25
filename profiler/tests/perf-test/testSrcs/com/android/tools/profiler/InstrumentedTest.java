package com.android.tools.profiler;

import static org.junit.Assert.assertEquals;

import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import org.junit.Rule;
import org.junit.Test;

public class InstrumentedTest {

    @Rule
    public final DeviceProperties myPropertiesFile = new DeviceProperties("Pre-O", "24", "24");
    private PerfDriver myDriver = new PerfDriver();
    @Test
    public void testPerfABeforePerfDIpConnection() throws Exception {
        //Start the test driver.
        myDriver.start();
        FakeAndroidDriver fakeAndroid = myDriver.getFakeAndroidDriver();
        GrpcUtils grpc = myDriver.getGrpc();

        // Initialize our fake android client with the proper settings, and dex files.
        fakeAndroid.setProperty("profiler.service.address", String.format(
            "%s:%d", PerfDriver.LOCAL_HOST, myDriver.getPort()));
        fakeAndroid.loadDex(ProcessRunner.getProcessPath("profiler.service.location"));
        fakeAndroid.loadDex(ProcessRunner.getProcessPath("app.dex.location"));
        fakeAndroid.launchActivity("com.activity.MyActivity");
        // Verify that the activity we launched was created.
        ActivityDataResponse response = grpc.getActivity(grpc.getProcessId());
        assertEquals(response.getData(0).getName(), "My Activity");
    }
}
