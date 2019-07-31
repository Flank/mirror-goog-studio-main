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

package com.android.builder.benchmarks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipCreator {

    private static long fileId = 0;

    public static void createZip(long numFiles, int sizePerFile, String path) throws IOException {
        Random r = new Random(1);
        try (FileOutputStream f = new FileOutputStream(new File(path));
                ZipOutputStream s = new ZipOutputStream(f)) {
            for (int i = 0; i < numFiles; i++) {
                long id = fileId++;
                String name = String.format("file%06d", id);
                ZipEntry entry = new ZipEntry(name);
                s.putNextEntry(entry);
                byte[] bytes = new byte[sizePerFile];
                r.nextBytes(bytes);
                s.write(bytes);
                s.closeEntry();
            }
        }
    }
}
