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
import com.android.zipflinger.Source;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipMap;
import com.android.zipflinger.ZipRepo;
import com.android.zipflinger.ZipSource;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

public class ZipMerger {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !(args[0].equals("c") || args[0].equals("cC"))) {
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
        int level = args[0].equals("cC") ? Deflater.BEST_COMPRESSION : Deflater.NO_COMPRESSION;

        List<ZipFile> adds = new ArrayList<>();
        for (String arg : expanded) {
            String root = "";
            boolean override = false;
            String dataPath = arg;
            String[] parts = arg.split("=", 2);
            if (parts.length == 2) {
                root = parts[0];
                dataPath = parts[1];
            }
            if (!dataPath.isEmpty() && dataPath.charAt(0) == '+') {
                override = true;
                dataPath = dataPath.substring(1);
            }
            adds.add(new ZipFile(root, ZipMap.from(Paths.get(dataPath)), override));
        }
        mergeZips(out, adds, true, level);
    }

    private static class ZipFile {
        ZipFile(String root, ZipMap map, boolean override) {
            this.root = root;
            this.override = override;
            this.map = map;
        }

        final boolean override;
        final String root;
        final ZipMap map;
    }

    private static class Entry {
        Entry(ZipFile zip, String entry) {
            this.zip = zip;
            this.entry = entry;
        }

        final ZipFile zip;
        final String entry;
    }

    /**
     * Recursively merge a set of zip files, in <files> into one zip file. If the same entry is in
     * two or more zip files, the tool will throw an error, unless one of them is marked to
     * override. If two or more entries collide, the behavior depends on whether the entry is an
     * archive (zip or jar) or a file. If multiple source zip files provide the same file, the tool
     * will fail unless exactly one of them is comes from an override zip. In that case, that entry
     * is chosen. If multiple source zip files provide the same archive, then only one of them is
     * allowed not to come from an override zip. If that's the case, all these archived are merged
     * recursively. See the tests for examples.
     *
     * <p>If ensureRW is true, all the entries in the resulting zip will have linux read/write
     * permissions.
     */
    private static void mergeZips(
            String out, List<ZipFile> files, boolean ensureRW, int compressionLevel)
            throws Exception {
        Path outFile = Paths.get(out);
        Files.deleteIfExists(outFile);

        ListMultimap<String, Entry> entries = LinkedListMultimap.create();
        for (ZipFile add : files) {
            for (String s : add.map.getEntries().keySet()) {
                String newName = add.root + s;
                entries.put(newName, new Entry(add, s));
            }
        }

        // Merge
        // Zip sources are not pre-populated to allow interleaving of inner zips.
        LinkedHashMap<ZipFile, ZipSource> zipSources = new LinkedHashMap<>();
        for (Map.Entry<String, Collection<Entry>> e : entries.asMap().entrySet()) {
            String newName = e.getKey();
            Collection<Entry> zips = e.getValue();
            if (zips.size() == 1) {
                Entry next = zips.iterator().next();
                select(zipSources, next, newName);
                continue;
            }
            boolean isZip = newName.endsWith(".jar") || newName.endsWith(".zip");
            if (isZip) {
                // We recursively merge the zips if they can be overridden
                Entry doNotOverride = null;
                for (Entry zip : zips) {
                    if (!zip.zip.override) {
                        if (doNotOverride != null) {
                            throw new IllegalArgumentException(
                                    "Attempting to merge zips from entries with no overrides");
                        }
                        doNotOverride = zip;
                    }
                }
                Path tmp = File.createTempFile(new File(newName).getName(), ".mrg.tmp").toPath();
                List<ZipFile> toMerge = new ArrayList<>();
                for (Entry zip : zips) {
                    ZipMap innerZipMap = extract(zip.zip.map, zip.entry);
                    toMerge.add(new ZipFile("", innerZipMap, zip.zip.override));
                }
                mergeZips(tmp.toAbsolutePath().toString(), toMerge, false, compressionLevel);
                for (ZipFile zipFile : toMerge) {
                    Files.delete(zipFile.map.getPath());
                }
                Path mergedZip = File.createTempFile("merged", ".zip").toPath();
                createZip(mergedZip, tmp, newName, compressionLevel);
                Files.delete(tmp);
                ZipFile zip = new ZipFile("", ZipMap.from(mergedZip), false);
                select(zipSources, new Entry(zip, newName), newName);
                mergedZip.toFile().deleteOnExit();
            } else if (newName.endsWith("/")) {
                // Directory entries should not have content, just pick the first.
                Entry chosen = zips.iterator().next();
                select(zipSources, chosen, newName);
            } else {
                // Merging files - Pick the one override, or fail.
                Entry chosen = null;
                for (Entry entry : zips) {
                    if (entry.zip.override) {
                        if (chosen != null) {
                            throw new IllegalArgumentException("Two overrides for " + newName);
                        }
                        chosen = entry;
                    }
                }
                if (chosen == null) {
                    throw new IllegalArgumentException(
                            "No entries for " + newName + ", have override set");
                }
                select(zipSources, chosen, newName);
            }
        }

        // Save
        try (ZipArchive zip = new ZipArchive(outFile)) {
            for (ZipSource zipsrc : zipSources.values()) {
                if (ensureRW) {
                    for (Source source : zipsrc.getSelectedEntries()) {
                        if ((source.getVersionMadeBy() & 0xFF00) != Source.MADE_BY_UNIX) {
                            // Only adjust UNIX entries
                            continue;
                        }
                        // Make sure entries are owner RW
                        int attr = source.getExternalAttributes();
                        source.setExternalAttributes(attr | Source.PERMISSION_USR_RW);
                    }
                }
                zip.add(zipsrc);
            }
        }
    }

    private static void createZip(Path zip, Path file, String entry, int level) throws IOException {
        Files.deleteIfExists(zip);
        try (ZipArchive a = new ZipArchive(zip)) {
            a.add(new BytesSource(file, entry, level));
        }
    }

    private static ZipMap extract(ZipMap map, String entry) throws IOException {
        Path o = File.createTempFile(new File(entry).getName(), ".tmp").toPath();
        try (ZipRepo archive = new ZipRepo(map);
                OutputStream fos = Files.newOutputStream(o)) {
            ByteStreams.copy(archive.getInputStream(entry), fos);
        }
        return ZipMap.from(o);
    }

    private static void select(Map<ZipFile, ZipSource> zipSources, Entry entry, String newName) {
        ZipSource zipSource = zipSources.computeIfAbsent(entry.zip, zip -> new ZipSource(zip.map));
        zipSource.select(entry.entry, newName);
    }

    private static void printUsage() {
        System.out.println("zip_merger is a zipper tool that supports merging zips.");
        System.out.println("Usage:");
        System.out.println("   zip_merger cC <out> zip1 zip2 zip3 ...");
        System.out.println("Args:");
        System.out.println("     Args format: [path=][+]zip_file where:");
        System.out.println("  - [path] Prefix to be added to each entry name in this zip.");
        System.out.println("  - [+] Allows entries in this zip to overwrite destination entries.");
    }
}
