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
import java.nio.file.Files;
import java.util.Arrays;

public class Benchmarks {

    public static void main(String[] args) throws Exception {
        File file = Utils.getTestOutputFile("apk-12MiB.apk");
        Utils.createZip(21, 1 << 20, file);
        test(file);

        file = Utils.getTestOutputFile("apk-42MiB.apk");
        Utils.createZip(41, 1 << 20, file);
        test(file);
    }

    private static void test(File file) throws Exception {
        for (Signer signer : Signers.signers) {
            long times[] = new long[3];
            SignResult result = null;
            for (int i = 0; i < times.length; i++) {
                result = V2Signer.sign(file, signer);
                times[i] = result.time;
            }
            Utils.verify(result.file);
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
