/*
 * Copyright (C) 2022 The Android Open Source Project
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


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * A tool to modify manifest entries in a JAR file.
 */
public class ModifyJarManifest {
    private static final long DEFAULT_TIMESTAMP = LocalDateTime.of(2022, 1, 1, 0, 0, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

    public static void main(String[] args) throws IOException {
        Path jar = null;
        Path output = null;
        List<String> removeEntries = new ArrayList<>();
        List<String> addEntries = new ArrayList<>();

        Iterator<String> iterator = Arrays.asList(args).iterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if (arg.equals("--jar") && iterator.hasNext()) {
                jar = Paths.get(iterator.next());
                if (!Files.isReadable(jar)) {
                    System.err.println("Jar file is not readable");
                    System.exit(1);
                }
            } else if (arg.equals("--out") && iterator.hasNext()) {
                output = Paths.get(iterator.next());
            } else if (arg.equals("--remove-entry") && iterator.hasNext()) {
                removeEntries.add(iterator.next());
            } else if (arg.equals("--add-entry") && iterator.hasNext()) {
                String entry = iterator.next();
                if (!entry.contains(":")) {
                    System.err.println("Invalid entry format, expected KEY:VALUE entries");
                    System.exit(1);
                }
                addEntries.add(entry);
            } else {
                printUsage();
                System.exit(1);
            }
        }
        if (jar == null || output == null) {
            printUsage();
            System.exit(1);
        }
        copyJarAndModifyManifest(jar, output, removeEntries, addEntries);
    }

    private static void copyJarAndModifyManifest(Path source, Path output, List<String> removeEntries, List<String> addEntries) throws IOException {
        try (JarFile jarFile = new JarFile(source.toFile(), false)) {
            Manifest manifest = jarFile.getManifest();
            // There is no existing manifest to modify, make a copy and exit early.
            if (manifest == null) {
                Files.copy(source, output, REPLACE_EXISTING);
                return;
            }
            Attributes mainAttributes = manifest.getMainAttributes();
            mainAttributes.putValue("Created-By", "ModifyJarManifest");
            for (String entry : removeEntries) {
                mainAttributes.remove(new Attributes.Name(entry));
            }
            for (String entry : addEntries) {
                String[] split = entry.split(":");
                mainAttributes.putValue(split[0], split[1]);
            }
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(output))) {
                writeManifest(manifest, jos);
                Enumeration<JarEntry> jarEntries = jarFile.entries();
                while (jarEntries.hasMoreElements()) {
                    JarEntry jarEntry = jarEntries.nextElement();
                    // Skip the existing manifest since we already wrote a manifest.
                    if (jarEntry.getName().equals(JarFile.MANIFEST_NAME)) {
                        continue;
                    }
                    writeEntry(jarFile, jarEntry, jos);
                }
            }
        }
    }

    private static void writeManifest(Manifest manifest, ZipOutputStream zos) throws IOException {
        ZipEntry newManifestEntry = new ZipEntry(JarFile.MANIFEST_NAME);
        newManifestEntry.setTime(DEFAULT_TIMESTAMP);
        zos.putNextEntry(newManifestEntry);
        manifest.write(zos);
        zos.closeEntry();
    }

    private static void writeEntry(JarFile jarFile, JarEntry entry, ZipOutputStream zos) throws IOException {
        ZipEntry newEntry = new ZipEntry(entry.getName());
        newEntry.setComment(entry.getComment());
        newEntry.setExtra(entry.getExtra());
        newEntry.setMethod(entry.getMethod());
        newEntry.setTime(entry.getTime());
        if (entry.getMethod() == ZipEntry.STORED) {
            newEntry.setSize(entry.getSize());
            newEntry.setCrc(entry.getCrc());
        }

        try (InputStream in = jarFile.getInputStream(entry)) {
            zos.putNextEntry(newEntry);
            copy(in, zos);
            zos.closeEntry();
        } catch (ZipException e) {
            if (e.getMessage().contains("duplicate entry:")) {
                // If there is a duplicate entry we keep the first one we saw.
                System.err.println("WARN: Skipping duplicate jar entry " + newEntry.getName() + " in " + jarFile);
            } else {
                throw e;
            }
        }
    }

    private static long copy(InputStream is, OutputStream os) throws IOException{
        byte[] buf = new byte[8126];
        long total = 0L;

        while(true) {
            int r = is.read(buf);
            if (r == -1) {
                return total;
            }

            os.write(buf, 0, r);
            total += (long)r;
        }
    }

    private static void printUsage() {
        System.err.println("Usage: ModifyJarManifest --jar JAR --out OUT [--remove-entry NAME ...]"
                + "[--add-entry NAME:VALUE ...]");
    }

}
