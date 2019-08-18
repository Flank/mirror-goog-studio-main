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

package com.android.signflinger;

import static org.junit.Assert.assertTrue;

import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;

public class V2SigningTest {

    @Rule public final Workspace workspace = new Workspace();

    @Test
    public void v2SignNormalApk() throws Exception {
        createZipAndSign(12_000_000);
    }

    @Test
    public void v2SignBigApk() throws Exception {
        createZipAndSign(42_000_000);
    }

    private void createZipAndSign(int size) throws Exception {
        File androidManifest = workspace.getDummyAndroidManifest();
        File file = workspace.createZip(1, size, "apk-" + size + ".apk", androidManifest);
        assertTrue(Files.size(file.toPath()) > size);
        v2Sign(file);
    }

    private void v2Sign(File file) throws Exception {

        for (SignerConfig signerConfig : Signers.getAll(workspace)) {
            // Remove signature
            try (ZipArchive ignored = new ZipArchive(file)) {}
            // Sign
            V2Signer.sign(file, signerConfig);
            // Verify
            Utils.verifyApk(file);
        }
    }

    @Test
    public void benchmarkAddAndV2sign() throws Exception {
        File androidManifest = workspace.getDummyAndroidManifest();
        File file = workspace.createZip(400, 120_000, "apk-42MiB-400files.apk", androidManifest);

        SignerConfig signerConfig = Signers.getDefaultRSASigner(workspace);
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV2Enabled(true)
                        .setV1Enabled(false)
                        .setPrivateKey(signerConfig.getPrivateKey())
                        .setCertificates(signerConfig.getCertificates())
                        .setExecutor(Utils.createExecutor());
        SignedApkOptions options = builder.build();

        long start = System.nanoTime();
        try (SignedApk signedApk = new SignedApk(file, options)) {
            signedApk.add(new BytesSource(workspace.getResourceFile("test1.txt"), "test1", 1));
        }
        long totalTime = (System.nanoTime() - start) / 1000000;
        Utils.verifyApk(file);
        System.out.println("Adding and Signing time=" + totalTime);
    }

}
