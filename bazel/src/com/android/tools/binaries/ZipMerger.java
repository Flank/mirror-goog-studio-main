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
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipSource;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
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
        List<Action> overrides = new ArrayList<>();
        List<Action> adds = new ArrayList<>();
        for (String arg : expanded) {
            if (arg.startsWith("#")) {
                String[] spec = arg.substring(1).split("=");
                if (spec.length != 2) {
                    printUsage();
                    return;
                }
                String[] paths = spec[0].split("!", 2);
                if (paths.length != 2) {
                    printUsage();
                    return;
                }
                Action action = new Action();
                action.entry = paths[0];
                action.subEntry = paths[1];
                action.data = new File(spec[1]);
                action.type = Type.OVERRIDE;
                overrides.add(action);
            } else {
                Action action = new Action();
                action.entry = "";
                action.type = Type.ADD;

                String dataPath = arg;
                String[] parts = arg.split("=", 2);
                if (parts.length == 2) {
                    action.entry = parts[0];
                    dataPath = parts[1];
                }
                if (!dataPath.isEmpty() && dataPath.charAt(0) == '+') {
                    action.type = Type.EXTRACT;
                    dataPath = dataPath.substring(1);
                }
                action.data = new File(dataPath);
                adds.add(action);
            }
        }

        createZip(out, adds, overrides, level);
    }

    private enum Type {
        ADD, // Adds a file to the target zip
        EXTRACT, // Extracts a zip file into the target zip
        OVERRIDE, // Overrides a nested file in the target zip
    }

    private static class Action {
        String entry;
        String subEntry;
        File data;
        Type type;
    }

    private static void createZip(
            String out, List<Action> adds, List<Action> overridesList, int level) throws Exception {
        File outFile = new File(out);
        if (outFile.exists()) {
            outFile.delete();
        }
        Map<String, List<Action>> overrides = new HashMap<>();
        for (Action action : overridesList) {
            List<Action> list = overrides.computeIfAbsent(action.entry, k -> new ArrayList<>());
            list.add(action);
        }
        try (ZipArchive zip = new ZipArchive(outFile)) {
            for (Action add : adds) {
                if (add.type == Type.ADD) {
                    File file = add.data;
                    if (overrides.containsKey(add.entry)) {
                        file = patchArchive(overrides, add.entry, file, level);
                    }
                    zip.add(new BytesSource(file, add.entry, level));
                } else if (add.type == Type.EXTRACT){
                    List<String> skipped =
                            addZipFileToArchive(add.entry, add.data, zip, overrides::containsKey);
                    try (ZipArchive archive = new ZipArchive(add.data)) {
                        for (String oldName : skipped) {
                            String newName = add.entry + oldName;
                            ByteBuffer content = archive.getContent(oldName);
                            File in = File.createTempFile("before", ".jar");
                            try (FileOutputStream fos = new FileOutputStream(in)) {
                                byte[] array = new byte[content.remaining()];
                                content.get(array);
                                fos.write(array);
                            }
                            in.deleteOnExit();
                            File o = patchArchive(overrides, newName, in, level);
                            zip.add(new BytesSource(o, newName, level));
                        }
                    }
                }
            }
        }
    }

    private static File patchArchive(
            Map<String, List<Action>> overrides, String newName, File in, int level)
            throws Exception {
        File o = File.createTempFile("after", ".jar");
        o.delete();
        List<Action> actions = overrides.get(newName);
        Map<String, File> filesToOverride = new LinkedHashMap<>();
        for (Action action : actions) {
            filesToOverride.put(action.subEntry, action.data);
        }
        try (ZipArchive patched = new ZipArchive(o)) {
            addZipFileToArchive("", in, patched, filesToOverride.keySet()::contains);
            for (Map.Entry<String, File> e : filesToOverride.entrySet()) {
                patched.add(new BytesSource(e.getValue(), e.getKey(), level));
            }
        }
        o.deleteOnExit();
        return o;
    }

    private static List<String> addZipFileToArchive(
            String prefix, File from, ZipArchive to, Predicate<String> skip) throws Exception {
        List<String> skipped = new ArrayList<>();
        ZipSource zipsrc = new ZipSource(from);
        for (String s : zipsrc.entries().keySet()) {
            String newName = prefix + s;
            if (!skip.test(newName)) {
                zipsrc.select(s, newName);
            } else {
                skipped.add(s);
            }
        }
        to.add(zipsrc);
        return skipped;
    }

    private static void printUsage() {
        System.out.println("zip_merger is a zipper tool that supports merging zips.");
        System.out.println("Usage:");
        System.out.println("   zip_merger c <out> arg1 arg2 @arg_file");
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
        System.out.println("     #<path1>!<path2>=file: Allows to override a file inside");
        System.out.println("                            another zip file. <path1> refers to the");
        System.out.println("                            path of a zip file in the final zip.");
        System.out.println("                            <path2> refers to a path in that file.");
        System.out.println("                            and <file> points to a file whose content");
        System.out.println("                            will be used instead.");
        System.out.println("Example:");
        System.out.println("    zip_merger c a.zip @args");
        System.out.println("where args is:");
        System.out.println("dir/file.txt");
        System.out.println("dir/file.jar");
        System.out.println("dir/file2.jar=/local/path/to/file2.jar");
        System.out.println("+/local/path/to/file.zip");
        System.out.println("other_path/=+/local/path/to/file.zip");
        System.out.println("#dir/file.jar!rewrite.txt=/local/file/to/rewrite.txt");
    }
}
