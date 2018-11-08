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
package com.android.tools.deployer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Patcher {
    public Patcher() {}

    public void apply(PatchGenerator.Patch patch, File destinationFile) throws IOException {
        IntBuffer instructions = patch.instructions.asIntBuffer();

        destinationFile.delete();
        destinationFile.createNewFile();

        // Generate the truncated destination with adequate size.
        byte[] oldBytes = Files.readAllBytes(Paths.get(patch.sourcePath));
        OutputStream output = new FileOutputStream(destinationFile);
        output.write(oldBytes, 0, (int) patch.destinationSize);
        output.close();

        // Patch the old file to generate the new file.
        int dataCursor = 0;
        try (RandomAccessFile raf = new RandomAccessFile(destinationFile, "rw")) {
            while (instructions.hasRemaining()) {
                int offset = instructions.get();
                int size = instructions.get();
                raf.seek(offset);
                raf.write(patch.data.array(), dataCursor, size);
                dataCursor += size;
            }
        }
    }
}
