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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ApkMaker {

    static final int NUM_DEX = 10;
    private static long fileId = 0;

    public static void create(long numRes, int resSize, int numDex, int dexSize, String path)
            throws IOException {
        Random r = new Random(1);
        try (FileOutputStream f = new FileOutputStream(new File(path));
                ZipOutputStream s = new ZipOutputStream(f)) {
            byte[] resourceBytes = new byte[resSize];
            for (int i = 0; i < numRes; i++) {
                long id = fileId++;
                String name = String.format("res/foo/bar/%06d", id);
                ZipEntry entry = new ZipEntry(name);
                s.putNextEntry(entry);
                r.nextBytes(resourceBytes);
                s.write(resourceBytes);
                s.closeEntry();
            }

            byte[] dexBytes = new byte[dexSize];
            for (int i = 0; i < NUM_DEX; i++) {
                String name = String.format("classes%d.dex", i);
                ZipEntry entry = new ZipEntry(name);
                s.putNextEntry(entry);
                r.nextBytes(dexBytes);
                s.write(dexBytes);
                s.closeEntry();
            }
        }
    }
}
