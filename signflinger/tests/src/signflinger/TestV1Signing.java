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

package signflinger;

import org.junit.Test;

public class TestV1Signing extends TestBaseSigning {

    @Override
    @Test
    public void testSimpleZipWithOneFile() throws Exception {
        super.testSimpleZipWithOneFile();
    }

    @Override
    @Test
    public void testIncrementalSimpleFileTrustManifest() throws Exception {
        super.testIncrementalSimpleFileTrustManifest();
    }

    @Override
    @Test
    public void testIncrementalSimpleFileNoTrustManifest() throws Exception {
        super.testIncrementalSimpleFileNoTrustManifest();
    }

    @Override
    protected SignedApkOptions getOptions(SignerConfig signerConfig, boolean trustManifest) {
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV2Enabled(false)
                        .setV1Enabled(true)
                        .v1CreatedBy("Signflinger")
                        .v1TrustManifest(trustManifest)
                        .setMinSdkVersion(21)
                        .setPrivateKey(signerConfig.privateKey)
                        .setCertificates(signerConfig.certificates)
                        .setExecutor(Utils.createExecutor());
        return builder.build();
    }
}
