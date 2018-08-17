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

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

class Bin2C {

    public static final int LINE_SIZE = 12;

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage bin2c binary_path cc_path variable_name");
            System.exit(1);
        }

        byte[] buffer = Files.readAllBytes(Paths.get(args[0]));
        File outputFile = new File(args[1]);
        String varName = args[2];

        outputFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            // Generate array.
            writer.print("unsigned char " + varName + "[] = {");
            for (int i = 0; i < buffer.length; i++) {
                if (i % LINE_SIZE == 0) {
                    writer.println();
                }
                writer.write(String.format("0x%02x, ", buffer[i]));
            }
            writer.println("};");

            writer.println(String.format("uint64_t %s_len = %d;", varName, buffer.length));
            String hash = Hashing.sha256().hashBytes(buffer).toString();
            writer.println(String.format("const char* %s_hash = \"%s\";", varName, hash));
        }
    }
}
