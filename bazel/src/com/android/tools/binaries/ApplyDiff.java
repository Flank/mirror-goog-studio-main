/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.binaries;

import com.android.testutils.diff.UnifiedDiff;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

class ApplyDiff {

    public static void main(String[] args) throws IOException {
        System.exit(new ApplyDiff().run(Arrays.asList(args)));
    }

    private int run(List<String> args) throws IOException {
        Path outFile = null;
        Path inFile = null;
        Path diffFile = null;

        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("--output_file") && it.hasNext()) {
                outFile = Paths.get(it.next());
            } else if (arg.equals("--input_file") && it.hasNext()) {
                inFile = Paths.get(it.next());
            } else if (arg.equals("--diff_file") && it.hasNext()) {
                diffFile = Paths.get(it.next());
            } else {
                throw new IllegalArgumentException("Unknown argument" + arg);
            }
        }

        if (outFile == null || inFile == null || diffFile == null) {
            System.err.println("Requires all of --output_file, --input_file and ---diff_file");
            return 1;
        }

        Path tempDir = Files.createTempDirectory("diff_apply");
        Path tempFile = tempDir.resolve(inFile.getFileName());
        Files.copy(inFile, tempFile);

        UnifiedDiff diff = new UnifiedDiff(Files.readAllLines(diffFile));
        diff.apply(tempDir.toFile(), 1);

        Files.createDirectories(outFile.getParent());
        Files.copy(tempFile, outFile);

        return 0;
    }
}
