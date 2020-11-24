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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.testutils.AssumeUtil;
import com.android.testutils.TestUtils;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.rules.ApiLevel;
import com.android.tools.deployer.rules.FakeDeviceConnection;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(ApiLevel.class)
public class OptimisticInstallTest {
    @Rule public TestName name = new TestName();
    @Rule @ApiLevel.Init public FakeDeviceConnection connection;

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    private static File installersPath;
    private static File dexDbFile;

    private FakeDevice device;
    private DeployerRunner runner;

    private DeploymentCacheDatabase cacheDb;
    private SqlApkFileDatabase dexDb;
    private UIService service;
    private ILogger logger;

    @BeforeClass
    public static void prepare() throws Exception {
        AssumeUtil.assumeNotWindows();
        installersPath = DeployerTestUtils.prepareInstaller();

        // Fill in the database file by calling dump() at least once.
        // From then on, we will just keep copying this file and reusing it
        // for every test.
        dexDbFile = File.createTempFile("cached_db", ".bin");
        dexDbFile.delete();
        new SqlApkFileDatabase(dexDbFile, null).dump();
        dexDbFile.deleteOnExit();
    }

    @Before
    public void setUp() throws Exception {
        device = connection.getDevice();
        service = Mockito.mock(UIService.class);
        logger = new TestLogger();

        File dbFile = File.createTempFile("test_db", ".bin");
        dbFile.deleteOnExit();
        FileUtils.copyFile(dexDbFile, dbFile);
        dexDb = new SqlApkFileDatabase(dbFile, null);
        cacheDb = new DeploymentCacheDatabase(2);
        runner = new DeployerRunner(cacheDb, dexDb, service);
    }

    @After
    public void tearDown() throws Exception {
        System.out.print(getLogcatContent(device));
        Mockito.verifyNoMoreInteractions(service);
    }

    @Test
    @ApiLevel.InRange(max = 29)
    public void noOptimisticInstallBeforeApi30() throws Exception {
        assertTrue(device.getApps().isEmpty());
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        assertEquals(
                0,
                runDeployCommand(
                        "install",
                        "com.example.helloworld",
                        file.toString(),
                        "--force-full-install",
                        "--optimistic-install"));
        assertEquals(1, device.getApps().size());
        assertInstalled("com.example.helloworld", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");
    }

    @Test
    @ApiLevel.InRange(min = 30)
    public void fallBackIfNotInstalled() throws Exception {
        assertTrue(device.getApps().isEmpty());
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        assertEquals(
                0,
                runDeployCommand(
                        "install",
                        "com.example.helloworld",
                        file.toString(),
                        "--optimistic-install"));
        assertEquals(1, device.getApps().size());
        assertInstalled("com.example.helloworld", file);
        assertMetrics(
                runner.getMetrics(),
                "IWI_INSTALL_DUMP:DUMP_UNKNOWN_PACKAGE",
                ":Success",
                ":Success",
                ":Success",
                "PARSE_PATHS:Success",
                "OPTIMISTIC_INSTALL:Failed",
                "DELTAINSTALL:DUMP_UNKNOWN_PACKAGE",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");
    }

    @Test
    @ApiLevel.InRange(min = 30)
    public void fallBackOnAddedSplit() throws Exception {
        assertTrue(device.getApps().isEmpty());
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split = TestUtils.resolveWorkspacePath(BASE + "apks/split.apk");

        assertEquals(
                0,
                runDeployCommand(
                        "install",
                        "com.example.simpleapp",
                        base.toString(),
                        "--force-full-install"));
        assertInstalled("com.example.simpleapp", base);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");

        assertEquals(
                0,
                runDeployCommand(
                        "install",
                        "com.example.simpleapp",
                        base.toString(),
                        split.toString(),
                        "--optimistic-install"));
        assertInstalled("com.example.simpleapp", base, split);
        assertMetrics(
                runner.getMetrics(),
                "IWI_INSTALL_DUMP:Success",
                "IWI_INSTALL_DIFF:DIFFERENT_NUMBER_OF_APKS",
                ":Success",
                ":Success",
                ":Success",
                "PARSE_PATHS:Success",
                "OPTIMISTIC_INSTALL:Failed",
                "DELTAINSTALL:CANNOT_GENERATE_DELTA",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");
    }

    private int runDeployCommand(String... args) {
        args = Arrays.copyOf(args, args.length + 1);
        args[args.length - 1] = "--installers-path=" + installersPath.getAbsolutePath();
        return runner.run(args, logger);
    }

    public void assertInstalled(String packageName, Path... files) throws IOException {
        assertArrayEquals(new String[] {packageName}, device.getApps().toArray());
        List<String> paths = device.getAppPaths(packageName);
        assertEquals(files.length, paths.size());
        for (int i = 0; i < paths.size(); i++) {
            byte[] expected = Files.readAllBytes(files[i]);
            assertArrayEquals(expected, device.readFile(paths.get(i)));
        }
    }

    private void assertMetrics(List<DeployMetric> metrics, String... expected) {
        String[] actual =
                metrics.stream()
                        .map(m -> m.getName() + (m.hasStatus() ? ":" + m.getStatus() : ""))
                        .toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    private static String getLogcatContent(FakeDevice device) {
        try {
            return new String(Files.readAllBytes(device.getLogcatFile().toPath()), Charsets.UTF_8);
        } catch (IOException io) {
            return "";
        }
    }
}
