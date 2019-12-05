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

import org.junit.Test;

public class V1andV2SigningTest extends BaseSigning {

    @Test
    public void simpleZipWithOneFile() throws Exception {
        super.testSimpleZipWithOneFile();
    }

    @Test
    public void incrementalSimpleFileTrustManifest() throws Exception {
        super.testIncrementalSimpleFileTrustManifest();
    }

    @Test
    public void incrementalSimpleFileNoTrustManifest() throws Exception {
        super.testIncrementalSimpleFileNoTrustManifest();
    }

    @Override
    protected SignedApkOptions getOptions(SignerConfig signerConfig, boolean trustManifest) {
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV2Enabled(true)
                        .setV1Enabled(true)
                        .setV1CreatedBy("Signflinger")
                        .setV1TrustManifest(trustManifest)
                        .setMinSdkVersion(21)
                        .setPrivateKey(signerConfig.getPrivateKey())
                        .setCertificates(signerConfig.getCertificates())
                        .setExecutor(Utils.createExecutor());
        return builder.build();
    }
}
