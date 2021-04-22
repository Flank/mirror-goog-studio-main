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

import static com.android.tools.deployer.DeployerException.Error.CANNOT_SWAP_BEFORE_API_26;
import static com.android.tools.deployer.DeployerException.Error.CANNOT_SWAP_RESOURCE;
import static com.android.tools.deployer.DeployerException.Error.DUMP_FAILED;
import static com.android.tools.deployer.DeployerException.Error.DUMP_UNKNOWN_PROCESS;
import static com.android.tools.deployer.DeployerException.Error.NO_ERROR;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.testutils.AssumeUtil;
import com.android.testutils.TestUtils;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.shell.FailingMkdir;
import com.android.tools.deployer.rules.ApiLevel;
import com.android.tools.deployer.rules.FakeDeviceConnection;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.tracer.Trace;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/*
How these tests work:
====================
These tests use the FakeAdbServer infrastructure but none of the default Handler.
Instead, we install our own FakeDeviceHandler.

DeployerRunner -> DDMLIB -> FakeAdbServer -> FakeDeviceHandler | Fake sync (for install command)
                                                               | Fake shell/exec ->| Fake ls
                                                                                   | Fake mkdir
                                                                                   | ...
                                                                                   | installer (external command)

The installer executable is built and runs on the local machine. To work in a non-Android environment,
two mechanisms are used:
1- For filesystem operations, an IO system configured via FAKE_DEVICE_ROOT environment variable redirects all
open/read/write/close/state to a test directory.
2- For exec(3) operations, the workspace Executor is substituted to a RedirectExecutor which forward requests
to a shell based on FAKE_DEVICE_SHELL environment variable.


The DeployerRunner runs as is and the DeviceHandler records all sync/exec/shell commands received by the device.
As the end of each test, the list of commands received is compared against the list of commands expected.

Concurrency: These tests are NEVER sharded on the same machine. Therefore, having one FakeAdbServer is not a problem.
             A single FakeAdbServer can also be used in other tests.
*/

@RunWith(ApiLevel.class)
public class DeployerRunnerTest {
    @Rule public TestName name = new TestName();
    @Rule @ApiLevel.Init public FakeDeviceConnection connection;

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    private static File dexDbFile;
    private DeploymentCacheDatabase cacheDb;
    private SqlApkFileDatabase dexDB;
    private UIService service;
    private FakeDevice device;
    private ILogger logger;

    private Benchmark benchmark;
    private long startTime;

    private static final String INSTALLER_INVOCATION =
            AdbInstaller.INSTALLER_PATH + " -version=$VERSION";

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
        this.device = connection.getDevice();
        this.service = Mockito.mock(UIService.class);
        logger = new TestLogger();

        File dbFile = File.createTempFile("test_db", ".bin");
        dbFile.deleteOnExit();
        FileUtils.copyFile(dexDbFile, dbFile);
        dexDB = new SqlApkFileDatabase(dbFile, null);
        cacheDb = new DeploymentCacheDatabase(2);

        if ("true".equals(System.getProperty("dashboards.enabled"))) {
            // Put all APIs (parameters) of a particular test into one benchmark.
            String benchmarkName = name.getMethodName();
            benchmark =
                    new Benchmark.Builder(benchmarkName)
                            .setProject("Android Studio Deployment")
                            .build();
            startTime = System.currentTimeMillis();
        }

