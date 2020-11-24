/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.deployer.ApkTestUtils.assertApkEntryEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.testutils.TestUtils;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.idea.protobuf.ByteString;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.Mockito;

public class ApplicationDumperTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    @Test
    public void testWithSignature() throws Exception {
        Path cd = TestUtils.resolveWorkspacePath(BASE + "signed_app/base.apk.remotecd");
        Path sig = TestUtils.resolveWorkspacePath(BASE + "signed_app/base.apk.remoteblock");

        Deploy.ApkDump.Builder dump =
                Deploy.ApkDump.newBuilder()
                        .setCd(ByteString.copyFrom(Files.readAllBytes(cd)))
                        .setSignature(ByteString.copyFrom(Files.readAllBytes(sig)));

        Deploy.DumpResponse response =
                Deploy.DumpResponse.newBuilder()
                        .addPackages(
                                Deploy.PackageDump.newBuilder().setName("package").addApks(dump))
                        .build();

        Installer installer = Mockito.mock(Installer.class);
        Mockito.when(installer.dump(ImmutableList.of("package"))).thenReturn(response);

        Apk dumpApk = Apk.builder().setPackageName("package").addApkEntry("", 0).build();
        Map<String, ApkEntry> files =
                new ApplicationDumper(installer)
                        .dump(ImmutableList.of(dumpApk))
                        .apks
                        .get(0)
                        .apkEntries;

        String apk = "b236acae47f2b2163e9617021c4e1adc7a0c197b";
        assertEquals(277, files.size());
        // Check a few files
        assertApkEntryEquals(
                apk,
                "res/drawable-nodpi-v4/frantic.jpg",
                0x492381F1L,
                files.get("res/drawable-nodpi-v4/frantic.jpg"));
        assertApkEntryEquals(
                apk,
                "res/drawable-xxhdpi-v4/abc_textfield_search_default_mtrl_alpha.9.png",
                0x4034A6D4L,
                files.get("res/drawable-xxhdpi-v4/abc_textfield_search_default_mtrl_alpha.9.png"));
        assertApkEntryEquals(apk, "resources.arsc", 0xFCD1856L, files.get("resources.arsc"));
    }

    @Test
    public void testApkArchiveApkNonV2SignedDumpdMatchDigest() throws Exception {
        Path cd = TestUtils.resolveWorkspacePath(BASE + "nonsigned_app/base.apk.remotecd");

        Deploy.ApkDump.Builder dump =
                Deploy.ApkDump.newBuilder().setCd(ByteString.copyFrom(Files.readAllBytes(cd)));
        Deploy.DumpResponse response =
                Deploy.DumpResponse.newBuilder()
                        .addPackages(
                                Deploy.PackageDump.newBuilder().setName("package").addApks(dump))
                        .build();

        Installer installer = Mockito.mock(Installer.class);
        Mockito.when(installer.dump(ImmutableList.of("package"))).thenReturn(response);

        Apk dumpApk = Apk.builder().setPackageName("package").addApkEntry("", 0).build();
        Map<String, ApkEntry> files =
                new ApplicationDumper(installer)
                        .dump(ImmutableList.of(dumpApk))
                        .apks
                        .get(0)
                        .apkEntries;

        String apk = "e5c64a6b8f51198331aefcb7ff695e7faebbd80a";
        assertEquals(494, files.size());
        // Check a few files
        assertApkEntryEquals(
                apk,
                "res/drawable/abc_list_selector_background_transition_holo_light.xml",
                0x29EE1C29L,
                files.get("res/drawable/abc_list_selector_background_transition_holo_light.xml"));
        assertApkEntryEquals(
                apk,
                "res/drawable-xxxhdpi-v4/abc_ic_menu_cut_mtrl_alpha.png",
                0x566244DBL,
                files.get("res/drawable-xxxhdpi-v4/abc_ic_menu_cut_mtrl_alpha.png"));
        assertApkEntryEquals(
                apk,
                "res/color/abc_tint_spinner.xml",
                0xD7611BC4L,
                files.get("res/color/abc_tint_spinner.xml"));
    }

    @Test
    public void testProcessIds() throws Exception {
        Deploy.DumpResponse response =
                Deploy.DumpResponse.newBuilder()
                        .addPackages(
                                Deploy.PackageDump.newBuilder()
                                        .setName("target")
                                        .addProcesses(1)
                                        .addProcesses(2)
                                        .setArch(Deploy.Arch.ARCH_64_BIT))
                        .addPackages(
                                Deploy.PackageDump.newBuilder()
                                        .setName("instrument")
                                        .addProcesses(3)
                                        .addProcesses(4)
                                        .setArch(Deploy.Arch.ARCH_UNKNOWN))
                        .build();

        Installer installer = Mockito.mock(Installer.class);
        Mockito.when(installer.dump(ImmutableList.of("instrument", "target"))).thenReturn(response);

        Apk dumpApk =
                Apk.builder()
                        .setPackageName("instrument")
                        .setTargetPackages(ImmutableList.of("target"))
                        .addApkEntry("", 0)
                        .build();

        Map<String, List<Integer>> pids =
                new ApplicationDumper(installer).dump(ImmutableList.of(dumpApk)).packagePids;
        assertEquals(ImmutableList.of(1, 2), pids.get("target"));
        assertEquals(ImmutableList.of(3, 4), pids.get("instrument"));
    }

    @Test
    public void testMixedArch() throws Exception {
        Deploy.DumpResponse response =
                Deploy.DumpResponse.newBuilder()
                        .addPackages(
                                Deploy.PackageDump.newBuilder()
                                        .setName("target")
                                        .addProcesses(1)
                                        .addProcesses(2)
                                        .setArch(Deploy.Arch.ARCH_64_BIT))
                        .addPackages(
                                Deploy.PackageDump.newBuilder()
                                        .setName("instrument")
                                        .addProcesses(3)
                                        .addProcesses(4)
                                        .setArch(Deploy.Arch.ARCH_32_BIT))
                        .build();

        Installer installer = Mockito.mock(Installer.class);
        Mockito.when(installer.dump(ImmutableList.of("instrument", "target"))).thenReturn(response);

        Apk dumpApk =
                Apk.builder()
                        .setPackageName("instrument")
                        .setTargetPackages(ImmutableList.of("target"))
                        .addApkEntry("", 0)
                        .build();

        try {
            new ApplicationDumper(installer).dump(ImmutableList.of(dumpApk));
            fail("DeployerException should have been thrown.");
        } catch (DeployerException e) {
            assertTrue(
                    e.getMessage()
                            .contains("Application with process in both 32 and 64 bit mode."));
        }
    }
}
