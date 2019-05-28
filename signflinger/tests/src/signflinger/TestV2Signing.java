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

import java.io.File;
import org.junit.Test;

public class TestV2Signing {

    @Test
    public void testV2SignNormalApk() throws Exception {
        File file = Utils.getTestOuputFile("apk-22MiB.apk");
        Utils.createZip(1, 12_000_000, file);
        v2Sign(file);
    }

    @Test
    public void testV2kSignBigApk() throws Exception {
        File file = Utils.getTestOuputFile("apk-42MiB.apk");
        Utils.createZip(1, 42_000_000, file);
        v2Sign(file);
    }

    private void v2Sign(File file) throws Exception {
        for (Signer signer : Signers.signers) {
            SignResult result = V2Signer.sign(file, signer);
            Utils.verify(result.file);
        }
    }
}
