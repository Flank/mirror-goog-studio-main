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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.Test;

public class DeployerTest {

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
        Apk apk = new Apk(file.getAbsolutePath(), BASE);
        assertEquals(apk.getLocalArchive().getDigest(), "74eaa38f4d4d8619c7bb886289f84efe1fce7ce3");
    }

    @Test
    public void testApkArchiveMap() {
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        ApkFull apkFull = new ApkFull(file.getAbsolutePath());
        ApkFull.ApkArchiveMap apkArchiveMap = apkFull.getMap();
        assertNotEquals(apkArchiveMap.cdOffset, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.cdSize, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.eocdOffset, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.eocdSize, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertEquals(apkArchiveMap.signatureBlockOffset, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertEquals(apkArchiveMap.signatureBlockSize, ApkFull.ApkArchiveMap.UNINITIALIZED);
    }

    @Test
    public void testApkArchiveV2Map() {
        File file = TestUtils.getWorkspaceFile(BASE + "v2_signed.apk");
        ApkFull apkFull = new ApkFull(file.getAbsolutePath());
        ApkFull.ApkArchiveMap apkArchiveMap = apkFull.getMap();
        assertNotEquals(apkArchiveMap.cdOffset, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.cdSize, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.eocdOffset, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.eocdSize, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.signatureBlockOffset, ApkFull.ApkArchiveMap.UNINITIALIZED);
        assertNotEquals(apkArchiveMap.signatureBlockSize, ApkFull.ApkArchiveMap.UNINITIALIZED);
    }

    @Test
    public void testApkArchiveApkDumpdMatchCrcs() {
        String app1Base = BASE + "signed_app/";
        File file = TestUtils.getWorkspaceFile(app1Base + "base.apk");
        File workingDirectory = TestUtils.getWorkspaceFile(app1Base);
        Apk apk = new Apk(file.getAbsolutePath(), workingDirectory.getAbsolutePath());

        assertEquals(
                apk.getLocalArchive().getCrcs().keySet().size(),
                apk.getRemoteArchive().getCrcs().keySet().size());
        assertTrue(
                Arrays.equals(
                        apk.getLocalArchive().getCrcs().keySet().toArray(),
                        apk.getRemoteArchive().getCrcs().keySet().toArray()));
        assertTrue(
                Arrays.equals(
                        apk.getLocalArchive().getCrcs().values().toArray(),
                        apk.getRemoteArchive().getCrcs().values().toArray()));
    }

    @Test
    public void testApkArchiveApkV2SignedDumpdMatchDigest() {
        String app1Base = BASE + "signed_app/";
        File file = TestUtils.getWorkspaceFile(app1Base + "base.apk");
        File workingDirectory = TestUtils.getWorkspaceFile(app1Base);
        Apk apk = new Apk(file.getAbsolutePath(), workingDirectory.getAbsolutePath());
        assertEquals(apk.getLocalArchive().getDigest(), apk.getRemoteArchive().getDigest());
    }

    @Test
    public void testApkArchiveApkNonV2SignedDumpdMatchDigest() {
        String app1Base = BASE + "nonsigned_app/";
        File file = TestUtils.getWorkspaceFile(app1Base + "base.apk");
        File workingDirectory = TestUtils.getWorkspaceFile(app1Base);
        Apk apk = new Apk(file.getAbsolutePath(), workingDirectory.getAbsolutePath());
        assertEquals(apk.getLocalArchive().getDigest(), apk.getRemoteArchive().getDigest());
    }
}
