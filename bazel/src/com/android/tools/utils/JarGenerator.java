/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JarGenerator {

    private byte[] mBuffer = new byte[1024 * 1024];

    private static final long DOS_EPOCH = new GregorianCalendar(1980, 0, 1, 0, 0, 0).getTimeInMillis();
    /**
     * Moves a directory into a .jar. All the files are added relative to the given directory.
     * The directory itself is also deleted.
     *
     * @param directory the directory whose files will be deleted and moved into a jar.
     * @param file the jar file to create.
     */
    public void directoryToJar(File directory, File file) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            addToJar(directory, out, "");
        }
        directory.delete();
    }

    private void addToJar(File tmp, ZipOutputStream out, String name) throws IOException {
        File[] files = tmp.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String newName = name + (name.isEmpty() ? "" : "/") + file.getName();
            String entryName = file.isDirectory() ? newName + "/" : newName;
            ZipEntry entry = new ZipEntry(entryName);
            // Adding two seconds to match what Bazel does on jar generation.
            entry.setTime(DOS_EPOCH + 2);
            if (file.isDirectory()) {
                // Trailing slash to mark a directory entry
                out.putNextEntry(entry);
                out.closeEntry();
                addToJar(file, out, newName);
            } else {
                out.putNextEntry(entry);
                try (FileInputStream in = new FileInputStream(file)) {
                    int k;
                    while ((k = in.read(mBuffer)) != -1) {
                        out.write(mBuffer, 0, k);
                    }
                }
                out.closeEntry();
            }
            file.delete();
        }
    }
}