        Trace.begin(name.getMethodName());
    }

    @After
    public void tearDown() throws Exception {
        long currentTime = System.currentTimeMillis();
        Trace.end();
        if (benchmark != null) {
            long timeTaken = currentTime - startTime;

            // Benchmark names can only include [a-zA-Z0-9_-] characters in them.
            String metricName = String.format("%s-%s_time", name.getMethodName(), connection.getDeviceId());
            benchmark.log(metricName, timeTaken);
        }
        System.out.print(getLogcatContent(device));
        Mockito.verifyNoMoreInteractions(service);
    }

    @Test
    public void testFullInstallSuccessful() throws Exception {
        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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
    @ApiLevel.InRange(min = 28)
    public void testInstallCoroutineDebuggerSuccessful() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--installers-path=" + installersPath.toString()
        };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());

        assertInstalled("com.example.helloworld", file);
        // file should be there after app install
        assertTrue(
                device.hasFile(
                        Sites.appCodeCache("com.example.helloworld")
                                + "coroutine_debugger_agent.so"));
    }

    @Test
    public void testAttemptDeltaInstallWithoutPreviousInstallation() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.toString(),
            "--installers-path=" + installersPath.toString()
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
        } else if (device.getApi() < 28) {
            String packageCommand = "dump";
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DUMP_UNKNOWN_PACKAGE",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.helloworld
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.helloworld
                    "/system/bin/run-as com.example.helloworld id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.helloworld", packageCommand),
                    "cmd package install-create -r -t -S ${size:com.example.helloworld}",
                    "cmd package install-write -S ${size:com.example.helloworld} 1 0_sample -",
                    "cmd package install-commit 1");
        } else {
            String packageCommand = "path";
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:DUMP_UNKNOWN_PACKAGE",
                    "INSTALL:OK",
                    "DDMLIB_UPLOAD",
                    "DDMLIB_INSTALL");
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.helloworld
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.helloworld
                    "/system/bin/run-as com.example.helloworld id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.helloworld", packageCommand),
                    "cmd package install-create -r -t -S ${size:com.example.helloworld}",
                    "cmd package install-write -S ${size:com.example.helloworld} 1 0_sample -",
                    "cmd package install-commit 1",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                    "/system/bin/run-as com.example.helloworld cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.helloworld")
                            + "coroutine_debugger_agent.so",
                    "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.helloworld")
                            + "coroutine_debugger_agent.so");
        }
    }

    @Test
    @ApiLevel.InRange(max = 29)
    public void testSkipInstall() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
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
        } else if (device.getApi() < 28) {
            String packageCommand = "dump";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION,
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION,
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    "am force-stop com.example.simpleapp");
            assertMetrics(runner.getMetrics(), "INSTALL:SKIPPED_INSTALL");
        } else {
            String packageCommand = "path";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION,
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    "am force-stop com.example.simpleapp",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                    "/system/bin/run-as com.example.simpleapp cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.simpleapp")
                            + "coroutine_debugger_agent.so",
                    "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.simpleapp")
                            + "coroutine_debugger_agent.so");
            assertMetrics(runner.getMetrics(), "INSTALL:SKIPPED_INSTALL");
        }
    }

    @Test
    public void testDeltaInstall() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
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
        } else if (device.getApi() < 28) {
            String packageCommand = "dump";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    INSTALLER_INVOCATION, // deltainstall
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL_UPLOAD",
                    "DELTAINSTALL_INSTALL",
                    "DELTAINSTALL:SUCCESS");
        } else {
            String packageCommand = "path";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    INSTALLER_INVOCATION, // deltainstall
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                    "/system/bin/run-as com.example.simpleapp cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.simpleapp")
                            + "coroutine_debugger_agent.so",
                    "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.simpleapp")
                            + "coroutine_debugger_agent.so");
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path v2 = TestUtils.resolveWorkspacePath(BASE + "apks/simple+ver.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            v2.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        Path v1 = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    v1.toString(),
                    "--installers-path=" + installersPath.toString()
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
        } else if (device.getApi() < 28) {
            String packageCommand = "dump";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    INSTALLER_INVOCATION, // deltainstall
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL_UPLOAD",
                    "DELTAINSTALL_INSTALL",
                    "DELTAINSTALL:ERROR.INSTALL_FAILED_VERSION_DOWNGRADE");
        } else {
            String packageCommand = "path";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    INSTALLER_INVOCATION, // deltainstall
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split = TestUtils.resolveWorkspacePath(BASE + "apks/split.apk");

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.toString(),
            split.toString(),
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split = TestUtils.resolveWorkspacePath(BASE + "apks/split+ver.apk");

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.toString(),
            split.toString(),
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split = TestUtils.resolveWorkspacePath(BASE + "apks/split.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.toString(),
            split.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        Path update = TestUtils.resolveWorkspacePath(BASE + "apks/split+ver.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    base.toString(),
                    update.toString(),
                    "--installers-path=" + installersPath.toString()
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
            } else if (device.getApi() < 28) {
                String packageCommand = "dump";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        AdbInstallerTest.RM_DIR,
                        AdbInstallerTest.MK_DIR,
                        AdbInstallerTest.CHMOD,
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        INSTALLER_INVOCATION, // deltainstall
                        "/system/bin/cmd package install-create -t -r",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 split_split_01.apk",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 base.apk",
                        "/system/bin/cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL_UPLOAD",
                        "DELTAINSTALL_INSTALL",
                        "DELTAINSTALL:ERROR.INSTALL_FAILED_INVALID_APK");
            } else {
                String packageCommand = "path";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        INSTALLER_INVOCATION, // deltainstall
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split = TestUtils.resolveWorkspacePath(BASE + "apks/split.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.toString(),
            split.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        Path update = TestUtils.resolveWorkspacePath(BASE + "apks/split+code.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    base.toString(),
                    update.toString(),
                    "--installers-path=" + installersPath.toString()
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
            } else if (device.getApi() < 28) {
                String packageCommand = "dump";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        AdbInstallerTest.RM_DIR,
                        AdbInstallerTest.MK_DIR,
                        AdbInstallerTest.CHMOD,
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        INSTALLER_INVOCATION, // detalinstall
                        "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 split_split_01.apk",
                        "/system/bin/cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL_UPLOAD",
                        "DELTAINSTALL_INSTALL",
                        "DELTAINSTALL:SUCCESS");
            } else {
                String packageCommand = "path";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        INSTALLER_INVOCATION, // detalinstall
                        "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 split_split_01.apk",
                        "/system/bin/cmd package install-commit 2",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                        "/system/bin/run-as com.example.simpleapp cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so",
                        "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so");
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split = TestUtils.resolveWorkspacePath(BASE + "apks/split.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.toString(),
            split.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        Path added = TestUtils.resolveWorkspacePath(BASE + "apks/split2.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    base.toString(),
                    split.toString(),
                    added.toString(),
                    "--installers-path=" + installersPath.toString()
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
            } else if (device.getApi() < 28) {
                String packageCommand = "dump";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        AdbInstallerTest.RM_DIR,
                        AdbInstallerTest.MK_DIR,
                        AdbInstallerTest.CHMOD,
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
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
            } else {
                String packageCommand = "path";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        "cmd package install-create -r -t -S ${size:com.example.simpleapp}",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split -",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_02.apk} 2 2_split_ -",
                        "cmd package install-commit 2",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                        "/system/bin/run-as com.example.simpleapp cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so",
                        "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so");
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split1 = TestUtils.resolveWorkspacePath(BASE + "apks/split.apk");
        Path split2 = TestUtils.resolveWorkspacePath(BASE + "apks/split2.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.toString(),
            split1.toString(),
            split2.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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
                    base.toString(),
                    split1.toString(),
                    "--installers-path=" + installersPath.toString()
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
            } else if (device.getApi() < 28) {
                String packageCommand = "dump";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        AdbInstallerTest.RM_DIR,
                        AdbInstallerTest.MK_DIR,
                        AdbInstallerTest.CHMOD,
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
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
            } else {
                String packageCommand = "path";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        "cmd package install-create -r -t -S ${size:com.example.simpleapp}",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 0_simple -",
                        "cmd package install-write -S ${size:com.example.simpleapp:split_split_01.apk} 2 1_split -",
                        "cmd package install-commit 2",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                        "/system/bin/run-as com.example.simpleapp cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so",
                        "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so");
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+new_asset.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
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
        } else if (device.getApi() < 28) {
            String packageCommand = "dump";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    INSTALLER_INVOCATION, // deltainstall
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL_UPLOAD",
                    "DELTAINSTALL_INSTALL",
                    "DELTAINSTALL:SUCCESS");
        } else {
            String packageCommand = "path";
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    INSTALLER_INVOCATION, // deltainstall
                    "/system/bin/cmd package install-create -t -r",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "/system/bin/cmd package install-commit 2",
                    "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                    "/system/bin/run-as com.example.simpleapp cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.simpleapp")
                            + "coroutine_debugger_agent.so",
                    "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                            + Sites.appCodeCache("com.example.simpleapp")
                            + "coroutine_debugger_agent.so");
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
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path base = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path split = TestUtils.resolveWorkspacePath(BASE + "apks/split.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            base.toString(),
            split.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        Path newBase = TestUtils.resolveWorkspacePath(BASE + "apks/simple+new_asset.apk");
        args =
                new String[] {
                    "install",
                    "com.example.simpleapp",
                    newBase.toString(),
                    split.toString(),
                    "--installers-path=" + installersPath.toString()
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
            } else if (device.getApi() < 28) {
                String packageCommand = "dump";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        AdbInstallerTest.RM_DIR,
                        AdbInstallerTest.MK_DIR,
                        AdbInstallerTest.CHMOD,
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        INSTALLER_INVOCATION, // deltainstal
                        "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 base.apk",
                        "/system/bin/cmd package install-commit 2");
                assertMetrics(
                        runner.getMetrics(),
                        "DELTAINSTALL_UPLOAD",
                        "DELTAINSTALL_INSTALL",
                        "DELTAINSTALL:SUCCESS");
            } else {
                String packageCommand = "path";
                assertHistory(
                        device,
                        "getprop",
                        INSTALLER_INVOCATION, // dump com.example.simpleapp
                        "/system/bin/run-as com.example.simpleapp id -u",
                        "id -u",
                        String.format(
                                "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                        INSTALLER_INVOCATION, // deltainstal
                        "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                        "cmd package install-write -S ${size:com.example.simpleapp:base.apk} 2 base.apk",
                        "/system/bin/cmd package install-commit 2",
                        "/data/local/tmp/.studio/bin/installer -version=$VERSION",
                        "/system/bin/run-as com.example.simpleapp cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so",
                        "cp -F /data/local/tmp/.studio/tmp/$VERSION/coroutine_debugger_agent.so "
                                + Sites.appCodeCache("com.example.simpleapp")
                                + "coroutine_debugger_agent.so");
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
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        // Install the base apk:
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--installers-path=" + installersPath.toString()
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
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--installers-path=" + installersPath.toString()
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

        file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code.apk");
        args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
                };

        // We create a empty database. This simulate an installed APK not found in the database.
        dexDB = new SqlApkFileDatabase(File.createTempFile("test_db_empty", ".bin"), null);
        device.getShell().clearHistory();
        runner = new DeployerRunner(cacheDb, dexDB, service);
        retcode = runner.run(args, logger);
        if (device.supportsJvmti()) {
            // TODO WIP. This is WRONG, this is where optimistic swap should fail because of
            // OverlayID mismatch.
            if (device.getApi() < 30) {
                assertEquals(DeployerException.Error.REMOTE_APK_NOT_FOUND_IN_DB.ordinal(), retcode);
            }
        } else {
            assertEquals(DeployerException.Error.CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
        }
        if (device.getApi() < 26) {
            assertTrue(runner.getMetrics().isEmpty());
            assertHistory(device, "getprop");
        } else if (device.getApi() < 30) {
            String packageCommand = device.getApi() < 28 ? "dump" : "path";
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Success",
                    "COMPARE:Failed");
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    String.format(
                            "/system/bin/cmd package %s com.example.simpleapp", packageCommand),
                    INSTALLER_INVOCATION, // deltapreinstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "cmd package install-abandon 2");
        }
    }

    @Test
    public void testDump() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        String packageName = "com.example.simpleapp";
        assertTrue(device.getApps().isEmpty());
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);

        AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            Thread.sleep(100);
        }
        IDevice iDevice = bridge.getDevices()[0];
        AdbClient adb = new AdbClient(iDevice, logger);
        ArrayList<DeployMetric> metrics = new ArrayList<>();
        Installer installer = new AdbInstaller(installersPath.toString(), adb, metrics, logger);

        // Make sure we have true negative.
        Deploy.DumpResponse response = installer.dump(Collections.singletonList(packageName));
        assertEquals(Deploy.DumpResponse.Status.ERROR_PACKAGE_NOT_FOUND, response.getStatus());
        AndroidDebugBridge.terminate();

        // Install our target APK.
        {
            Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
            String[] args = {
                "install",
                packageName,
                file.toString(),
                "--installers-path=" + installersPath.toString()
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

    @Test
    public void testSwapWithAppNotRunning() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
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

        file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code.apk");
        args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
                };
        int retcode = runner.run(args, logger);

        if (device.getApi() < 26) {
            assertEquals(CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
            assertMetrics(runner.getMetrics()); // No metrics
        } else if (device.getApi() < 28) {
            assertEquals(DUMP_UNKNOWN_PROCESS.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package "
                            + (device.getApi() < 28 ? "dump" : "path")
                            + " com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltapreinstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "cmd package install-abandon 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Success",
                    "COMPARE:Success",
                    "SWAP:Failed");
        } else if (device.getApi() < 30) {
            assertEquals(DUMP_UNKNOWN_PROCESS.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package "
                            + (device.getApi() < 28 ? "dump" : "path")
                            + " com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltapreinstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "cmd package install-abandon 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Success",
                    "COMPARE:Success",
                    "SWAP:Failed");
        }

        // TODO: API 30 tests.
    }

    @Test
    @ApiLevel.InRange(max = 29)
    public void testBasicSwap() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");

        String cmd =
                "am start -n com.example.simpleapp/.MainActivity -a android.intent.action.MAIN";
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        device.getShell().clearHistory();

        file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code.apk");
        args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
                };
        int retcode = runner.run(args, logger);
        String logcat = getLogcatContent(device);

        if (device.getApi() < 26) {
            assertEquals(CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
            assertMetrics(runner.getMetrics()); // No metrics
        } else if (device.getApi() < 28) {
            assertEquals(NO_ERROR.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package dump com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltapreinstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    INSTALLER_INVOCATION, // swap
                    "/system/bin/run-as com.example.simpleapp cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/cmd activity attach-agent 10001 /data/data/com.example.simpleapp/code_cache/.studio/agent.so=irsocket-0",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Success",
                    "COMPARE:Success",
                    "SWAP:Success");
            assertRetransformed(
                    logcat, "android.app.ActivityThread", "dalvik.system.DexPathList$Element");
        } else if (device.getApi() < 30) {
            assertEquals(NO_ERROR.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package path com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltapreinstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    INSTALLER_INVOCATION, // swap
                    "/system/bin/run-as com.example.simpleapp cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/cmd activity attach-agent 10001 /data/data/com.example.simpleapp/code_cache/.studio/agent.so=irsocket-0",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Success",
                    "COMPARE:Success",
                    "SWAP:Success");
            assertRetransformed(
                    logcat, "android.app.ActivityThread", "dalvik.system.DexPathList$Element");
        } else {
            assertRetransformed(
                    logcat,
                    "android.app.ActivityThread",
                    "dalvik.system.DexPathList$Element",
                    "dalvik.system.DexPathList",
                    "android.app.ResourcesManager");
        }

        TestUtils.eventually(
                () -> {
                    try {
                        if (dexDB.dump().isEmpty()) {
                            Assert.fail();
                        }
                    } catch (DeployerException e) {
                        Assert.fail();
                    }
                },
                Duration.ofSeconds(5));

        assertFalse(dexDB.hasDuplicates());
    }

    @Test
    @ApiLevel.InRange(min = 30)
    public void testAgentTransformCache() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        Path oldApk = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path newApk = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code.apk");

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);

        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            oldApk.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", oldApk);

        String cmd =
                "am start -n com.example.simpleapp/.MainActivity -a android.intent.action.MAIN";
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        device.getShell().clearHistory();

        // This swap is just to put the agent in place.
        args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    newApk.toString(),
                    "--installers-path=" + installersPath.toString()
                };

        assertEquals(0, runner.run(args, logger));

        // Stop the app so we can restart it and attach a startup agent.
        device.stopApp("com.example.simpleapp");
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        // The second time we restart it, it should use cached instrumentation.
        device.stopApp("com.example.simpleapp");
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        // The third time we restart it, it should use cached instrumentation.
        device.stopApp("com.example.simpleapp");
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        String logcat = getLogcatContent(device);

        // We currently determine if the agent is using transform caching by inspecting JVMTI
        // invocations. The agent uses RetransformClasses when cached classes are not available, and
        // uses RedefineClasses when cached classes are present.

        // Should only have one retransform of each of these classes.
        assertRetransformed(
                logcat,
                "java.lang.Thread",
                "dalvik.system.DexPathList",
                "android.app.LoadedApk",
                "android.app.ResourcesManager");
        // Should have redefined each of these classes twice, once per restart.
        assertRedefined(
                logcat,
                "java.lang.Thread",
                "dalvik.system.DexPathList",
                "android.app.LoadedApk",
                "android.app.ResourcesManager",
                "java.lang.Thread",
                "dalvik.system.DexPathList",
                "android.app.LoadedApk",
                "android.app.ResourcesManager");
    }

    @Test
    @ApiLevel.InRange(min = 31)
    public void testHiddenAPISuppression() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        Path oldApk = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path newApk = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code.apk");

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);

        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            oldApk.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", oldApk);

        String cmd =
                "am start -n com.example.simpleapp/.MainActivity -a android.intent.action.MAIN";
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        device.getShell().clearHistory();

        // This swap is just to put the agent in place.
        args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    newApk.toString(),
                    "--installers-path=" + installersPath.toString()
                };

        assertEquals(0, runner.run(args, logger));

        // Stop the app so we can restart it and attach a startup agent.
        device.stopApp("com.example.simpleapp");
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        String logcat = getLogcatContent(device);
        assertHiddenAPISilencer(logcat, "Suppressing", "Restoring");
    }

    @Test
    public void testCodeSwapThatFails() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");

        String cmd =
                "am start -n com.example.simpleapp/.MainActivity -a android.intent.action.MAIN";
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        device.getShell().clearHistory();

        file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code+res.apk");
        args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
                };
        int retcode = runner.run(args, logger);

        if (device.getApi() < 26) {
            assertEquals(CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
            assertMetrics(runner.getMetrics()); // No metrics
        } else if (device.getApi() < 28) {
            assertEquals(CANNOT_SWAP_RESOURCE.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package "
                            + (device.getApi() < 28 ? "dump" : "path")
                            + " com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltainstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "cmd package install-abandon 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Failed");
        } else if (device.getApi() < 30) {
            assertEquals(CANNOT_SWAP_RESOURCE.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package "
                            + (device.getApi() < 28 ? "dump" : "path")
                            + " com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltainstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    "cmd package install-abandon 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Failed");
        } else {
            // TODO: Pipeline 2.0 tests.
        }
    }

    @Test
    @ApiLevel.InRange(max = 29)
    public void testResourceAndCodeSwap() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple.apk");
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();

        String[] args = {
            "install",
            "com.example.simpleapp",
            file.toString(),
            "--force-full-install",
            "--installers-path=" + installersPath.toString()
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", file);
        assertMetrics(
                runner.getMetrics(),
                "DELTAINSTALL:DISABLED",
                "INSTALL:OK",
                "DDMLIB_UPLOAD",
                "DDMLIB_INSTALL");

        String cmd =
                "am start -n com.example.simpleapp/.MainActivity -a android.intent.action.MAIN";
        assertEquals(0, device.executeScript(cmd, new byte[] {}).value);

        device.getShell().clearHistory();

        file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code+res.apk");
        args =
                new String[] {
                    "fullswap",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
                };
        int retcode = runner.run(args, logger);
        String logcat = getLogcatContent(device);

        if (device.getApi() < 26) {
            assertEquals(CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
            assertMetrics(runner.getMetrics()); // No metrics
        } else if (device.getApi() < 28) {
            assertEquals(NO_ERROR.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR,
                    AdbInstallerTest.CHMOD,
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package dump com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltapreinstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    INSTALLER_INVOCATION, // swap
                    "/system/bin/run-as com.example.simpleapp cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/cmd activity attach-agent 10001 /data/data/com.example.simpleapp/code_cache/.studio/agent.so=irsocket-0",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Success",
                    "COMPARE:Success",
                    "SWAP:Success");
            assertRetransformed(
                    logcat, "android.app.ActivityThread", "dalvik.system.DexPathList$Element");
        } else if (device.getApi() < 30) {
            assertEquals(NO_ERROR.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION, // dump com.example.simpleapp
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package "
                            + (device.getApi() < 28 ? "dump" : "path")
                            + " com.example.simpleapp",
                    INSTALLER_INVOCATION, // deltapreinstall
                    "/system/bin/cmd package install-create -t -r --dont-kill",
                    "cmd package install-write -S ${size:com.example.simpleapp} 2 base.apk",
                    INSTALLER_INVOCATION, // swap
                    "/system/bin/run-as com.example.simpleapp cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "cp -rF /data/local/tmp/.studio/tmp/$VERSION/ "
                            + Sites.appStudio("com.example.simpleapp"),
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "cp -n /data/local/tmp/.studio/tmp/$VERSION/install_server /data/data/com.example.simpleapp/code_cache/install_server-$VERSION",
                    "/system/bin/run-as com.example.simpleapp /data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/data/data/com.example.simpleapp/code_cache/install_server-$VERSION com.example.simpleapp",
                    "/system/bin/cmd activity attach-agent 10001 /data/data/com.example.simpleapp/code_cache/.studio/agent.so=irsocket-0",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAPREINSTALL_WRITE",
                    ":Success",
                    ":Success",
                    ":Success",
                    "PARSE_PATHS:Success",
                    "DUMP:Success",
                    "DIFF:Success",
                    "PREINSTALL:Success",
                    "VERIFY:Success",
                    "COMPARE:Success",
                    "SWAP:Success");
            assertRetransformed(
                    logcat, "android.app.ActivityThread", "dalvik.system.DexPathList$Element");
        } else {
            assertRetransformed(
                    logcat,
                    "android.app.ActivityThread",
                    "dalvik.system.DexPathList$Element",
                    "dalvik.system.DexPathList",
                    "android.app.ResourcesManager");
        }
    }

    @Test
    public void checkFailingMkDir() throws IOException {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        DeployerRunner runner = new DeployerRunner(cacheDb, dexDB, service);
        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        Path file = TestUtils.resolveWorkspacePath(BASE + "apks/simple+code.apk");

        // Set device to fail on any attempt to mkdir
        device.getShell().addCommand(new FailingMkdir());
        String[] args =
                new String[] {
                    "codeswap",
                    "com.example.simpleapp",
                    file.toString(),
                    "--installers-path=" + installersPath.toString()
                };
        int retcode = runner.run(args, logger);

        if (device.getApi() < 26) {
            assertEquals(CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
            assertMetrics(runner.getMetrics()); // No metrics
        } else if (device.getApi() < 30) {
            assertEquals(DUMP_FAILED.ordinal(), retcode);
            assertHistory(
                    device,
                    "getprop",
                    INSTALLER_INVOCATION,
                    AdbInstallerTest.RM_DIR,
                    AdbInstallerTest.MK_DIR);
        } else {
            // TODO: Add R+ tests.
        }
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

    private static void assertRedefined(String logcat, String... classes) {
        assertPrefixedInLogcat(logcat, "JVMTI::RedefineClasses:", classes);
    }

    private static void assertRetransformed(String logcat, String... classes) {
        assertPrefixedInLogcat(logcat, "JVMTI::RetransformClasses:", classes);
    }

    private static void assertHiddenAPISilencer(String logcat, String... classes) {
        assertPrefixedInLogcat(logcat, "JVMTI::HiddenAPIWarning:", classes);
    }

    private static void assertPrefixedInLogcat(String logcat, String prefix, String... expected) {
        String[] actual = logcat.split("\n");
        int expectedIndex = 0;
        for (int i = 0; i < actual.length; ++i) {
            int idx = actual[i].indexOf(prefix);
            if (idx == -1) {
                continue;
            }

            if (expectedIndex == expected.length) {
                fail("Unexpected logcat line: " + actual[i]);
            }

            String trimmed = actual[i].substring(idx);
            assertEquals("Unexpected logcat line", prefix + expected[expectedIndex], trimmed);
            ++expectedIndex;
        }
        if (expectedIndex != expected.length) {
            fail("Missing logcat line: " + prefix + expected[expectedIndex]);
        }
    }
}
