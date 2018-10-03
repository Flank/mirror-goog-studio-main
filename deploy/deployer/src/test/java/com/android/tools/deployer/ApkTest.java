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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.Test;

public class ApkTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    @Test
    public void testCentralDirectoryParse() throws IOException {
        File file = TestUtils.getWorkspaceFile(BASE + "base.apk.remotecd");
        byte[] fileContent = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
        HashMap<String, Long> crcs = ZipUtils.readCrcs(fileContent);
        assertEquals(1007, crcs.size());
        long manifestCrc = crcs.get("AndroidManifest.xml");
        assertEquals(0x5804A053, manifestCrc);
    }

    @Test
    public void testApkId() {
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        ApkFull local = new ApkFull(file.getPath());
        assertEquals("74eaa38f4d4d8619c7bb886289f84efe1fce7ce3", local.getDigest());
    }

    @Test
    public void testApkArchiveMap() {
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        ApkFull apkFull = new ApkFull(file.getAbsolutePath());
        ApkFull.ApkArchiveMap apkArchiveMap = apkFull.getMap();
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.cdOffset);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.cdSize);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.eocdOffset);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.eocdSize);
        assertEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.signatureBlockOffset);
        assertEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.signatureBlockSize);
    }

    @Test
    public void testApkArchiveV2Map() {
        File file = TestUtils.getWorkspaceFile(BASE + "v2_signed.apk");
        ApkFull apkFull = new ApkFull(file.getAbsolutePath());
        ApkFull.ApkArchiveMap apkArchiveMap = apkFull.getMap();
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.cdOffset);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.cdSize);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.eocdOffset);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.eocdSize);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.signatureBlockOffset);
        assertNotEquals(ApkFull.ApkArchiveMap.UNINITIALIZED, apkArchiveMap.signatureBlockSize);
    }

    @Test
    public void testApkArchiveApkDumpdMatchCrcs() throws IOException {
        String app1Base = BASE + "signed_app/";
        File file = TestUtils.getWorkspaceFile(app1Base + "base.apk");
        File cd = new File(file.getParent(), "base.apk.remotecd");
        File sig = new File(file.getParent(), "base.apk.remoteblock");
        ApkFull local = new ApkFull(file.getPath());
        ApkDump remote =
                new ApkDump(
                        "base.apk",
                        ByteBuffer.wrap(Files.readAllBytes(cd.toPath())),
                        ByteBuffer.wrap(Files.readAllBytes(sig.toPath())));

        assertEquals(local.getCrcs().keySet().size(), remote.getCrcs().keySet().size());
        assertTrue(
                Arrays.equals(
                        local.getCrcs().keySet().toArray(), remote.getCrcs().keySet().toArray()));
        assertTrue(
                Arrays.equals(
                        local.getCrcs().values().toArray(), remote.getCrcs().values().toArray()));
    }

    @Test
    public void testApkArchiveApkV2SignedDumpdMatchDigest() throws IOException {
        String app1Base = BASE + "signed_app/";
        File file = TestUtils.getWorkspaceFile(app1Base + "base.apk");
        File cd = new File(file.getParent(), "base.apk.remotecd");
        File sig = new File(file.getParent(), "base.apk.remoteblock");
        ApkFull local = new ApkFull(file.getPath());
        ApkDump remote =
                new ApkDump(
                        "base.apk",
                        ByteBuffer.wrap(Files.readAllBytes(cd.toPath())),
                        ByteBuffer.wrap(Files.readAllBytes(sig.toPath())));
        assertEquals(local.getDigest(), remote.getDigest());
    }

    @Test
    public void testApkArchiveApkNonV2SignedDumpdMatchDigest() throws IOException {
        String app1Base = BASE + "nonsigned_app/";
        File file = TestUtils.getWorkspaceFile(app1Base + "base.apk");
        File cd = new File(file.getParent(), "base.apk.remotecd");
        ApkFull local = new ApkFull(file.getPath());
        ApkDump remote =
                new ApkDump("base.apk", ByteBuffer.wrap(Files.readAllBytes(cd.toPath())), null);
        assertEquals(local.getDigest(), remote.getDigest());
    }

    @Test
    public void testGetApkDetails() throws IOException {
        File file = TestUtils.getWorkspaceFile(BASE + "multiprocess.apk");
        ApkFull apkFull = new ApkFull(file.getAbsolutePath());
        ApkFull.ApkDetails apkDetails = apkFull.getApkDetails();

        assertEquals(apkDetails.fileName(), "base.apk");
        assertEquals(apkDetails.processNames().size(), 5);

        assertTrue(apkDetails.processNames().contains("com.test.multiprocess"));
        assertTrue(apkDetails.processNames().contains("alternate.default"));
        assertTrue(apkDetails.processNames().contains("com.test.multiprocess:service"));
        assertTrue(apkDetails.processNames().contains("com.test.multiprocess:private"));
        assertTrue(apkDetails.processNames().contains(".global"));
    }
}
