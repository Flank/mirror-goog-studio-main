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
import static com.android.zipflinger.Profiler.NUM_DEX;
import static com.android.zipflinger.Profiler.NUM_RES;
import static com.android.zipflinger.Profiler.RES_SIZE;
import static com.android.zipflinger.Profiler.WARM_UP_ITERATION;
import static com.android.zipflinger.Profiler.displayParameters;
import static com.android.zipflinger.Profiler.prettyPrint;

import com.android.tools.tracer.Trace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;

public class ProfileAdd {

    public static void main(String[] args) throws IOException {
        Path src = Files.createTempDirectory("tmp" + System.nanoTime());
        Files.createDirectories(src);
        Path zipFile = src.resolve("profileAdd.zip");
        zipFile.toFile().deleteOnExit();

        // Fake 32 MiB aapt2 like zip archive.
        ApkMaker.createWithDescriptors(NUM_RES, RES_SIZE, NUM_DEX, DEX_SIZE, zipFile.toString());

        byte[] fakeDex = new byte[DEX_SIZE];
        for (int i = 0; i < WARM_UP_ITERATION; i++) {
            editArchive(i, zipFile, fakeDex);
        }

        displayParameters();

        Trace.start();
        long start = System.nanoTime();
        try (Trace t = Trace.begin("Editing archive")) {
            editArchive(WARM_UP_ITERATION, zipFile, fakeDex);
        }
        long end = System.nanoTime();
        Trace.flush();
        prettyPrint("Edit time (ms)", (int) ((end - start) / 1_000_000L));
    }

    private static void editArchive(int i, Path file, byte[] data) throws IOException {
        try (ZipArchive archive = new ZipArchive(file)) {
            String entry = String.format("classes%d.dex", i);
            archive.delete(entry);
            BytesSource s = new BytesSource(data, entry, Deflater.NO_COMPRESSION);
            archive.add(s);
        }
    }
}
