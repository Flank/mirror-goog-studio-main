/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.testutils.TestUtils;
import com.android.tools.deployer.devices.DeviceId;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class DeployerRunnerDeviceSelectorTest {
    private static final long WAIT_TIME_MS = TimeUnit.SECONDS.toMillis(10);
    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    private FakeAdbServer fakeAdbServer;
    private final FakeDeviceHandler handler = new FakeDeviceHandler();

    private static File dexDbFile;
    private DeploymentCacheDatabase cacheDb;
    private SqlApkFileDatabase dexDb;
    private UIService service;

    private FakeDevice device0;
    private FakeDevice device1;
    private FakeDevice device2;

    @BeforeClass
    public static void prepare() throws Exception {
        dexDbFile = File.createTempFile("cached_db", ".bin");
        dexDbFile.delete();
        // Fill in the database file by calling dump() at least once.
        // From then on, we will just keep copying this file and reusing it
        // for every test.
        new SqlApkFileDatabase(dexDbFile, null).dump();
        dexDbFile.deleteOnExit();
    }

    @Before
    public void setUp() throws Exception {
        File dbFile = File.createTempFile("test_db", ".bin");
        dbFile.deleteOnExit();
        FileUtils.copyFile(dexDbFile, dbFile);
        dexDb = new SqlApkFileDatabase(dbFile, null);
        cacheDb = new DeploymentCacheDatabase(2);
        service = Mockito.mock(UIService.class);

        fakeAdbServer =
                new FakeAdbServer.Builder()
                        .installDefaultCommandHandlers()
                        .addDeviceHandler(handler)
                        .build();
        fakeAdbServer.start();

        FakeDeviceLibrary library = new FakeDeviceLibrary();
        device0 = library.build(DeviceId.API_28, "Google", "Pixel", "0");
        device1 = library.build(DeviceId.API_28, "Google", "Pixel", "1");
        device2 = library.build(DeviceId.API_28, "Google", "Pixel", "2");

        AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
    }

    @Test
    public void testInstallCorrectDevice() throws Exception {
        assertTrue(device0.getApps().isEmpty());
        assertTrue(device1.getApps().isEmpty());
        assertTrue(device2.getApps().isEmpty());

        handler.connect(device0, fakeAdbServer);
        handler.connect(device1, fakeAdbServer);
        handler.connect(device2, fakeAdbServer);

        DeployerRunner runner = new DeployerRunner(cacheDb, dexDb, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath,
            "--device=2"
        };
        assertEquals(0, runner.run(args));
        assertTrue(device0.getApps().isEmpty());
        assertTrue(device1.getApps().isEmpty());
        assertEquals(1, device2.getApps().size());
        assertInstalled(device2, "com.example.helloworld", file);
    }

    @Test
    public void testMissingDevice() throws Exception {
        assertTrue(device0.getApps().isEmpty());
        assertTrue(device1.getApps().isEmpty());
        assertTrue(device2.getApps().isEmpty());

        handler.connect(device0, fakeAdbServer);
        handler.connect(device1, fakeAdbServer);

        DeployerRunner runner = new DeployerRunner(cacheDb, dexDb, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath,
            "--device=0",
            "--device=1",
            "--device=2"
        };
        assertEquals(1003, runner.run(args));
        assertTrue(device0.getApps().isEmpty());
        assertTrue(device1.getApps().isEmpty());
        assertTrue(device2.getApps().isEmpty());
    }

    @Test
    public void testInstallMultipleDevices() throws Exception {
        assertTrue(device0.getApps().isEmpty());
        assertTrue(device1.getApps().isEmpty());
        assertTrue(device2.getApps().isEmpty());

        handler.connect(device0, fakeAdbServer);
        handler.connect(device1, fakeAdbServer);
        handler.connect(device2, fakeAdbServer);

        DeployerRunner runner = new DeployerRunner(cacheDb, dexDb, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath,
            "--device=2",
            "--device=0"
        };
        assertEquals(0, runner.run(args));
        assertEquals(1, device0.getApps().size());
        assertTrue(device1.getApps().isEmpty());
        assertEquals(1, device2.getApps().size());
        assertInstalled(device0, "com.example.helloworld", file);
        assertInstalled(device2, "com.example.helloworld", file);
    }

    @Test
    public void testNoDeviceConnected() throws Exception {
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDb, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath,
            "--device=2"
        };
        assertEquals(1003, runner.run(args));

        String[] otherArgs = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath
        };
        assertEquals(1003, runner.run(otherArgs));
    }

    @After
    public void after() throws InterruptedException {
        fakeAdbServer.stop();
        boolean status = fakeAdbServer.awaitServerTermination(WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        assertTrue(status);
        AndroidDebugBridge.terminate();
        AndroidDebugBridge.disableFakeAdbServerMode();
    }

    private static void assertInstalled(FakeDevice device, String packageName, Path... files)
            throws IOException {
        assertArrayEquals(new String[] {packageName}, device.getApps().toArray());
        List<String> paths = device.getAppPaths(packageName);
        assertEquals(files.length, paths.size());
        for (int i = 0; i < paths.size(); i++) {
            byte[] expected = Files.readAllBytes(files[i]);
            assertArrayEquals(expected, device.readFile(paths.get(i)));
        }
    }
}
