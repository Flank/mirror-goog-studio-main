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
import com.android.testutils.AssumeUtil;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.rules.ApiLevel;
import com.android.tools.deployer.rules.FakeDeviceConnection;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/**
 * This test works in the same manner as the DeployerRunnerTest with a fake adb server to which
 * ddmlib connects.
 */
@RunWith(ApiLevel.class)
public class AdbInstallerTest {

    private static final String INVOCATION =
            AdbInstaller.INSTALLER_PATH + " -version=wrong_version_hash";

    private static final String INSTALLER_WORKSPACE =
            Deployer.INSTALLER_DIRECTORY + " " + Deployer.INSTALLER_TMP_DIRECTORY;
    public static final String RM_DIR = "rm -fr " + INSTALLER_WORKSPACE;
    public static final String MK_DIR = "mkdir -p " + INSTALLER_WORKSPACE;
    public static final String CHMOD = "chmod +x " + AdbInstaller.INSTALLER_PATH;

    @Rule @ApiLevel.Init public FakeDeviceConnection connection;
    private FakeDevice device;
    private ILogger logger;

    @Before
    public void setUp() {
        device = connection.getDevice();
        logger = new TestLogger();
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

        String[] expectedHistory = {"getprop", INVOCATION, RM_DIR, MK_DIR, CHMOD, INVOCATION};

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
