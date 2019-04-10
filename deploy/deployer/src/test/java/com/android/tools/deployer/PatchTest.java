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
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PatchTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/patch_tests/";

    @Test
    public void testPatch() throws DeployerException, IOException {
        String remoteApkPath = TestUtils.getWorkspaceFile(BASE + "remote.apk").getAbsolutePath();
        List<String> remoteApks = Lists.newArrayList(remoteApkPath);
        List<ApkEntry> remoteEntries = new ApkParser().parsePaths(remoteApks);
        Apk remoteApk =
                Apk.builder()
                        .setPath(remoteApkPath)
                        .setZipEntries(remoteEntries.get(0).apk.zipEntries)
                        .build();

        String localApkPath = TestUtils.getWorkspaceFile(BASE + "local.apk").getAbsolutePath();
        List<String> localApks = Lists.newArrayList(localApkPath);
        List<ApkEntry> localEntries = new ApkParser().parsePaths(localApks);
        Apk localApk =
                Apk.builder()
                        .setPath(localApkPath)
                        .setZipEntries(localEntries.get(0).apk.zipEntries)
                        .build();

        PatchGenerator.Patch patch = new PatchGenerator().generate(remoteApk, localApk);

        // Check that the patch is small than the remote apk
        long remoteApkSize = Files.size(Paths.get(remoteApkPath));
        assertTrue(
                "Patch is smaller than apk",
                patch.data.capacity() + patch.instructions.capacity() < remoteApkSize);

        // Hard-coded value is specific to this test.
        assertEquals("Patch size", patch.data.capacity() + patch.instructions.capacity(), 1458384);

        // Apply patch.
        Patcher patcher = new Patcher();
        String patchedFilePath =
                TestUtils.getTestOutputDir().getAbsolutePath() + BASE + "patch.apk";
        File dst = new File(patchedFilePath);
        patcher.apply(patch, dst);

        // Make sure the content is the same.
        byte[] patchedBytes = Files.readAllBytes(Paths.get(dst.getAbsolutePath()));
        byte[] localBytes = Files.readAllBytes(Paths.get(localApk.path));

        assertTrue(Arrays.equals(patchedBytes, localBytes));
    }
}
