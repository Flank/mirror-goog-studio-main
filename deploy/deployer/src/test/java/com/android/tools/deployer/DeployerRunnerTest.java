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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler;
import com.android.testutils.AssumeUtil;
import com.android.testutils.TestUtils;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.tools.deployer.devices.FakeDeviceLibrary.DeviceId;
import com.android.tools.perflogger.Benchmark;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class DeployerRunnerTest {
    @Rule public TestName name = new TestName();

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    private UIService service;
    private final FakeDevice device;
    private FakeAdbServer myAdbServer;
    private ILogger logger;

    private Benchmark benchmark;
    private long startTime;

    @Parameterized.Parameters(name = "{0}")
    public static DeviceId[] getDevices() {
        return DeviceId.values();
    }

    public DeployerRunnerTest(DeviceId id) throws Exception {
        this.device = new FakeDeviceLibrary().build(id);
    }

    @Before
    public void setUp() throws Exception {
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.setHostCommandHandler(
                TrackDevicesCommandHandler.COMMAND, TrackDevicesCommandHandler::new);
        FakeDeviceHandler handler = new FakeDeviceHandler();
        builder.addDeviceHandler(handler);

        myAdbServer = builder.build();
        handler.connect(device, myAdbServer);
        myAdbServer.start();
        this.service = Mockito.mock(UIService.class);
        logger = new TestLogger();
        AndroidDebugBridge.enableFakeAdbServerMode(myAdbServer.getPort());

        if ("true".equals(System.getProperty("dashboards.enabled"))) {
            // Put all APIs (parameters) of a particular test into one benchmark.
            String benchmarkName = name.getMethodName().replaceAll("\\[.*", "");
            benchmark =
                    new Benchmark.Builder(benchmarkName)
                            .setProject("Android Studio Deployment")
                            .build();
            startTime = System.currentTimeMillis();
        }
    }

    @After
    public void tearDown() throws Exception {
        long currentTime = System.currentTimeMillis();
        if (benchmark != null) {
            long timeTaken = currentTime - startTime;

            // Benchmark names can only include [a-zA-Z0-9_-] characters in them.
            String metricName =
                    name.getMethodName().replace('[', '-').replace("]", "").replace(',', '_');
            benchmark.log(metricName + "_time", timeTaken);
        }
        System.out.print(
                new String(Files.readAllBytes(device.getLogcatFile().toPath()), Charsets.UTF_8));
        device.shutdown();
        Mockito.verifyNoMoreInteractions(service);
        AndroidDebugBridge.terminate();
        myAdbServer.close();
    }

    @Test
    public void testFullInstallSuccessful() throws Exception {
        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        String[] args = {
            "install", "com.example.helloworld", file.getAbsolutePath(), "--force-full-install"
        };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());
        assertInstalled("com.example.helloworld", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");
        assertFalse(device.hasFile("/data/local/tmp/sample.apk"));
    }

    @Test
    public void testAttemptDeltaInstallWithoutPreviousInstallation() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.getAbsolutePath(),
            "--installers-path=" + installersPath.getAbsolutePath()
        };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());

        assertInstalled("com.example.helloworld", file);

        if (device.getApi() < 21) {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
            assertHistory(
                    device,
                    "getprop",
                    "pm install -r -t \"/data/local/tmp/sample.apk\"",
                    "rm \"/data/local/tmp/sample.apk\"");
        } else if (device.getApi() < 24) {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
            assertHistory(
                    device,
                    "getprop",
                    "pm install-create -r -t -S ${size:com.example.helloworld}",
                    "pm install-write -S ${size:com.example.helloworld} 1 0_sample -",
                    "pm install-commit 1");
        } else {
            String packageCommand = device.getApi() < 28 ? "dump" : "path";
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DUMP_UNKNOWN_PACKAGE",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.helloworld",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.helloworld",
                    "/system/bin/run-as com.example.helloworld id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.helloworld", packageCommand),
                    "cmd package install-create -r -t -S ${size:com.example.helloworld}",
                    "cmd package install-write -S ${size:com.example.helloworld} 1 0_sample -",
                    "cmd package install-commit 1");
        }
    }

    @Test
    public void testSkipInstall() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install", "com.example.simpleapp", file.getAbsolutePath(), "--force-full-install"
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");
        device.getShell().clearHistory();

        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    file.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());

        assertInstalled("com.example.simpleapp", file);

        if (device.getApi() < 24) {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        } else {
            String packageCommand = device.getApi() < 28 ? "dump" : "path";
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    "am force-stop com.example.simpleapp");
            assertMetrics(runner.getMetrics(), "INSTALL:SKIPPED_INSTALL");
        }
    }

    @Test
    public void testDeltaInstall() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install", "com.example.simpleapp", file.getAbsolutePath(), "--force-full-install"
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");
        device.getShell().clearHistory();

        file = TestUtils.getWorkspaceFile(BASE + "apks/simple+code.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    file.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());

        assertInstalled("com.example.simpleapp", file);

        if (device.getApi() < 24) {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        } else {
            String packageCommand = device.getApi() < 28 ? "dump" : "path";
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION deltainstall",
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL_UPLOAD",
                    "DELTAINSTALL_INSTALL",
                    "DELTAINSTALL:SUCCESS");
        }
    }

    @Test
    public void testInstallOldVersion() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File v2 = TestUtils.getWorkspaceFile(BASE + "apks/simple+ver.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install", "com.example.simpleapp", v2.getAbsolutePath(), "--force-full-install"
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", v2);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");

        device.getShell().clearHistory();

        File v1 = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    v1.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };

        Mockito.when(service.prompt(ArgumentMatchers.anyString())).thenReturn(false);

        int retcode = runner.run(args, logger);
        assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), retcode);
        assertEquals(1, device.getApps().size());

        // Check old app still installed
        assertInstalled("com.example.simpleapp", v2);

        if (device.getApi() == 19) {
            assertHistory(
                    device, "getprop", "pm install -r -t \"/data/local/tmp/simple.apk\""
                    // ,"rm \"/data/local/tmp/simple.apk\"" TODO: ddmlib doesn't remove when installation fails
                    );
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:INSTALL_FAILED_VERSION_DOWNGRADE");
        } else if (device.getApi() < 24) {
            assertHistory(
                    device,
                    "getprop",
                    "pm install-create -r -t -S ${size:com.example.simpleapp}", // TODO: passing size on create?
                    "pm install-write -S ${size:com.example.simpleapp} 2 0_simple -",
                    "pm install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:INSTALL_FAILED_VERSION_DOWNGRADE");
        } else {
            String packageCommand = device.getApi() < 28 ? "dump" : "path";
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION deltainstall",
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL_UPLOAD",
                    "DELTAINSTALL_INSTALL",
                    "DELTAINSTALL:ERROR.INSTALL_FAILED_VERSION_DOWNGRADE");
        }
        Mockito.verify(service, Mockito.times(1)).prompt(ArgumentMatchers.anyString());
    }

    @Test
    public void testInstallSplit() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File base = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File split = TestUtils.getWorkspaceFile(BASE + "apks/split.apk");

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.getAbsolutePath(),
            split.getAbsolutePath(),
            "--force-full-install"
        };

        int code = runner.run(args, logger);
        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertInstalled("com.example.simpleapp", base, split);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        }
    }

    @Test
    public void testInstallVersionMismatchSplit() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File base = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File split = TestUtils.getWorkspaceFile(BASE + "apks/split+ver.apk");

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.getAbsolutePath(),
            split.getAbsolutePath(),
            "--force-full-install"
        };

        int code = runner.run(args, logger);
        assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
        if (device.getApi() < 21) {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:INSTALL_FAILED_INVALID_APK");
        }
    }

    @Test
    public void testBadDeltaOnSplit() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File base = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File split = TestUtils.getWorkspaceFile(BASE + "apks/split.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.getAbsolutePath(),
            split.getAbsolutePath(),
            "--force-full-install"
        };

        int code = runner.run(args, logger);
        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertInstalled("com.example.simpleapp", base, split);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        }

        device.getShell().clearHistory();

        File update = TestUtils.getWorkspaceFile(BASE + "apks/split+ver.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    base.getAbsolutePath(),
                    update.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };

        code = runner.run(args, logger);

        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertEquals(1, device.getApps().size());

            // Check old app still installed
            assertInstalled("com.example.simpleapp", base, split);

            if (device.getApi() < 24) {
                assertHistory(
                        device,
                        "getprop",
                        "pm install-create -r -t -S ${size:com.example.simpleapp}",
                        "pm install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "pm install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split_ver -",
                        "pm install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL:API_NOT_SUPPORTED",
                        "INSTALL:INSTALL_FAILED_INVALID_APK");
            } else {
                String packageCommand = device.getApi() < 28 ? "dump" : "path";
                assertHistory(
                        device,
                        "getprop",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "mkdir -p /data/local/tmp/.studio/bin",
                        "chmod +x /data/local/tmp/.studio/bin/installer",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION deltainstall",
                        "/system/bin/cmd package install-create -t -r",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 split_split_01.apk",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 base.apk",
                        "/system/bin/cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL_UPLOAD",
                        "DELTAINSTALL_INSTALL",
                        "DELTAINSTALL:ERROR.INSTALL_FAILED_INVALID_APK");
            }
        }
    }

    @Test
    public void testDeltaOnSplit() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File base = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File split = TestUtils.getWorkspaceFile(BASE + "apks/split.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.getAbsolutePath(),
            split.getAbsolutePath(),
            "--force-full-install"
        };

        int code = runner.run(args, logger);
        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertInstalled("com.example.simpleapp", base, split);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        }

        device.getShell().clearHistory();

        File update = TestUtils.getWorkspaceFile(BASE + "apks/split+code.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    base.getAbsolutePath(),
                    update.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };

        code = runner.run(args, logger);

        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertEquals(0, code);
            assertEquals(1, device.getApps().size());

            // Check new app installed
            assertInstalled("com.example.simpleapp", base, update);

            if (device.getApi() < 24) {
                assertHistory(
                        device,
                        "getprop",
                        "pm install-create -r -t -S ${size:com.example.simpleapp}",
                        "pm install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "pm install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split_code -",
                        "pm install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL:API_NOT_SUPPORTED",
                        "INSTALL:OK",
                        "DDMLIB_UPLOAD",
                        "DDMLIB_INSTALL");
            } else {
                String packageCommand = device.getApi() < 28 ? "dump" : "path";
                assertHistory(
                        device,
                        "getprop",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "mkdir -p /data/local/tmp/.studio/bin",
                        "chmod +x /data/local/tmp/.studio/bin/installer",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION deltainstall",
                        "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 split_split_01.apk",
                        "/system/bin/cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL_UPLOAD",
                        "DELTAINSTALL_INSTALL",
                        "DELTAINSTALL:SUCCESS");
            }
        }
    }

    @Test
    public void testAddSplit() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File base = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File split = TestUtils.getWorkspaceFile(BASE + "apks/split.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.getAbsolutePath(),
            split.getAbsolutePath(),
            "--force-full-install"
        };

        int code = runner.run(args, logger);
        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertInstalled("com.example.simpleapp", base, split);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        }

        device.getShell().clearHistory();

        File added = TestUtils.getWorkspaceFile(BASE + "apks/split2.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    base.getAbsolutePath(),
                    split.getAbsolutePath(),
                    added.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };

        code = runner.run(args, logger);

        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertEquals(0, code);
            assertEquals(1, device.getApps().size());

            // Check new app installed
            assertInstalled("com.example.simpleapp", base, split, added);

            if (device.getApi() < 24) {
                assertHistory(
                        device,
                        "getprop",
                        "pm install-create -r -t -S ${size:com.example.simpleapp}",
                        "pm install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "pm install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split -",
                        "pm install-write -S ${size:com.example.simpleapp:split_split_02.apk} 2 2_split_ -",
                        "pm install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL:API_NOT_SUPPORTED",
                        "INSTALL:OK",
                        "DDMLIB_UPLOAD",
                        "DDMLIB_INSTALL");
            } else {
                String packageCommand = device.getApi() < 28 ? "dump" : "path";
                assertHistory(
                        device,
                        "getprop",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "mkdir -p /data/local/tmp/.studio/bin",
                        "chmod +x /data/local/tmp/.studio/bin/installer",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        "cmd package install-create -r -t -S ${size:com.example.simpleapp}",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split -",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_02.apk} 2 2_split_ -",
                        "cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL:CANNOT_GENERATE_DELTA",
                        "INSTALL:OK",
                        "DDMLIB_UPLOAD",
                        "DDMLIB_INSTALL");
            }
        }
    }

    @Test
    public void testRemoveSplit() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File base = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File split1 = TestUtils.getWorkspaceFile(BASE + "apks/split.apk");
        File split2 = TestUtils.getWorkspaceFile(BASE + "apks/split2.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.getAbsolutePath(),
            split1.getAbsolutePath(),
            split2.getAbsolutePath(),
            "--force-full-install"
        };

        int code = runner.run(args, logger);
        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertInstalled("com.example.simpleapp", base, split1, split2);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        }

        device.getShell().clearHistory();

        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    base.getAbsolutePath(),
                    split1.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };

        code = runner.run(args, logger);

        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertEquals(0, code);
            assertEquals(1, device.getApps().size());

            // Check new app installed
            assertInstalled("com.example.simpleapp", base, split1);

            if (device.getApi() < 24) {
                assertHistory(
                        device,
                        "getprop",
                        "pm install-create -r -t -S ${size:com.example.simpleapp}",
                        "pm install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "pm install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split -",
                        "pm install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL:API_NOT_SUPPORTED",
                        "INSTALL:OK",
                        "DDMLIB_UPLOAD",
                        "DDMLIB_INSTALL");
            } else {
                String packageCommand = device.getApi() < 28 ? "dump" : "path";
                assertHistory(
                        device,
                        "getprop",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "mkdir -p /data/local/tmp/.studio/bin",
                        "chmod +x /data/local/tmp/.studio/bin/installer",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        "cmd package install-create -r -t -S ${size:com.example.simpleapp}",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split -",
                        "cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL:CANNOT_GENERATE_DELTA",
                        "INSTALL:OK",
                        "DDMLIB_UPLOAD",
                        "DDMLIB_INSTALL");
            }
        }
    }

    @Test
    public void testAddAsset() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install", "com.example.simpleapp", file.getAbsolutePath(), "--force-full-install"
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");
        device.getShell().clearHistory();

        file = TestUtils.getWorkspaceFile(BASE + "apks/simple+new_asset.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    file.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());

        assertInstalled("com.example.simpleapp", file);

        if (device.getApi() < 24) {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        } else {
            String packageCommand = device.getApi() < 28 ? "dump" : "path";
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION deltainstall",
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL_UPLOAD",
                    "DELTAINSTALL_INSTALL",
                    "DELTAINSTALL:SUCCESS");
        }
    }

    @Test
    public void testAddAssetWithSplits() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File base = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File split = TestUtils.getWorkspaceFile(BASE + "apks/split.apk");
        File installersPath = DeployerTestUtils.prepareInstaller();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.getAbsolutePath(),
            split.getAbsolutePath(),
            "--force-full-install"
        };

        int code = runner.run(args, logger);
        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertInstalled("com.example.simpleapp", base, split);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DISABLED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        }

        device.getShell().clearHistory();

        File newBase = TestUtils.getWorkspaceFile(BASE + "apks/simple+new_asset.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    newBase.getAbsolutePath(),
                    split.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };

        code = runner.run(args, logger);

        if (device.getApi() < 21) {
            assertEquals(DeployerException.Error.INSTALL_FAILED.ordinal(), code);
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:MULTI_APKS_NO_SUPPORTED_BELOW21");
        } else {
            assertEquals(0, code);
            assertEquals(1, device.getApps().size());

            // Check new app installed
            assertInstalled("com.example.simpleapp", newBase, split);

            if (device.getApi() < 24) {
                assertHistory(
                        device,
                        "getprop",
                        "pm install-create -r -t -S ${size:com.example.simpleapp}",
                        "pm install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple_new_asset -",
                        "pm install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split -",
                        "pm install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL:API_NOT_SUPPORTED",
                        "INSTALL:OK",
                        "DDMLIB_UPLOAD",
                        "DDMLIB_INSTALL");
            } else {
                String packageCommand = device.getApi() < 28 ? "dump" : "path";
                assertHistory(
                        device,
                        "getprop",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "mkdir -p /data/local/tmp/.studio/bin",
                        "chmod +x /data/local/tmp/.studio/bin/installer",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION deltainstall",
                        "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 base.apk",
                        "/system/bin/cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL_UPLOAD",
                        "DELTAINSTALL_INSTALL",
                        "DELTAINSTALL:SUCCESS");
            }
        }
    }

    @Test
    public void testStartApp() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host
        assertTrue(device.getApps().isEmpty());
        File installersPath = DeployerTestUtils.prepareInstaller();

        // Install the base apk:
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        String[] args = {
            "install",
            "com.example.simpleapp",
            file.getAbsolutePath(),
            "--installers-path=" + installersPath.getAbsolutePath()
        };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());
        assertInstalled("com.example.simpleapp", file);

        String cmd = "am start -n com.example.simpleapp/.MainActivity -a android.intent.action.MAIN";
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);
        List<FakeDevice.AndroidProcess> processes = device.getProcesses();
        assertEquals(1, processes.size());
        assertEquals("com.example.simpleapp", processes.get(0).application.packageName);

        assertEquals(0, device.executeScript("am force-stop com.foo", new byte[] {}).value);
        processes = device.getProcesses();
        assertEquals(1, processes.size());
        assertEquals("com.example.simpleapp", processes.get(0).application.packageName);

        assertEquals(
                0,
                device.executeScript("am force-stop com.example.simpleapp.bar", new byte[] {})
                        .value);
        processes = device.getProcesses();
        assertEquals(1, processes.size());
        assertEquals("com.example.simpleapp", processes.get(0).application.packageName);

        assertNotEquals(0, device.executeScript("am force-stop", new byte[] {}).value);
        processes = device.getProcesses();
        assertEquals(1, processes.size());
        assertEquals("com.example.simpleapp", processes.get(0).application.packageName);

        assertEquals(
                0,
                device.executeScript("am force-stop com.example.simpleapp", new byte[] {}).value);
        processes = device.getProcesses();
        assertEquals(0, processes.size());
    }

    @Test
    public void testApkNotRecognized() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        // Install the base apk:
        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        File installersPath = DeployerTestUtils.prepareInstaller();
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        String[] args = {
            "install",
            "com.example.simpleapp",
            file.getAbsolutePath(),
            "--installers-path=" + installersPath.getAbsolutePath()
        };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());
        assertInstalled("com.example.simpleapp", file);

        if (device.getApi() < 24) {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        } else {
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DUMP_UNKNOWN_PACKAGE",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
        }

        file = TestUtils.getWorkspaceFile(BASE + "apks/simple+code.apk");
        args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    file.getAbsolutePath(),
                    "--installers-path=" + installersPath.getAbsolutePath()
                };

        // We create a empty database. This simulate an installed APK not found in the database.
        db = new SqlApkFileDatabase(File.createTempFile("test_db_empty", ".bin"), null);
        device.getShell().clearHistory();
        runner = new DeployerRunner(db, service);
        retcode = runner.run(args, logger);
        if (device.supportsJvmti()) {
            assertEquals(DeployerException.Error.REMOTE_APK_NOT_FOUND_IN_DB.ordinal(), retcode);
        } else {
            assertEquals(DeployerException.Error.CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
        }
        if (device.getApi() < 26) {
            assertTrue(runner.getMetrics().isEmpty());
            assertHistory(device, "getprop");
        } else {
            String packageCommand = device.getApi() < 28 ? "dump" : "path";
            assertMetrics(runner.getMetrics(), "DELTAPREINSTALL_WRITE");
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION deltapreinstall",
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk");
        }
    }

    @Test
    public void testDump() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        String packageName = "com.example.simpleapp";
        assertTrue(device.getApps().isEmpty());
        File installersPath = DeployerTestUtils.prepareInstaller();
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"), null);
        DeployerRunner runner = new DeployerRunner(db, service);

        AndroidDebugBridge.init(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            Thread.sleep(100);
        }
        IDevice iDevice = bridge.getDevices()[0];
        AdbClient adb = new AdbClient(iDevice, logger);
        ArrayList<DeployMetric> metrics = new ArrayList<>();
        Installer installer =
                new AdbInstaller(installersPath.getAbsolutePath(), adb, metrics, logger);

        // Make sure we have true negative.
        Deploy.DumpResponse response = installer.dump(Collections.singletonList(packageName));
        assertEquals(Deploy.DumpResponse.Status.ERROR_PACKAGE_NOT_FOUND, response.getStatus());
        AndroidDebugBridge.terminate();

        // Install our target APK.
        {
            File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
            String[] args = {
                "install",
                packageName,
                file.getAbsolutePath(),
                "--installers-path=" + installersPath.getAbsolutePath()
            };
            int retcode = runner.run(args, logger);
            assertEquals(0, retcode);
            assertEquals(1, device.getApps().size());
            assertInstalled(packageName, file);
        }

        // Make sure we have true positive and no false negative.
        response = installer.dump(Collections.singletonList(packageName));
        if (device.getApi() < 24) {
            // No "cmd" on APIs < 24.
            assertEquals(Deploy.DumpResponse.Status.ERROR_PACKAGE_NOT_FOUND, response.getStatus());
        } else {
            assertEquals(Deploy.DumpResponse.Status.OK, response.getStatus());
            assertEquals(1, response.getPackagesCount());
            assertEquals(1, response.getPackages(0).getApksCount());
            assertEquals(
                    device.getAppPaths(packageName).get(0),
                    response.getPackages(0).getApks(0).getAbsolutePath());
        }

        // Make sure we don't have false positive.
        response = installer.dump(Collections.singletonList("foo.bar"));
        assertEquals(Deploy.DumpResponse.Status.ERROR_PACKAGE_NOT_FOUND, response.getStatus());
    }

    private static void assertHistory(FakeDevice device, String... expectedHistory)
            throws IOException {
        List<String> actualHistory = device.getShell().getHistory();
        String actual = String.join("\n", actualHistory);
        String expected = String.join("\n", expectedHistory);

        // Apply the right version
        expected = expected.replaceAll("\\$VERSION", Version.hash());

        // Find the right sizes:
        Pattern pattern = Pattern.compile("\\$\\{size:([^:}]*)(:([^:}]*))?}");
        Matcher matcher = pattern.matcher(expected);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String pkg = matcher.group(1);
            String file = matcher.group(3);
            List<String> paths = device.getAppPaths(pkg);
            int size = 0;
            for (String path : paths) {
                if (file == null || path.endsWith("/" + file)) {
                    size += device.readFile(path).length;
                }
            }
            matcher.appendReplacement(buffer, Integer.toString(size));
        }
        matcher.appendTail(buffer);
        expected = buffer.toString();

        assertEquals(expected, actual);
    }

    public void assertInstalled(String packageName, File... files) throws IOException {
        assertArrayEquals(new String[] {packageName}, device.getApps().toArray());
        List<String> paths = device.getAppPaths(packageName);
        assertEquals(files.length, paths.size());
        for (int i = 0; i < paths.size(); i++) {
            byte[] expected = Files.readAllBytes(files[i].toPath());
            assertArrayEquals(expected, device.readFile(paths.get(i)));
        }
    }

    private void assertMetrics(ArrayList<DeployMetric> metrics, String... expected) {
        String[] actual =
                metrics.stream()
                        .map(m -> m.getName() + (m.hasStatus() ? ":" + m.getStatus() : ""))
                        .toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    private static class TestLogger implements ILogger {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> infos = new ArrayList<>();
        List<String> verboses = new ArrayList<>();

        @Override
        public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
            errors.add(String.format(msgFormat, args));
        }

        @Override
        public void warning(@NonNull String msgFormat, Object... args) {
            warnings.add(String.format(msgFormat, args));
        }

        @Override
        public void info(@NonNull String msgFormat, Object... args) {
            infos.add(String.format(msgFormat, args));
        }

        @Override
        public void verbose(@NonNull String msgFormat, Object... args) {
            verboses.add(String.format(msgFormat, args));
        }
    }
}
