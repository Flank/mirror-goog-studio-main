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
import com.android.zipflinger.BytesSource;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import org.junit.Rule;
import org.junit.Test;

public class V3SigningTest {

    @Rule public final Workspace workspace = new Workspace();

    @Test
    public void testV3()
            throws IOException, InvalidKeyException, ApkFormatException, NoSuchAlgorithmException {
        File manifest = workspace.getDummyAndroidManifest();
        File file = workspace.createZip(10, 10, "testV3.apk", manifest);

        SignerConfig signerConfig = Signers.getDefaultRSASigner(workspace);
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV3Enabled(true)
                        .setV2Enabled(false)
                        .setV1Enabled(false)
                        .setPrivateKey(signerConfig.getPrivateKey())
                        .setCertificates(signerConfig.getCertificates())
                        .setExecutor(Utils.createExecutor());
        SignedApkOptions options = builder.build();

        try (SignedApk signedApk = new SignedApk(file, options)) {}
        Utils.verifyApk(file);
    }

    @Test
    public void testV3AfterModification()
            throws IOException, InvalidKeyException, ApkFormatException, NoSuchAlgorithmException {
        File manifest = workspace.getDummyAndroidManifest();
        File file = workspace.createZip(10, 10, "testV3Edit.apk", manifest);

        SignerConfig signerConfig = Signers.getDefaultRSASigner(workspace);
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV3Enabled(true)
                        .setV2Enabled(false)
                        .setV1Enabled(false)
                        .setPrivateKey(signerConfig.getPrivateKey())
                        .setCertificates(signerConfig.getCertificates())
                        .setExecutor(Utils.createExecutor());
        SignedApkOptions options = builder.build();

        try (SignedApk signedApk = new SignedApk(file, options)) {}
        Utils.verifyApk(file);

        try (SignedApk signedApk = new SignedApk(file, options)) {
            signedApk.add(new BytesSource(new byte[1024], "test1", 0));
        }
        Utils.verifyApk(file);
    }
}
