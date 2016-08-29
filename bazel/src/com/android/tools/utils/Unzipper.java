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
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Unzipper {

    /**
     * Unzips files from a zip file to the specified locations.
     * Each file putput needs to be individually specified. For example:
     *
     * Unzipper file.zip my/compressed/path/image.png:new/path/and/name.png
     */
    public static void main(String[] args) throws IOException {
        System.exit(new Unzipper().run(Arrays.asList(args)));
    }

    private void usage(String message) {
        System.err.println("Error: " + message);
        System.err.println("Usage: Unzipper <zip_file> file:target file:target...");
    }

    private int run(List<String> args) throws IOException {
        File zip = null;
        Map<String, String> files = new HashMap<>();

        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (zip == null) {
                zip = new File(arg);
            } else {
                String[] split = arg.split(":");
                if (split.length != 2) {
                    usage("Invalid file map entry: " + arg);
                }
                files.put(split[0], split[1]);
            }
        }
        if (zip == null) {
            usage("Input zip file not specified.");
            System.exit(1);
        }
        if (files.isEmpty()) {
            usage("No output files specified.");
            System.exit(1);
        }
        return unzip(zip, files);
    }

    private int unzip(File zipFile, Map<String, String> files) throws IOException {

        try (ZipFile zip = new ZipFile(zipFile)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                String name = file.getKey();
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) {
                    System.err.println("Entry " + name + " not found.");
                    return 1;
                }
                File outFile = new File(file.getValue());
                File dirname = outFile.getParentFile();
                if (!dirname.exists() && !dirname.mkdirs()) {
                    System.err.println("Cannot create directory for " + outFile.getAbsolutePath());
                }
                try (InputStream in = zip.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    Utils.copy(in, out);
                }
            }
        }
        return 0;
    }

    public void unzip(File zipFile, File outDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    File file = new File(outDir, entry.getName());
                    File dirname = file.getParentFile();
                    if (!dirname.exists() && !dirname.mkdirs()) {
                        System.err.println("Cannot create directory for " + file.getAbsolutePath());
                    }
                    try (FileOutputStream os = new FileOutputStream(file)) {
                        Utils.copy(zis, os);
                    }
                }
            }
        }
    }
}
