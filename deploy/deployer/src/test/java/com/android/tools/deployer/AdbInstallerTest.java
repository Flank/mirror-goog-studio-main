/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler;
import com.android.testutils.AssumeUtil;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

/**
 * This test works in the same manner as the DeployerRunnerTest with a fake adb server to which
 * ddmlib connects.
 */
@RunWith(Parameterized.class)
public class AdbInstallerTest {

    private FakeAdbServer adbServer;
    private final FakeDevice device;
    private ILogger logger;

    public AdbInstallerTest(FakeDeviceLibrary.DeviceId id) throws Exception {
        this.device = new FakeDeviceLibrary().build(id);
    }

    @Parameterized.Parameters(name = "{0}")
    public static FakeDeviceLibrary.DeviceId[] getDevices() {
        return FakeDeviceLibrary.DeviceId.values();
    }

    @Before
    public void setUp() throws Exception {
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.setHostCommandHandler(
                TrackDevicesCommandHandler.COMMAND, TrackDevicesCommandHandler::new);
        FakeDeviceHandler handler = new FakeDeviceHandler();
        builder.addDeviceHandler(handler);

        adbServer = builder.build();
        handler.connect(device, adbServer);
        adbServer.start();
        logger = new TestLogger();
        AndroidDebugBridge.enableFakeAdbServerMode(adbServer.getPort());
    }

    @After
    public void tearDown() throws Exception {
        device.shutdown();
        AndroidDebugBridge.terminate();
        adbServer.close();
    }

    @Test
    public void testWrongVersionDetection() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        File installersPath = DeployerTestUtils.prepareInstaller();

        AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            Thread.sleep(100);
        }

        List<DeployMetric> unusedMetric = new ArrayList<>();
        AdbClient client = new AdbClient(getDevice(bridge), logger);

        AdbInstaller installer =
                new AdbInstaller(installersPath.getAbsolutePath(), client, unusedMetric, logger);
        AdbInstaller mockInstaller = Mockito.spy(installer);
        Mockito.when(mockInstaller.getVersion()).thenReturn("wrong_version_hash");

        try {
            mockInstaller.dump(ImmutableList.of("foo"));
            Assert.fail("NO exception thrown even though installer failed to install");
        } catch (IOException e) {
        }

        String[] expectedHistory = {
            "getprop",
            "/data/local/tmp/.studio/bin/installer -version=wrong_version_hash dump foo",
            "rm -fr /data/local/tmp/.studio",
            "mkdir -p /data/local/tmp/.studio/bin",
            "chmod +x /data/local/tmp/.studio/bin/installer",
            "/data/local/tmp/.studio/bin/installer -version=wrong_version_hash dump foo"
        };

        assertHistory(device, expectedHistory);
    }

    private static void assertHistory(FakeDevice device, String... expected) {
        Object[] actual = device.getShell().getHistory().toArray();
        Assert.assertArrayEquals("", expected, actual);
    }

    private IDevice getDevice(AndroidDebugBridge bridge) {
        IDevice[] devices = bridge.getDevices();
        if (devices.length < 1) {
            return null;
        }
        return devices[0];
    }
}
