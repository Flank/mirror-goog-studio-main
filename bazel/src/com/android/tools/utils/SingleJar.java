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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A tool to merge multiple jars into a single one. All the entries are copied
 * and contain the same timestamp as the original ones.
 * Only directories are special cased, so if they show up in multiple input jars
 * The first one is the only one taken, if the same file appears in two different
 * jars, the tool will fail.
 */
public class SingleJar {

    public static void main(String[] args) throws Exception {
        System.exit(new SingleJar().run(Arrays.asList(args)));
    }

    private void usage(String message) {
        System.err.println("Error: " + message);
        System.err.println("Usage: SingleJar jar_file <jars>...");
    }

    private int run(List<String> args) throws IOException {
        File out = null;
        List<File> jars = new LinkedList<>();
        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (out == null) {
                out = new File(arg);
            } else {
                File file = new File(arg);
                if (!file.exists() || file.isDirectory()) {
                    usage("File " + arg + " does not exist or is not a file.");
                    return 1;
                }
                jars.add(file);
            }
        }
        if (out == null) {
            usage("Output file not specified");
            return 1;
        }
        mergeJars(out, jars);

        return 0;
    }

    private void mergeJars(File jar, List<File> jars) throws IOException {
        Set<String> dups = new HashSet<>();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            for (File file : jars) {
                addToJar(file, out, dups);
            }
        }
    }

    private void addToJar(File jar, ZipOutputStream out, Set<String> dups) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path path = Paths.get(entry.getName());
                ArrayDeque<String> files = new ArrayDeque<>();
                files.add(entry.getName());
                path = path.getParent();
                while (path != null) {
                    if (!path.toString().equals("/")) {
                        files.addFirst(path.toString() + "/");
                        path = path.getParent();
                    }
                }
                for (String file : files) {
                    if (!dups.add(file)) {
                        continue;
                    }

                    ZipEntry newEntry = new ZipEntry(file);
                    newEntry.setTime(entry.getTime());
                    out.putNextEntry(newEntry);
                    if (!file.endsWith("/")) {
                        Utils.copy(zis, out);
                    }
                }
            }
        }
    }
}
