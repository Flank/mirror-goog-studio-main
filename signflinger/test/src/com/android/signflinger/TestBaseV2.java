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

import java.io.File;
import java.nio.file.Path;

public class TestBaseV2 extends TestBase {
    public SignResult sign(File fileToSign, Signer signer) throws Exception {
        SignerConfig signerConfig = getSignerConfig(signer.type, signer.subtype);

        Path dst = getTestOutputPath("signed.apk");
        TestBase.copy(fileToSign.toPath(), dst);

        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV2Enabled(true)
                        .setV1Enabled(false)
                        .setPrivateKey(signerConfig.privateKey)
                        .setCertificates(signerConfig.certificates)
                        .setExecutor(createExecutor());
        SignedApkOptions options = builder.build();

        long startTime = System.nanoTime();
        try (SignedApk s = new SignedApk(dst.toFile(), options)) {}
        return new SignResult(dst.toFile(), (System.nanoTime() - startTime) / 1_000_000);
    }
}
