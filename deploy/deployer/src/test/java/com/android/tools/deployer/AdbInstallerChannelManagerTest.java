/*
 * Copyright (C) 2022 The Android Open Source Project
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
import java.io.File;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ApiLevel.class)
public class AdbInstallerChannelManagerTest {

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
    @ApiLevel.InRange(max = 31, min = 31)
    public void testClosedChannelDetection() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        File installersPath = DeployerTestUtils.prepareInstaller();

        AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            Thread.sleep(100);
        }

        List<DeployMetric> noop = new ArrayList<>();
        LocalHostInstallerAdbClient client =
                new LocalHostInstallerAdbClient(
                        getDevice(bridge), logger, installersPath + "/x86/installer");

        String executable = installersPath.getAbsolutePath();
        AdbInstaller installer =
                new AdbInstaller(executable, client, noop, logger, AdbInstaller.Mode.DAEMON);

        // Request to time out.
        try {
            installer.timeout(Timeouts.CMD_TIMEOUT + 1000);
            Assert.fail("Timeout did not timeout!");
        } catch (Exception e) {
            // Expected since we requested a timeout.
        }

        // Now the channel should have been closed by the AdbInstallerChannelManager
        try {
            installer.dump(new ArrayList<>());
        } catch (Exception e) {
        }

        // Even though we send two requests and both timed out, we should have pushed the binary
        // only once (so we should have only one set of rm/mkdir/chmod).
        String[] expectedHistory = {"getprop", RM_DIR, MK_DIR, CHMOD};
        assertHistory(device, expectedHistory);
    }

    private static void assertHistory(FakeDevice device, String... expect) {
        String actual = String.join("\n", device.getShell().getHistory());
        String expected = String.join("\n", expect);
        Assert.assertEquals("", expected, actual);
    }

    private IDevice getDevice(AndroidDebugBridge bridge) {
        IDevice[] devices = bridge.getDevices();
        if (devices.length < 1) {
            return null;
        }
        return devices[0];
    }

    class LocalHostInstallerAdbClient extends AdbClient {

        private final String installerPath;

        public LocalHostInstallerAdbClient(IDevice device, ILogger logger, String installerPath) {
            super(device, logger);
            this.installerPath = installerPath;
        }

        @Override
        public SocketChannel rawExec(String executable, String[] parameters) {
            if (executable.equals(AdbInstaller.INSTALLER_PATH)) {
                return HostInstaller.spawn(Path.of(installerPath), parameters);
            } else {
                throw new IllegalArgumentException("Cannot rawExec: " + executable);
            }
        }
    }
}
