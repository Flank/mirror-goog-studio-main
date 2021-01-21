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

import com.android.testutils.TestUtils;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;
import org.junit.Test;

public class ApkParserTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    @Test
    public void testCentralDirectoryParse() throws IOException {
        Path file = TestUtils.resolveWorkspacePath(BASE + "base.apk.remotecd");
        byte[] fileContent = Files.readAllBytes(file);
        Map<String, ZipUtils.ZipEntry> entries = ZipUtils.readZipEntries(fileContent);
        assertEquals(1007, entries.size());
        long manifestCrc = entries.get("AndroidManifest.xml").crc;
        assertEquals(0x5804A053, manifestCrc);
    }

    @Test
    public void testApkId() throws Exception {
        Path file = TestUtils.resolveWorkspacePath(BASE + "sample.apk");
        Map<String, ApkEntry> files =
                new ApkParser().parsePaths(ImmutableList.of(file.toString())).get(0).apkEntries;
        Map<String, Long> expected = new HashMap<>();
        expected.put("META-INF/CERT.SF", 0x45E32198L);
        expected.put("AndroidManifest.xml", 0x7BF3141DL);
        expected.put("res/layout/main.xml", 0x45FED99AL);
        expected.put("META-INF/CERT.RSA", 0x4A3142E9L);
        expected.put("resources.arsc", 0x332A4621L);
        expected.put("classes.dex", 0x9AC9ECA1L);
        expected.put("META-INF/MANIFEST.MF", 0xA93C1C7FL);

        String apk = "74eaa38f4d4d8619c7bb886289f84efe1fce7ce3";
        assertEquals(7, files.size());
        for (String fileName : expected.keySet()) {
            assertApkEntryEquals(apk, fileName, expected.get(fileName), files.get(fileName));
        }
    }

    @Test
    public void testApkArchiveV2Map() throws Exception {
        Path file = TestUtils.resolveWorkspacePath(BASE + "v2_signed.apk");
        Map<String, ApkEntry> files =
                new ApkParser().parsePaths(ImmutableList.of(file.toString())).get(0).apkEntries;
        Map<String, Long> expected = new HashMap<>();
        expected.put(
                "res/drawable/abc_list_selector_background_transition_holo_light.xml", 0x29EE1C29L);
        expected.put("res/drawable-xxxhdpi-v4/abc_ic_menu_cut_mtrl_alpha.png", 0x566244DBL);
        expected.put("res/color/abc_tint_spinner.xml", 0xD7611BC4L);
        String apk = "6b1dc4b97ab0dbb66afc33868c700d6f665eeb13";
        assertEquals(494, files.size());
        // Check a few files
        for (String fileName : expected.keySet()) {
            assertApkEntryEquals(apk, fileName, expected.get(fileName), files.get(fileName));
        }
    }

    @Test
    public void testApkArchiveApkDumpdMatchCrcs() throws Exception {
        Path file = TestUtils.resolveWorkspacePath(BASE + "signed_app/base.apk");
        Map<String, ApkEntry> files =
                new ApkParser().parsePaths(ImmutableList.of(file.toString())).get(0).apkEntries;
        Map<String, Long> expected = new HashMap<>();
        expected.put("res/drawable-nodpi-v4/frantic.jpg", 0x492381F1L);
        expected.put(
                "res/drawable-xxhdpi-v4/abc_textfield_search_default_mtrl_alpha.9.png",
                0x4034A6D4L);
        expected.put("resources.arsc", 0xFCD1856L);
        String apk = "b236acae47f2b2163e9617021c4e1adc7a0c197b";
        assertEquals(277, files.size());
        // Check a few files
        for (String fileName : expected.keySet()) {
            assertApkEntryEquals(apk, fileName, expected.get(fileName), files.get(fileName));
        }
    }

    @Test
    public void testApkArchiveApkNonV2SignedDumpdMatchDigest() throws Exception {
        Path file = TestUtils.resolveWorkspacePath(BASE + "nonsigned_app/base.apk");
        Map<String, ApkEntry> files =
                new ApkParser().parsePaths(ImmutableList.of(file.toString())).get(0).apkEntries;
        Map<String, Long> expected = new HashMap<>();
        expected.put(
                "res/drawable/abc_list_selector_background_transition_holo_light.xml", 0x29EE1C29L);
        expected.put("res/drawable-xxxhdpi-v4/abc_ic_menu_cut_mtrl_alpha.png", 0x566244DBL);
        expected.put("res/color/abc_tint_spinner.xml", 0xD7611BC4L);
        String apk = "e5c64a6b8f51198331aefcb7ff695e7faebbd80a";
        assertEquals(494, files.size());
        // Check a few files
        for (String fileName : expected.keySet()) {
            assertApkEntryEquals(apk, fileName, expected.get(fileName), files.get(fileName));
        }
    }

    @Test
    public void testGetApkInstrumentation() throws Exception {
        Path file = TestUtils.resolveWorkspacePath(BASE + "instrument.apk");
        Apk apk = new ApkParser().parsePaths(ImmutableList.of(file.toString())).get(0);
        assertEquals("com.example.android.basicgesturedetect.test", apk.packageName);
        assertEquals(1, apk.targetPackages.size());
        assertEquals("com.example.android.basicgesturedetect", apk.targetPackages.get(0));
    }

    @Test
    public void testGetApkNativeLibs() throws Exception {
        Path file = TestUtils.resolveWorkspacePath(BASE + "native_libs.apk");
        Apk apk = new ApkParser().parsePaths(ImmutableList.of(file.toString())).get(0);
        assertEquals(4, apk.libraryAbis.size());

        String[] allExpected = new String[] {"x86", "x86_64", "armeabi-v7a", "arm64-v8a"};
        for (String expected : allExpected) {
            assertTrue(apk.libraryAbis.contains(expected));
        }
    }

    @Test
    public void testFindCDSigned() throws Exception {
        ApkParser.ApkArchiveMap map = new ApkParser.ApkArchiveMap();
        try (RandomAccessFile file = new RandomAccessFile(BASE + "signed_app/base.apk", "r")) {
            ApkParser.findCDLocation(file.getChannel(), map);
            assertEquals(
                    "CD of signed_app.apk found",
                    true,
                    map.cdOffset != ApkParser.ApkArchiveMap.UNINITIALIZED);
        }
    }

    @Test
    public void testFindCDUnsigned() throws Exception {
        ApkParser.ApkArchiveMap map = new ApkParser.ApkArchiveMap();
        try (RandomAccessFile file = new RandomAccessFile(BASE + "nonsigned_app/base.apk", "r")) {
            ApkParser.findCDLocation(file.getChannel(), map);
            assertEquals(
                    "CD of signed_app.apk found",
                    true,
                    map.cdOffset != ApkParser.ApkArchiveMap.UNINITIALIZED);
        }
    }

    @Test
    public void testFindSignatureBlock() throws Exception {
        ApkParser.ApkArchiveMap map = new ApkParser.ApkArchiveMap();
        try (RandomAccessFile file = new RandomAccessFile(BASE + "signed_app/base.apk", "r")) {
            ApkParser.findCDLocation(file.getChannel(), map);
            ApkParser.findSignatureLocation(file.getChannel(), map);
            assertEquals(
                    "Signature block of signed_app.apk found",
                    true,
                    map.signatureBlockOffset != ApkParser.ApkArchiveMap.UNINITIALIZED);
        }
    }

    static void createZip(long numFiles, int sizePerFile, File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }

        long fileId = 0;
        Random random = new Random(1);
        try (FileOutputStream f = new FileOutputStream(file);
                ZipOutputStream s = new ZipOutputStream(f)) {
            s.setLevel(ZipOutputStream.STORED);
            for (int i = 0; i < numFiles; i++) {
                long id = fileId++;
                String name = String.format("file%06d", id);
                ZipEntry entry = new ZipEntry(name);
                byte[] bytes = new byte[sizePerFile];
                random.nextBytes(bytes);
                s.putNextEntry(entry);
                s.write(bytes);
                s.closeEntry();
            }
        }
    }

    @Test
    public void testParsingBigZip() throws Exception {
        Path tempDirectory = Files.createTempDirectory("");
        try {
            Path zipArchive = tempDirectory.resolve("big.zip");
            int numFiles = 3;
            int sizePerFile = 1_000_000_000;
            createZip(numFiles, sizePerFile, zipArchive.toFile());
            Assert.assertTrue(
                    "Zip is less than 3GiB", Files.size(zipArchive) > numFiles * sizePerFile);
            ApkParser.ApkArchiveMap map = new ApkParser.ApkArchiveMap();
            try (RandomAccessFile file = new RandomAccessFile(zipArchive.toFile(), "r")) {
                ApkParser.findCDLocation(file.getChannel(), map);
                assertEquals(
                        "Central directory offset found",
                        true,
                        map.cdOffset != ApkParser.ApkArchiveMap.UNINITIALIZED);
            }
        } finally {
            PathUtils.deleteRecursivelyIfExists(tempDirectory);
        }
    }

    @Test
    public void testUIntOverflow() {
        long doesNotFitInUint32 = 0x1_FF_FF_FF_FFL;
        boolean exceptionCaught = false;
        try {
            ZipUtils.longToUint(doesNotFitInUint32);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Integer overflow not detected", exceptionCaught);
    }

    @Test
    public void testUIntNoOverflow() {
        long doesFitInUint32 = 0xFF_FF_FF_FFL;
        boolean exceptionCaught = false;
        try {
            ZipUtils.longToUint(doesFitInUint32);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertFalse("Integer overflow thrown", exceptionCaught);
    }

    @Test
    public void testUShortOverflow() {
        int doesNotFitInUint16 = 0x1_FF_FF;
        boolean exceptionCaught = false;
        try {
            ZipUtils.intToUShort(doesNotFitInUint16);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Short overflow not detected", exceptionCaught);
    }

    @Test
    public void testUShortNoOverflow() {
        int doesFitInUint16 = 0xFF_FF;
        boolean exceptionCaught = false;
        try {
            ZipUtils.intToUShort(doesFitInUint16);
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertFalse("Short overflow thrown", exceptionCaught);
    }

    @Test
    public void testGracefulMissingManifest() throws IOException, DeployerException {
        Path tempDirectory = Files.createTempDirectory("");
        try {
            Path zip = tempDirectory.resolve("testGracefulMissingManifest.zip");
            int numFiles = 1;
            int sizePerFile = 1;
            createZip(numFiles, sizePerFile, zip.toFile());
            ApkParser parser = new ApkParser();
            parser.parse(zip.toString());
            Assert.fail("No exception thrown in apk missing AndroidManifest.xml");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().startsWith(ApkParser.NO_MANIFEST_MSG));
        }
    }
}
