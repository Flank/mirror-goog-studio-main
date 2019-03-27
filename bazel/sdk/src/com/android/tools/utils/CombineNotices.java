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

package com.android.tools.utils;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CombineNotices {
    private static void printUsage() {
        System.err.println(
                "Combines multiple notice files into one.\n"
                        + "Usage: CombineNotices <outputFile> <inputFile1> <inputFile2> ...");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length < 2 || Arrays.asList(args).contains("--help")) {
            printUsage();
        }
        Path output = Paths.get(args[0]);
        List<Path> input =
                Arrays.asList(args)
                        .subList(1, args.length)
                        .stream()
                        .map(Paths::get)
                        .collect(Collectors.toList());
        try {
            CombineNotices.run(output, input);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static void run(Path outputFile, List<Path> inputFiles)
            throws IOException, NoSuchAlgorithmException {
        if (!Files.exists(outputFile.getParent())) {
            throw new RuntimeException(
                    "Output directory " + outputFile.getParent() + " does not exist!");
        }
        Optional<Path> nonExisting = inputFiles.stream().filter(f -> !Files.exists(f)).findFirst();
        if (nonExisting.isPresent()) {
            throw new RuntimeException("Input file " + nonExisting.get() + " does not exist!");
        }

        Map<String, String> digestToLicense = new HashMap<>();
        Map<String, List<Path>> digestToFiles = new HashMap<>();
        for (Path inputFile : inputFiles) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(inputFile);
                    DigestInputStream dis = new DigestInputStream(is, md);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(dis))) {
                String license = reader.lines().collect(Collectors.joining("\n"));
                String digest = new String(md.digest());
                digestToLicense.put(digest, license);
                digestToFiles.computeIfAbsent(digest, ignored -> new ArrayList<>()).add(inputFile);
            }
        }
        try (BufferedWriter writer =
                Files.newBufferedWriter(outputFile, WRITE, TRUNCATE_EXISTING, CREATE)) {
            for (String digest : digestToLicense.keySet()) {
                writer.write("============================================================\n");
                writer.write("Notices for file(s):\n");
                for (Path file : digestToFiles.get(digest)) {
                    writer.write(file.getParent().getFileName().toString() + "\n");
                }
                writer.write("------------------------------------------------------------\n");
                writer.write(digestToLicense.get(digest));
                writer.write("\n");
            }
        }
    }
}
