package com.android.instantapp.provision;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.instantapp.utils.InstantAppTests;
import org.junit.Test;

/** Tests for {@link UnprovisionRunner}. */
public class UnprovisionRunnerTest {
    @NonNull
    private static final String SUPERVISOR_PACKAGE = "com.google.android.instantapps.supervisor";

    @NonNull private static final String DEVMAN_PACKAGE = "com.google.android.instantapps.devman";

    private UnprovisionRunner unprovisionRunner = new UnprovisionRunner();

    @Test
    public void testUninstallCorrectly() throws Throwable {
        IDevice device =
                new InstantAppTests.DeviceGenerator()
                        .setResponseToCommand("pm path " + SUPERVISOR_PACKAGE, "path")
                        .setResponseToCommand("pm path " + DEVMAN_PACKAGE, "")
                        .getDevice();
        unprovisionRunner.runUnprovision(device);
        verify(device, times(1)).uninstallPackage(eq(SUPERVISOR_PACKAGE));
        verify(device, times(0)).uninstallPackage(eq(DEVMAN_PACKAGE));
    }
}
