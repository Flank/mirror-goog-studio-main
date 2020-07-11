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

package com.android.tools.binaries;

import com.android.zipflinger.BytesSource;
import com.android.zipflinger.StableArchive;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

public class ZipMerger {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !args[0].equals("c")) {
            printUsage();
            return;
        }

        String out = args[1];
        List<String> expanded = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            if (args[i].isEmpty()) {
                continue;
            }
            if (args[i].charAt(0) == '@') {
                expanded.addAll(Files.readAllLines(Paths.get(args[i].substring(1))));
            } else {
                expanded.add(args[i]);
            }
        }

        createZip(out, expanded);
    }

    private static void createZip(String out, List<String> specs) throws Exception {
        File outFile = new File(out);
        if (outFile.exists()) {
            outFile.delete();
        }
        try (StableArchive zip = new StableArchive(new ZipArchive(outFile))) {
            for (String spec : specs) {
                String pathInZip = "";
                String path = spec;
                boolean isZip = false;
                String[] parts = spec.split("=", 2);
                if (parts.length == 2) {
                    pathInZip = parts[0];
                    path = parts[1];
                }
                if (!path.isEmpty() && path.charAt(0) == '+') {
                    isZip = true;
                    path = path.substring(1);
                }
                File file = new File(path);
                if (!isZip) {
                    if (pathInZip.isEmpty()) {
                        pathInZip = file.getName();
                    }
                    zip.add(new BytesSource(file, pathInZip, Deflater.NO_COMPRESSION));
                } else {
                    ZipSource source = new ZipSource(file);
                    String[] keys = source.entries().keySet().toArray(new String[]{});
                    Arrays.sort(keys);
                    for (String s : keys) {
                        source.select(s, pathInZip + s);
                    }
                    zip.add(source);
                }
            }
        }
    }

    private static void printUsage() {
        System.out.println("zip_merger is a zipper tool that supports merging zips.");
        System.out.println("Usage:");
        System.out.println("   zip_merger c <out> <files>");
        System.out.println("Args:");
        System.out.println("     c <out>: Only create is supported, and the given file will be");
        System.out.println("              overridden.");
        System.out.println("     file: A file to add to the zip. It will be added using only its");
        System.out.println("           basename.");
        System.out.println("     +zip_file: A zip file whose entries will be added to the zip,");
        System.out.println("                at the same location they where on the original zip.");
        System.out.println("     <path>=[+]file: A file to add to the zip, where <path> is the");
        System.out.println("                     full path (and name) to be used in the zip.");
        System.out.println("                     If the file is of the form +zip_file, then all");
        System.out.println("                     the entries will be added relative to <path>.");
    }
}
