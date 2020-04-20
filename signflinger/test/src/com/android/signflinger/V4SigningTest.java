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

package com.android.signflinger;

import com.android.apksig.apk.ApkFormatException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import org.junit.Rule;
import org.junit.Test;

public class V4SigningTest {

    @Rule public final Workspace workspace = new Workspace();

    @Test
    public void testV4withV3()
            throws IOException, InvalidKeyException, ApkFormatException, NoSuchAlgorithmException {

        File manifest = workspace.getDummyAndroidManifest();
        File file = workspace.createZip(1, 1, "testV4withV3.apk", manifest);
        File v4File = workspace.getTestOutputFile("testV4withV3.sig");
        Files.deleteIfExists(v4File.toPath());

        SignerConfig signerConfig = Signers.getDefaultRSASigner(workspace);
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV3Enabled(true)
                        .setV2Enabled(false)
                        .setV1Enabled(false)
                        .setV4Enabled(true)
                        .setV4Output(v4File)
                        .setPrivateKey(signerConfig.getPrivateKey())
                        .setCertificates(signerConfig.getCertificates())
                        .setExecutor(Utils.createExecutor());
        SignedApkOptions options = builder.build();

        try (SignedApk signedApk = new SignedApk(file, options)) {}
        Utils.verifyApk(file, v4File);
    }

    @Test
    public void testV4withV2()
            throws IOException, InvalidKeyException, ApkFormatException, NoSuchAlgorithmException {

        File manifest = workspace.getDummyAndroidManifest();
        File file = workspace.createZip(1, 1, "testV4withV2.apk", manifest);
        File v4File = workspace.getTestOutputFile("testV4withV2.sig");
        Files.deleteIfExists(v4File.toPath());

        SignerConfig signerConfig = Signers.getDefaultRSASigner(workspace);
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV3Enabled(false)
                        .setV2Enabled(true)
                        .setV1Enabled(false)
                        .setV4Enabled(true)
                        .setV4Output(v4File)
                        .setPrivateKey(signerConfig.getPrivateKey())
                        .setCertificates(signerConfig.getCertificates())
                        .setExecutor(Utils.createExecutor());
        SignedApkOptions options = builder.build();

        try (SignedApk signedApk = new SignedApk(file, options)) {}
        Utils.verifyApk(file, v4File);
    }
}
