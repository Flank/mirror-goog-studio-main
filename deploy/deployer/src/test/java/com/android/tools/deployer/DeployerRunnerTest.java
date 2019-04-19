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
import static org.junit.Assert.assertTrue;

import com.android.testutils.AssumeUtil;
import com.android.testutils.TestUtils;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deploy.protobuf.ByteString;
import com.android.tools.deploy.protobuf.CodedInputStream;
import com.android.tools.deploy.protobuf.CodedOutputStream;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.tools.deployer.devices.FakeDeviceLibrary.DeviceId;
import com.android.tools.deployer.devices.shell.Arguments;
import com.android.tools.deployer.devices.shell.ShellCommand;
import com.android.tools.deployer.devices.shell.interpreter.ShellContext;
import com.android.utils.FileUtils;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class DeployerRunnerTest extends FakeAdbTestBase {

    private UIService service;

    @Parameterized.Parameters(name = "{0}")
    public static DeviceId[] getDevices() {
        return DeviceId.values();
    }

    public DeployerRunnerTest(DeviceId id) {
        super(new FakeDeviceLibrary().build(id));
    }

    @Before
    public void setUp() {
        this.service = Mockito.mock(UIService.class);
    }

    @After
    public void tearDown() {
        Mockito.verifyNoMoreInteractions(service);
    }

    @Test
    public void testFullInstallSuccessful() throws Exception {
        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"));
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        String[] args = {
            "install", "com.example.helloworld", file.getAbsolutePath(), "--force-full-install"
        };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());
        assertInstalled("com.example.helloworld", "base.apk", file);
        assertMetrics(runner.getMetrics(), "DELTAINSTALL:DISABLED", "INSTALL:OK");
        assertFalse(device.hasFile("/data/local/tmp/sample.apk"));
    }

    @Test
    public void testAttemptDeltaInstallWithoutPreviousInstallation() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        device.setShellBridge(getShell());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"));
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        File installersPath = prepareInstaller();
        String[] args = {
            "install",
            "com.example.helloworld",
            file.getAbsolutePath(),
            "--installers-path=" + installersPath.getAbsolutePath()
        };
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());

        assertInstalled("com.example.helloworld", "base.apk", file);

        if (device.getApi() < 21) {
            assertMetrics(runner.getMetrics(), "DELTAINSTALL:API_NOT_SUPPORTED", "INSTALL:OK");
            assertHistory(
                    device,
                    "getprop",
                    "pm install -r -t \"/data/local/tmp/sample.apk\"",
                    "rm \"/data/local/tmp/sample.apk\"");
        } else if (device.getApi() < 24) {
            assertMetrics(runner.getMetrics(), "DELTAINSTALL:API_NOT_SUPPORTED", "INSTALL:OK");
            assertHistory(
                    device,
                    "getprop",
                    "pm install-create -r -t -S 5047",
                    "pm install-write -S 5047 1 0_sample -",
                    "pm install-commit 1");
        } else {
            assertMetrics(runner.getMetrics(), "DELTAINSTALL:DUMP_UNKNOWN_PACKAGE", "INSTALL:OK");
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.helloworld",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.helloworld",
                    "/system/bin/run-as com.example.helloworld id -u",
                    "/system/bin/cmd package path com.example.helloworld",
                    "/system/bin/pm path com.example.helloworld", // TODO: we should not always attempt both paths
                    "cmd package install-create -r -t -S 5047",
                    "cmd package install-write -S 5047 1 0_sample -",
                    "cmd package install-commit 1");
        }
    }

    private static void assertHistory(FakeDevice device, String... expected) {
        List<String> actual = device.getShell().getHistory();
        assertArrayEquals(expected, actual.toArray(new String[] {}));
    }

    @Test
    public void testSkipInstall() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        device.setShellBridge(getShell());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"));
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File installersPath = prepareInstaller();

        String[] args = {
            "install", "com.example.simpleapp", file.getAbsolutePath(), "--force-full-install"
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", "base.apk", file);
        assertMetrics(runner.getMetrics(), "DELTAINSTALL:DISABLED", "INSTALL:OK");
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

        assertInstalled("com.example.simpleapp", "base.apk", file);

        if (device.getApi() < 24) {
            assertMetrics(runner.getMetrics(), "DELTAINSTALL:API_NOT_SUPPORTED", "INSTALL:OK");
        } else {
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.simpleapp",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package path com.example.simpleapp",
                    "am force-stop com.example.simpleapp");
            assertMetrics(runner.getMetrics(), "INSTALL:SKIPPED_INSTALL");
        }
    }

    @Test
    public void testDeltaInstall() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        device.setShellBridge(getShell());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"));
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "apks/simple.apk");
        File installersPath = prepareInstaller();

        String[] args = {
            "install", "com.example.simpleapp", file.getAbsolutePath(), "--force-full-install"
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", "base.apk", file);
        assertMetrics(runner.getMetrics(), "DELTAINSTALL:DISABLED", "INSTALL:OK");
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

        assertInstalled("com.example.simpleapp", "base.apk", file);

        if (device.getApi() < 24) {
            assertMetrics(runner.getMetrics(), "DELTAINSTALL:API_NOT_SUPPORTED", "INSTALL:OK");
        } else {
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.simpleapp",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package path com.example.simpleapp",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " deltainstall",
                    "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                    "cmd package install-write -S 12789 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(runner.getMetrics(), "DELTAINSTALL:SUCCESS");
        }
    }

    @Test
    public void testInstallOldVersion() throws Exception {
        AssumeUtil.assumeNotWindows(); // This test runs the installer on the host

        assertTrue(device.getApps().isEmpty());
        device.setShellBridge(getShell());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"));
        DeployerRunner runner = new DeployerRunner(db, service);
        File v2 = TestUtils.getWorkspaceFile(BASE + "apks/simple+ver.apk");
        File installersPath = prepareInstaller();

        String[] args = {
            "install", "com.example.simpleapp", v2.getAbsolutePath(), "--force-full-install"
        };

        assertEquals(0, runner.run(args, logger));
        assertInstalled("com.example.simpleapp", "base.apk", v2);
        assertMetrics(runner.getMetrics(), "DELTAINSTALL:DISABLED", "INSTALL:OK");

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
        assertInstalled("com.example.simpleapp", "base.apk", v2);

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
                    "pm install-create -r -t -S 12789", // TODO: passing size on create?
                    "pm install-write -S 12789 2 0_simple -",
                    "pm install-commit 2");
            assertMetrics(
                    runner.getMetrics(),
                    "DELTAINSTALL:API_NOT_SUPPORTED",
                    "INSTALL:INSTALL_FAILED_VERSION_DOWNGRADE");
        } else {
            assertHistory(
                    device,
                    "getprop",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.simpleapp",
                    "mkdir -p /data/local/tmp/.studio/bin",
                    "chmod +x /data/local/tmp/.studio/bin/installer",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " dump com.example.simpleapp",
                    "/system/bin/run-as com.example.simpleapp id -u",
                    "id -u",
                    "/system/bin/cmd package path com.example.simpleapp",
                    "/data/local/tmp/.studio/bin/installer -version="
                            + Version.hash()
                            + " deltainstall",
                    "/system/bin/cmd package install-create -t -r -p com.example.simpleapp",
                    "cmd package install-write -S 12789 2 base.apk",
                    "/system/bin/cmd package install-commit 2");
            assertMetrics(
                    runner.getMetrics(), "DELTAINSTALL:ERROR.INSTALL_FAILED_VERSION_DOWNGRADE");
        }
        Mockito.verify(service, Mockito.times(1)).prompt(ArgumentMatchers.anyString());
    }

    public File prepareInstaller() throws IOException {
        File root = TestUtils.getWorkspaceRoot();
        String testInstaller = "tools/base/deploy/installer/android-installer/test-installer";
        File installer = new File(root, testInstaller);
        if (!installer.exists()) {
            // Running from IJ
            File devRoot = new File(root, "bazel-genfiles/");
            installer = new File(devRoot, testInstaller);
        }
        File installers = Files.createTempDirectory("installers").toFile();
        File x86 = new File(installers, "x86");
        x86.mkdirs();
        FileUtils.copyFile(installer, new File(x86, "installer"));
        return installers;
    }

    public File getShell() {
        File root = TestUtils.getWorkspaceRoot();
        String path = "tools/base/deploy/installer/bash_bridge";
        File file = new File(root, path);
        if (!file.exists()) {
            // Running from IJ
            file = new File(root, "bazel-bin/" + path);
        }
        return file;
    }

    public void assertInstalled(String packageName, String fileName, File file) throws IOException {
        assertArrayEquals(new String[] {packageName}, device.getApps().toArray());
        byte[] expected = Files.readAllBytes(file.toPath());
        assertArrayEquals(
                expected, device.readFile(device.getAppPath(packageName) + "/" + fileName));
    }

    private void assertMetrics(ArrayList<DeployMetric> metrics, String... expected) {
        String[] actual =
                metrics.stream().map(m -> m.getName() + ":" + m.getStatus()).toArray(String[]::new);
        assertArrayEquals(actual, expected);
    }

    @Test
    public void testBasicSwap() throws Exception {
        // Install the base apk:
        assertTrue(device.getApps().isEmpty());
        ApkFileDatabase db = new SqlApkFileDatabase(File.createTempFile("test_db", ".bin"));
        DeployerRunner runner = new DeployerRunner(db, service);
        File file = TestUtils.getWorkspaceFile(BASE + "signed_app/base.apk");
        String[] args = {"install", "com.android.test.uibench", file.getAbsolutePath()};
        int retcode = runner.run(args, logger);
        assertEquals(0, retcode);
        assertEquals(1, device.getApps().size());
        assertInstalled("com.android.test.uibench", "base.apk", file);

        File installers = Files.createTempDirectory("installers").toFile();
        FileUtils.writeToFile(new File(installers, "x86/installer"), "INSTALLER");

        device.getShell().addCommand(new InstallerCommand());

        file = TestUtils.getWorkspaceFile(BASE + "signed_app/base.apk");
        args =
                new String[] {
                    "codeswap",
                    "com.android.test.uibench",
                    file.getAbsolutePath(),
                    "--installers-path=" + installers.getAbsolutePath()
                };
        retcode = runner.run(args, logger);

        if (device.supportsJvmti()) {
            assertEquals(0, retcode);
        } else {
            assertEquals(DeployerException.Error.CANNOT_SWAP_BEFORE_API_26.ordinal(), retcode);
        }
    }

    private class InstallerCommand extends ShellCommand {
        @Override
        public int execute(
                ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
                throws IOException {
            Arguments arguments = new Arguments(args);
            String version = arguments.nextOption();
            // We assume the version is fine
            String action = arguments.nextArgument();
            Deploy.InstallerResponse.Builder builder = Deploy.InstallerResponse.newBuilder();
            switch (action) {
                case "dump":
                    {
                        String pkg = arguments.nextArgument();
                        Deploy.DumpResponse.Builder dump = Deploy.DumpResponse.newBuilder();
                        dump.setStatus(Deploy.DumpResponse.Status.OK);
                        byte[] block =
                                Files.readAllBytes(
                                        TestUtils.getWorkspaceFile(
                                                        BASE + "/signed_app/base.apk.remoteblock")
                                                .toPath());
                        byte[] cd =
                                Files.readAllBytes(
                                        TestUtils.getWorkspaceFile(
                                                        BASE + "/signed_app/base.apk.remotecd")
                                                .toPath());

                        Deploy.PackageDump packageDump =
                                Deploy.PackageDump.newBuilder()
                                        .setName(pkg)
                                        .addProcesses(42)
                                        .addApks(
                                                Deploy.ApkDump.newBuilder()
                                                        .setName("base.apk")
                                                        .setCd(ByteString.copyFrom(cd))
                                                        .setSignature(ByteString.copyFrom(block))
                                                        .build())
                                        .build();
                        dump.addPackages(packageDump);
                        builder.setDumpResponse(dump);
                        break;
                    }
                case "deltapreinstall":
                    {
                        byte[] bytes = new byte[4];
                        ByteStreams.readFully(stdin, bytes);
                        int size = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        bytes = new byte[size];
                        ByteStreams.readFully(stdin, bytes);
                        CodedInputStream cis = CodedInputStream.newInstance(bytes);
                        Deploy.DeltaPreinstallRequest request =
                                Deploy.DeltaPreinstallRequest.parser().parseFrom(cis);

                        Deploy.DeltaPreinstallResponse.Builder preinstall =
                                Deploy.DeltaPreinstallResponse.newBuilder();
                        preinstall.setStatus(Deploy.DeltaPreinstallResponse.Status.OK);
                        preinstall.setSessionId("1234");
                        builder.setDeltapreinstallResponse(preinstall);

                        break;
                    }
                case "swap":
                    {
                        byte[] bytes = new byte[4];
                        ByteStreams.readFully(stdin, bytes);
                        int size = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        bytes = new byte[size];
                        ByteStreams.readFully(stdin, bytes);
                        CodedInputStream cis = CodedInputStream.newInstance(bytes);
                        Deploy.SwapRequest request = Deploy.SwapRequest.parser().parseFrom(cis);

                        Deploy.SwapResponse.Builder swap = Deploy.SwapResponse.newBuilder();
                        swap.setStatus(Deploy.SwapResponse.Status.OK);
                        builder.setSwapResponse(swap);
                        break;
                    }
            }

            Deploy.InstallerResponse response = builder.build();
            int size = response.getSerializedSize();
            byte[] bytes = new byte[Integer.BYTES + size];
            ByteBuffer sizeWritter = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            sizeWritter.putInt(size);
            CodedOutputStream cos = CodedOutputStream.newInstance(bytes, Integer.BYTES, size);
            response.writeTo(cos);
            stdout.write(bytes);
            return 0;
        }

        @Override
        public String getExecutable() {
            return "/data/local/tmp/.studio/bin/installer";
        }
    }
}
