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

import com.android.testutils.TestUtils;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.ApkEntry;
import com.google.protobuf.ByteString;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;

public class ApkDumperTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    @Test
    public void testWithSignature() throws Exception {
        File cd = TestUtils.getWorkspaceFile(BASE + "signed_app/base.apk.remotecd");
        File sig = TestUtils.getWorkspaceFile(BASE + "signed_app/base.apk.remoteblock");

        Deploy.ApkDump.Builder dump =
                Deploy.ApkDump.newBuilder()
                        .setCd(ByteString.copyFrom(Files.readAllBytes(cd.toPath())))
                        .setSignature(ByteString.copyFrom(Files.readAllBytes(sig.toPath())));
        Deploy.DumpResponse response = Deploy.DumpResponse.newBuilder().addDumps(dump).build();

        Installer installer = Mockito.mock(Installer.class);
        Mockito.when(installer.dump("package")).thenReturn(response);

        List<ApkEntry> files = new ApkDumper(installer).dump("package");

        String apk = "b236acae47f2b2163e9617021c4e1adc7a0c197b";
        assertEquals(277, files.size());
        // Check a few files
        assertApkEntryEquals(apk, "res/drawable-nodpi-v4/frantic.jpg", 0x492381F1L, files.get(10));
        assertApkEntryEquals(
                apk,
                "res/drawable-xxhdpi-v4/abc_textfield_search_default_mtrl_alpha.9.png",
                0x4034A6D4L,
                files.get(20));
        assertApkEntryEquals(apk, "resources.arsc", 0xFCD1856L, files.get(30));
    }

    @Test
    public void testApkArchiveApkNonV2SignedDumpdMatchDigest() throws Exception {
        File cd = TestUtils.getWorkspaceFile(BASE + "nonsigned_app/base.apk.remotecd");

        Deploy.ApkDump.Builder dump =
                Deploy.ApkDump.newBuilder()
                        .setCd(ByteString.copyFrom(Files.readAllBytes(cd.toPath())));
        Deploy.DumpResponse response = Deploy.DumpResponse.newBuilder().addDumps(dump).build();

        Installer installer = Mockito.mock(Installer.class);
        Mockito.when(installer.dump("package")).thenReturn(response);

        List<ApkEntry> files = new ApkDumper(installer).dump("package");

        String apk = "e5c64a6b8f51198331aefcb7ff695e7faebbd80a";
        assertEquals(494, files.size());
        // Check a few files
        assertApkEntryEquals(
                apk,
                "res/drawable/abc_list_selector_background_transition_holo_light.xml",
                0x29EE1C29L,
                files.get(10));
        assertApkEntryEquals(
                apk,
                "res/drawable-xxxhdpi-v4/abc_ic_menu_cut_mtrl_alpha.png",
                0x566244DBL,
                files.get(20));
        assertApkEntryEquals(apk, "res/color/abc_tint_spinner.xml", 0xD7611BC4L, files.get(30));
    }
}
