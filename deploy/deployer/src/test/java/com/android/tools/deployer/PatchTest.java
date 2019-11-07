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

import static org.junit.Assert.assertArrayEquals;

import com.android.testutils.TestUtils;
import com.android.tools.deployer.model.Apk;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.Deflater;
import org.junit.Assert;
import org.junit.Test;

public class PatchTest {

    private static final String BASE = "tools/base/deploy/deployer/src/test/resource/patch_tests/";

    @Test
    public void testPatch() throws DeployerException, IOException {
        String localApkPath = TestUtils.getWorkspaceFile(BASE + "local.apk").getAbsolutePath();
        String remoteApkPath = TestUtils.getWorkspaceFile(BASE + "remote.apk").getAbsolutePath();
        patchAndVerify(remoteApkPath, localApkPath);
    }

    // Create two zips differing only by one byte (in a one byte signature).
    @Test
    public void testOffByOneIn1ByteSignature() throws IOException, DeployerException {
        createAPKWithSignatureAndPatch(new byte[1]);
    }

    // Create two zips differing only by one byte (in a 10 bytes signature).
    @Test
    public void testOffByOneIn10BytesSignature() throws IOException, DeployerException {
        createAPKWithSignatureAndPatch(new byte[10]);
    }

    public void createAPKWithSignatureAndPatch(byte[] fakeSignature)
            throws IOException, DeployerException {
        // Create a minimal APK with just a manifest and a fake one byte signature.
        String androidManifestXML = "AndroidManifest.xml";
        String manifestPath =
                TestUtils.getWorkspaceFile(BASE + androidManifestXML).getAbsolutePath();
        File manifest = new File(manifestPath);
        File localZip = new File(TestUtils.getTestOutputDir() + "1-2-3sigx.zip");
        if (localZip.exists()) {
            localZip.delete();
        }
        try (ZipArchive archive = new ZipArchive(localZip)) {
            archive.add(new BytesSource(manifest, androidManifestXML, Deflater.NO_COMPRESSION));
        }
        fakeSignature[0] = (byte) 'x';
        long fakeSignatureOffset = addFakeSignature(localZip, fakeSignature);

        // Create a copy of the apk, differing by only one byte in its fake signature.
        File remoteZip = new File(TestUtils.getTestOutputDir() + "1-2-3sigy.zip");
        Files.copy(localZip.toPath(), remoteZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        fakeSignature[0] = (byte) 'y';
        updateFakeSignature(remoteZip, fakeSignatureOffset, fakeSignature);

        patchAndVerify(localZip.toString(), remoteZip.toString());
    }

    private void updateFakeSignature(File zip, long offset, byte[] fakeSignature)
            throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(zip, "rw");
                FileChannel channel = raf.getChannel()) {
            channel.position(offset);
            channel.write(ByteBuffer.wrap(fakeSignature));
        }
    }

    private long addFakeSignature(File zip, byte[] fakeSignatureByte) throws IOException {
        long fakeSignatureOffset;
        try (FileChannel channel = new RandomAccessFile(zip, "rw").getChannel()) {
            long size = channel.size();
            if (size < ApkParser.EOCD_SIZE) {
                throw new IllegalStateException("Zip smaller than min EOCD size");
            }

            // Find EOCD
            long eocdOffset = size - ApkParser.EOCD_SIZE;
            channel.position(eocdOffset);
            ByteBuffer eocd =
                    ByteBuffer.allocate(ApkParser.EOCD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(eocd);
            eocd.rewind();
            if (eocd.getInt() != ApkParser.EOCD_SIGNATURE) {
                throw new IllegalStateException("Unable to find EOCD");
            }
            eocd.position(12);
            long cdSize = eocd.getInt() & 0xFF_FF_FF_FFL;
            long cdOffset = fakeSignatureOffset = eocd.getInt() & 0xFF_FF_FF_FFL;

            // Find CD
            ByteBuffer cd = ByteBuffer.allocate((int) cdSize);
            channel.position(cdOffset);
            channel.read(cd);
            cd.rewind();

            // Write Signature
            channel.position(cdOffset);
            ByteBuffer fakeSignature = ByteBuffer.wrap(fakeSignatureByte);
            fakeSignature.rewind();
            channel.write(fakeSignature);

            // Write CD
            channel.write(cd);

            // Update and write EOCD
            eocd.position(16);
            eocd.putInt((int) (cdOffset + fakeSignatureByte.length));
            eocd.rewind();
            channel.write(eocd);
        }
        return fakeSignatureOffset;
    }

    private void patchAndVerify(String remoteApkPath, String localApkPath)
            throws DeployerException, IOException {

        List<String> remoteApks = Lists.newArrayList(remoteApkPath);
        Apk remoteApk = new ApkParser().parsePaths(remoteApks).get(0);

        List<String> localApks = Lists.newArrayList(localApkPath);
        Apk localApk = new ApkParser().parsePaths(localApks).get(0);

        // Create patch
        ILogger logger = new NullLogger();
        PatchGenerator.Patch patch = new PatchGenerator(logger).generate(remoteApk, localApk);

        // Check that the patch is small than the remote apk
        long remoteApkSize = Files.size(Paths.get(remoteApkPath));
        Assert.assertTrue(
                "Patch is smaller than apk",
                patch.data.capacity() + patch.instructions.capacity() < remoteApkSize);

        // Apply patch.
        Patcher patcher = new Patcher();
        File dst = new File(TestUtils.getTestOutputDir().getAbsolutePath() + BASE + "patch.apk");
        if (dst.exists()) {
            dst.delete();
        }
        patcher.apply(patch, dst);

        // Make sure the content is the same.
        byte[] reconstructedBytes = Files.readAllBytes(Paths.get(dst.getAbsolutePath()));
        byte[] localBytes = Files.readAllBytes(Paths.get(localApk.path));
        assertArrayEquals(reconstructedBytes, localBytes);
    }
}
