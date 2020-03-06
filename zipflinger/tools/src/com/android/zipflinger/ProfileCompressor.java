/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.zipflinger;

import static com.android.zipflinger.Profiler.DEX_SIZE;

import com.android.tools.tracer.Trace;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.Deflater;

public class ProfileCompressor {
    public static void main(String[] args) throws IOException {

        byte[] bytes = new byte[DEX_SIZE];

        // Warmup
        Compressor.deflate(bytes, Deflater.BEST_COMPRESSION);
        Compressor.deflate(bytes, Deflater.BEST_SPEED);

        Trace.start();

        runProfiling(bytes);

        try (Trace t = Trace.begin("Randomizing")) {
            Random r = new Random(0);
            r.nextBytes(bytes);
        }

        runProfiling(bytes);
    }

    private static void runProfiling(byte[] bytes) throws IOException {
        ByteBuffer compressedBytes = null;
        for (int i = Deflater.BEST_COMPRESSION; i >= Deflater.BEST_SPEED; i--) {
            compressedBytes = Compressor.deflate(bytes, i);
        }

        ByteBuffer decompressedBytes = Compressor.inflate(compressedBytes.array());
    }
}
