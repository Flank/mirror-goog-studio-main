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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.testutils.TestUtils;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deploy.protobuf.ByteString;
import com.android.tools.deploy.protobuf.CodedOutputStream;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.tools.deployer.devices.shell.Arguments;
import com.android.tools.deployer.devices.shell.ShellCommand;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApkInstallerTest extends FakeAdbTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static FakeDeviceLibrary.DeviceId[] getDevices() {
        return FakeDeviceLibrary.DeviceId.values();
    }

    public ApkInstallerTest(FakeDeviceLibrary.DeviceId id) {
        super(new FakeDeviceLibrary().build(id));
    }

    @Test
    public void testSkippedInstallation() throws Exception {
        AndroidDebugBridge bridge = initDebugBridge();
        IDevice iDevice = bridge.getDevices()[0];
        File installers = Files.createTempDirectory("installers").toFile();
        FileUtils.writeToFile(new File(installers, "x86/installer"), "INSTALLER");
        AdbClient client = new AdbClient(iDevice, logger);
        AdbInstaller adbInstaller =
                new AdbInstaller(installers.getAbsolutePath(), client, new ArrayList<>(), logger);
        ApkInstaller apkInstaller =
                new ApkInstaller(client, new EmptyUiService(), adbInstaller, logger);

        device.getShell().addCommand(new SkipInstallCommand());

        boolean installed =
                apkInstaller.install(
                        "com.android.test.uibench",
                        Lists.newArrayList(
                                TestUtils.getWorkspaceFile(BASE + "signed_app/base.apk")
                                        .getAbsolutePath()),
                        InstallOptions.builder().build(),
                        Deployer.InstallMode.DELTA,
                        Lists.newArrayList());


        List<String> history = device.getShell().getHistory();
        String lastCmd = history.get(history.size() - 1);
        if (device.getApi() >= 24) {
            Assert.assertFalse(installed);
            Assert.assertEquals("am force-stop com.android.test.uibench", lastCmd);
        } else if (device.getApi() >= 20) {
            Assert.assertTrue(lastCmd.startsWith("pm install-commit"));
        } else {
            Assert.assertTrue(history.stream().anyMatch(command -> command.contains("pm install")));
        }
    }

    private class SkipInstallCommand extends ShellCommand {
        @Override
        public boolean execute(
                FakeDevice device, String[] args, InputStream stdin, PrintStream stdout)
                throws IOException {
            Arguments arguments = new Arguments(args);
            String version = arguments.nextOption();
            // We assume the version is fine
            String action = arguments.nextArgument();
            Deploy.InstallerResponse.Builder builder = Deploy.InstallerResponse.newBuilder();
            Assert.assertEquals("dump", action);
            String pkg = arguments.nextArgument();
            Assert.assertEquals("com.android.test.uibench", pkg);
            Deploy.DumpResponse.Builder dump = Deploy.DumpResponse.newBuilder();
            dump.setStatus(Deploy.DumpResponse.Status.OK);
            byte[] block =
                    Files.readAllBytes(
                            TestUtils.getWorkspaceFile(BASE + "/signed_app/base.apk.remoteblock")
                                    .toPath());
            byte[] cd =
                    Files.readAllBytes(
                            TestUtils.getWorkspaceFile(BASE + "/signed_app/base.apk.remotecd")
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
            Deploy.InstallerResponse response = builder.build();
            int size = response.getSerializedSize();
            byte[] bytes = new byte[Integer.BYTES + size];
            ByteBuffer sizeWritter = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            sizeWritter.putInt(size);
            CodedOutputStream cos = CodedOutputStream.newInstance(bytes, Integer.BYTES, size);
            response.writeTo(cos);
            stdout.write(bytes);
            return true;
        }

        @Override
        public String getExecutable() {
            return "/data/local/tmp/.studio/bin/installer";
        }
    }
}
