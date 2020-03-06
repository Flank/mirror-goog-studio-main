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

import static com.android.zipflinger.Profiler.WARM_UP_ITERATION;
import static com.android.zipflinger.Profiler.displayParameters;
import static com.android.zipflinger.Profiler.prettyPrint;

import com.android.tools.tracer.Trace;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileCreate {

    public static void main(String[] args) throws IOException {
        Path src = Files.createTempDirectory("tmp" + System.nanoTime());
        src.toFile().mkdirs();

        for (int i = 0; i < WARM_UP_ITERATION; i++) {
            File zipFile = new File(src.toFile(), "profileCreate" + i + ".zip");
            zipFile.deleteOnExit();
            createArchive(zipFile);
        }

        displayParameters();

        File zipFile = new File(src.toFile(), "profileCreate.zip");
        zipFile.deleteOnExit();
        Trace.start();
        long start = System.nanoTime();
        try (Trace trace = Trace.begin("Creating archive")) {
            createArchive(zipFile);
        }
        long end = System.nanoTime();
        Trace.flush();
        prettyPrint("Create time (ms)", (int) ((end - start) / 1_000_000L));
    }

    static void createArchive(File src) throws IOException {
        File zipFile = new File(src, "profileCreate.zip");
        try (ZipArchive archive = new ZipArchive(zipFile)) {
            ApkMaker.createArchive(archive);
        }
    }
}
