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
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ApkParserTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    @Test
    public void testCentralDirectoryParse() throws IOException {
        File file = TestUtils.getWorkspaceFile(BASE + "base.apk.remotecd");
        byte[] fileContent = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
        HashMap<String, ZipUtils.ZipEntry> entries = ZipUtils.readZipEntries(fileContent);
        assertEquals(1007, entries.size());
        long manifestCrc = entries.get("AndroidManifest.xml").crc;
        assertEquals(0x5804A053, manifestCrc);
    }

    @Test
    public void testApkId() throws Exception {
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        List<ApkEntry> files = new ApkParser().parsePaths(ImmutableList.of(file.getPath()));
        String apk = "74eaa38f4d4d8619c7bb886289f84efe1fce7ce3";
        assertEquals(7, files.size());
        assertApkEntryEquals(apk, "META-INF/CERT.SF", 0x45E32198L, files.get(0));
        assertApkEntryEquals(apk, "AndroidManifest.xml", 0x7BF3141DL, files.get(1));
        assertApkEntryEquals(apk, "res/layout/main.xml", 0x45FED99AL, files.get(2));
        assertApkEntryEquals(apk, "META-INF/CERT.RSA", 0x4A3142E9L, files.get(3));
        assertApkEntryEquals(apk, "resources.arsc", 0x332A4621L, files.get(4));
        assertApkEntryEquals(apk, "classes.dex", 0x9AC9ECA1L, files.get(5));
        assertApkEntryEquals(apk, "META-INF/MANIFEST.MF", 0xA93C1C7FL, files.get(6));
    }

    @Test
    public void testApkArchiveV2Map() throws Exception {
        File file = TestUtils.getWorkspaceFile(BASE + "v2_signed.apk");
        List<ApkEntry> files = new ApkParser().parsePaths(ImmutableList.of(file.getAbsolutePath()));
        String apk = "6b1dc4b97ab0dbb66afc33868c700d6f665eeb13";
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

    @Test
    public void testApkArchiveApkDumpdMatchCrcs() throws Exception {
        File file = TestUtils.getWorkspaceFile(BASE + "signed_app/base.apk");
        List<ApkEntry> files = new ApkParser().parsePaths(ImmutableList.of(file.getPath()));

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
        File file = TestUtils.getWorkspaceFile(BASE + "nonsigned_app/base.apk");
        List<ApkEntry> files = new ApkParser().parsePaths(ImmutableList.of(file.getPath()));

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

    @Test
    public void testGetApkDetails() throws Exception {
        File file = TestUtils.getWorkspaceFile(BASE + "multiprocess.apk");
        List<ApkEntry> files = new ApkParser().parsePaths(ImmutableList.of(file.getAbsolutePath()));
        Apk apk = files.get(0).apk;
        assertEquals("base.apk", apk.name);
        assertEquals(5, apk.processes.size());

        Set<String> expected =
                ImmutableSet.of(
                        "com.test.multiprocess",
                        "alternate.default",
                        "com.test.multiprocess:service",
                        "com.test.multiprocess:private",
                        ".global");
        assertEquals(expected, new HashSet<>(apk.processes));
    }
}
