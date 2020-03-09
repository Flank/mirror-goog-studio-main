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

import com.android.tools.tracer.Trace;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;

public class Profiler {

    private static final int NUM_RES = 2500;
    private static final int RES_SIZE = 1 << 12; //  4 KiB

    private static final int NUM_DEX = 10;
    private static final int DEX_SIZE = 1 << 22; //  4 MiB

    private static final int TOTAL_APK_SIZE = RES_SIZE * NUM_RES + DEX_SIZE * NUM_DEX;

    private static final int WARM_UP_ITERATION = 2;

    private static void prettyPrint(String label, int value) {
        String string = String.format("  - %-17s %1s %5d", label, ":", value);
        System.out.println(string);
    }

    public static void main(String[] args) throws IOException {

        Path src = Files.createTempDirectory("tmp" + System.nanoTime());
        src.toFile().mkdirs();
        File zipFile = new File(src.toFile(), "archive.zip");

        // Fake 32 MiB aapt2 like zip archive.
        ApkMaker.create(NUM_RES, RES_SIZE, NUM_DEX, DEX_SIZE, zipFile.toString());

        byte[] fakeDex = new byte[DEX_SIZE];
        for (int i = 0; i < WARM_UP_ITERATION; i++) {
            editArchive(i, zipFile, fakeDex);
        }

        System.out.println("Profiling with an APK:");
        prettyPrint("Total size (MiB)", TOTAL_APK_SIZE / (1 << 20));
        prettyPrint("Num res", NUM_RES);
        prettyPrint("Size res (KiB)", RES_SIZE / (1 << 10));
        prettyPrint("Num dex", NUM_DEX);
        prettyPrint("Size dex (MiB)", DEX_SIZE / (1 << 20));

        Trace.start();
        Trace.begin("Profiling");
        long start = System.nanoTime();
        editArchive(WARM_UP_ITERATION, zipFile, fakeDex);
        long end = System.nanoTime();
        Trace.end();
        Trace.flush();
        prettyPrint("Edit time (ms)", (int) ((end - start) / 1_000_000L));
    }

    private static void editArchive(int i, File File, byte[] data) throws IOException {
        try (ZipArchive archive = new ZipArchive(File)) {
            String entry = String.format("classes%d.dex", i);
            archive.delete(entry);
            BytesSource s = new BytesSource(data, entry, Deflater.NO_COMPRESSION);
            archive.add(s);
        }
    }
}
