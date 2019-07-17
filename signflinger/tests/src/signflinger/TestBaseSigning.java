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

import com.android.zipflinger.FileSource;
import java.io.File;

public abstract class TestBaseSigning {

    public void testSimpleZipWithOneFile() throws Exception {
        for (Signer signer : Signers.signers) {
            File file = Utils.getTestOuputFile("emptyApk.apk");
            Utils.createZip(800, 20000, file);
            SignerConfig signerConfig = Utils.getSignerConfig(signer.type, signer.subtype);
            SignedApkOptions options = getOptions(signerConfig, false);

            long time = System.nanoTime();
            SignedApk signedApk = new SignedApk(file, options);
            signedApk.close();
            System.out.println("V1 time:" + (System.nanoTime() - time) / 1_000_000);
            Utils.verify(file);
        }
    }

    public void testIncrementalSimpleFileTrustManifest() throws Exception {
        signTwice(true);
    }

    public void testIncrementalSimpleFileNoTrustManifest() throws Exception {
        signTwice(false);
    }

    private void signTwice(boolean trustManifest) throws Exception {
        File file = Utils.getTestOuputFile("void.apk");
        Utils.createZip(800, 20000, file);

        SignerConfig signerConfig = Signers.getDefaultRSA();
        SignedApkOptions options = getOptions(signerConfig, trustManifest);

        // Sign a first time
        SignedApk signedApk = new SignedApk(file, options);
        signedApk.close();
        Utils.verify(file);

        // Incremental signing
        signedApk = new SignedApk(file, options);
        signedApk.add(new FileSource(Utils.getFile("test1.txt"), "test1.txt", 1));
        signedApk.close();
        Utils.verify(file);
    }

    protected abstract SignedApkOptions getOptions(
            SignerConfig signerConfig, boolean trustManifest);
}
