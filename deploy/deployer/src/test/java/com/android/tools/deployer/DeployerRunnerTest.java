/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler;
import com.android.testutils.TestUtils;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DeployerRunnerTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    private final FakeDevice device;
    private FakeAdbServer myAdbServer;

    @Parameterized.Parameters(name = "{0}")
    public static List<FakeDevice> getDevices() {
        return new FakeDeviceLibrary().getDevices();
    }

    public DeployerRunnerTest(FakeDevice device) {
        this.device = device;
    }

    @Before
    public void setup() throws Exception {
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.setHostCommandHandler(
                TrackDevicesCommandHandler.COMMAND, TrackDevicesCommandHandler::new);
        builder.addDeviceHandler(new FakeDeviceHandler(device));

        myAdbServer = builder.build();
        device.connectTo(myAdbServer);
        myAdbServer.start();
        AndroidDebugBridge.enableFakeAdbServerMode(myAdbServer.getPort());
    }

    @After
    public void teardown() throws Exception {
        AndroidDebugBridge.terminate();
        myAdbServer.close();
    }

    @Test
    public void testInstallSuccessful() throws Exception {
        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"));
        DeployerRunner runner = new DeployerRunner(db);
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        String[] args = {"install", "com.example.helloworld", file.getAbsolutePath()};
        List<String> tasks = runner.run(args);
        assertTrue(!tasks.isEmpty());
        assertEquals(1, device.getApps().size());
        byte[] expected = Files.readAllBytes(file.toPath());
        assertArrayEquals(expected, device.getApps().get(0));
    }
}
