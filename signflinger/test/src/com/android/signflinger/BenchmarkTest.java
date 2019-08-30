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
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;

public class BenchmarkTest extends TestBaseV2 {

    @Test
    public void run() throws Exception {
        File file = getTestOutputFile("apk-12MiB.apk");
        createZip(21, 1 << 20, file);
        signAndVerify(file);

        file = getTestOutputFile("apk-42MiB.apk");
        createZip(41, 1 << 20, file);
        signAndVerify(file);
    }

    private void signAndVerify(File file) throws Exception {
        for (Signer signer : SIGNERS) {
            long times[] = new long[3];
            SignResult result = null;
            for (int i = 0; i < times.length; i++) {
                result = sign(file, signer);
                times[i] = result.time;
            }
            verify(result.file);
            Arrays.sort(times);
            long timeMs = times[times.length / 2];
            long fileSizeMiB = Files.size(file.toPath()) / (1 << 20);
            String message =
                    String.format(
                            "V2 signed %d MiB in %3d ms (%s-%s)",
                            fileSizeMiB, timeMs, signer.type, signer.subtype);
            System.out.println(message);
        }
    }
}
