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

import static com.android.tools.deployer.PatchSet.Status.SizeThresholdExceeded;

import com.android.testutils.TestUtils;
import com.android.tools.deployer.model.Apk;
import com.android.utils.NullLogger;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import org.junit.Assert;
import org.junit.Test;

public class PatchTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    private static void createSimpleZip(File file, byte[] bytes, String entryName)
            throws IOException {
        Files.deleteIfExists(file.toPath());
        File manifestFile = TestUtils.getWorkspaceFile(BASE + "AndroidManifest.xml");
        BytesSource manifestSource =
                new BytesSource(manifestFile, "AndroidManifest.xml", Deflater.NO_COMPRESSION);
        try (ZipArchive archive = new ZipArchive(file)) {
            archive.add(new BytesSource(bytes, entryName, Deflater.NO_COMPRESSION));
            archive.add(manifestSource);
        }
    }

    @Test
    public void testPatchSetTooBig() throws DeployerException, IOException {
        PatchSetGenerator patchSetGenerator =
                new PatchSetGenerator(
                        PatchSetGenerator.WhenNoChanges.GENERATE_EMPTY_PATCH, new NullLogger());
        File testOutputDir = TestUtils.getTestOutputDir();
        ApkParser apkParser = new ApkParser();

        int fileSize = PatchSetGenerator.MAX_PATCHSET_SIZE - 1;
        byte[] bytes = new byte[fileSize];

        File remoteApk1 = new File(testOutputDir, "remoteApk1.apk");
        createSimpleZip(remoteApk1, bytes, "f");
        File remoteApk2 = new File(testOutputDir, "remoteApk2.apk");
        createSimpleZip(remoteApk2, bytes, "f");

        List<String> remoteApksString = new ArrayList<>();
        remoteApksString.add(remoteApk1.getAbsolutePath());
        remoteApksString.add(remoteApk2.getAbsolutePath());

        bytes[0] = 1;
        File localApk1 = new File(testOutputDir, "localApk1.apk");
        createSimpleZip(localApk1, bytes, "f");
        File localApk2 = new File(testOutputDir, "localApk2.apk");
        createSimpleZip(localApk2, bytes, "f");

        List<String> localApksString = new ArrayList<>();
        localApksString.add(localApk1.getAbsolutePath());
        localApksString.add(localApk2.getAbsolutePath());

        List<Apk> remoteApks = apkParser.parsePaths(remoteApksString);
        List<Apk> localApks = apkParser.parsePaths(localApksString);

        PatchSet patchSet = patchSetGenerator.generateFromApks(remoteApks, localApks);
        Assert.assertTrue("Patch size is too big", patchSet.getStatus() == SizeThresholdExceeded);
    }

    @Test
    public void testPatchTooBig() throws IOException, DeployerException {
        PatchGenerator patchGenerator = new PatchGenerator(new NullLogger());
        File testOutputDir = TestUtils.getTestOutputDir();

        int fileSize = PatchSetGenerator.MAX_PATCHSET_SIZE + 1;

        byte[] bytes = new byte[fileSize];
        File remote = new File(testOutputDir, "local.apk");
        createSimpleZip(remote, bytes, "f");

        bytes[0] = 1;
        File local = new File(testOutputDir, "remote.apk");
        createSimpleZip(local, bytes, "f");

        ApkParser apkParser = new ApkParser();
        List<Apk> remoteApks =
                apkParser.parsePaths(Arrays.asList(new String[] {remote.getAbsolutePath()}));
        List<Apk> localApks =
                apkParser.parsePaths(Arrays.asList(new String[] {local.getAbsolutePath()}));
        Apk remoteApk = remoteApks.get(0);
        Apk localApk = localApks.get(0);

        PatchGenerator.Patch patch = patchGenerator.generate(remoteApk, localApk);
        Assert.assertTrue(
                "Patch size is too big",
                patch.status == PatchGenerator.Patch.Status.SizeThresholdExceeded);
    }
}
