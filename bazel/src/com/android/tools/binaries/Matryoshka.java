/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * A tool that append files to the end of an already linked executable that would allow that
 * executable to extract that content itself.
 *
 * <p>Unlike other data embedding techniques such as inserting as part of the .RODATA, the data
 * needs not to be available in the compile or link time of the build. Instead, an already linked
 * executable is "expanded" by attaching extract payload at the end of the file.
 *
 * <p>At runtime, the executable looks up the available files by filename by using function in
 * helper.h
 */
public class Matryoshka {

    // This is a high level of what the output looks like.
    // The length of each section varies in size unless it is specified.

    // +-------------------------+
    // +                         +
    // +     Original Input      +
    // +                         +
    // +-------------------------+
    // +                         +
    // +   Payload #2 Content    +
    // +                         +
    // +-------------------------+
    // +   wc -c payload2.out    + (32-bit)
    // +-------------------------+
    // +     "payload2.out"      +
    // +-------------------------+
    // + "payload2.out".length() + (32-bit)
    // +-------------------------+
    // +                         +
    // +   Payload #1 Content    +
    // +                         +
    // +-------------------------+
    // +   wc -c payload1.out    + (32-bit)
    // +-------------------------+
    // +     "payload1.out"      +
    // +-------------------------+
    // + "payload2.out".length() +
    // +-------------------------+
    // +   Total # of payloads   + (32-bit)
    // +-------------------------+
    // +       MAGIC_NUMBER      + (32-bit)
    // +-------------------------+

    // echo "Self-Extracting Since 2018" | md5sum | cut -c-8
    private static final int MAGIC_NUMBER = 0xd1d50655;

    // TreeMap for determinism since it is a build system tool.
    // The payload will be sorted alphabetically by the file
    // name in the output.
    private static Map<String, File> payloads = new TreeMap<>();

    private static File input = null;
    private static File output = null;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            help();
            return;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-i":
                    input = new File(args[++i]);
                    if (!input.exists()) {
                        fail("File " + input.getAbsolutePath() + " does not exist!");
                    }
                    continue;
                case "-o":
                    output = new File(args[++i]);
                    continue;
                case "-p":
                    String pair = args[++i];
                    int split = pair.indexOf('=');
                    File payload = new File(pair.substring(split + 1));
                    if (!payload.exists()) {
                        fail("File " + payload.getAbsolutePath() + " does not exist!");
                    }
                    payloads.put(pair.substring(0, split), payload);
                    continue;
                case "-h":
                    help();
                    return;
                default:
                    help();
                    fail("unknown flag :" + arg);
            }
        }

        if (input == null) {
            fail("No input file given");
        }

        if (output == null) {
            fail("No input file given");
        }

        if (payloads.isEmpty()) {
            fail("No payload");
        }

        write();
    }

    private static void write() throws IOException {
        File tempFile = File.createTempFile("exec-", "-tmp");
        Files.write(
                tempFile.toPath(), Files.readAllBytes(input.toPath()), StandardOpenOption.APPEND);
        for (Map.Entry<String, File> payload : payloads.entrySet()) {
            byte[] content = Files.readAllBytes(payload.getValue().toPath());
            Files.write(tempFile.toPath(), content, StandardOpenOption.APPEND);
            Files.write(tempFile.toPath(), getInteger(content.length), StandardOpenOption.APPEND);
            Files.write(tempFile.toPath(), getString(payload.getKey()), StandardOpenOption.APPEND);
        }
        Files.write(tempFile.toPath(), getInteger(payloads.size()), StandardOpenOption.APPEND);
        Files.write(tempFile.toPath(), getInteger(MAGIC_NUMBER), StandardOpenOption.APPEND);
        Files.copy(tempFile.toPath(), output.toPath());
        tempFile.deleteOnExit();
    }

    private static byte[] getInteger(int i) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array();
    }

    private static byte[] getString(String value) {
        ByteBuffer buffer = ByteBuffer.allocate(value.length() + 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < value.length(); i++) {
            // ASCII only.
            buffer.put((byte) value.charAt(i));
        }
        buffer.putInt(value.length());
        return buffer.array();
    }

    private static void help() {
        System.out.println("Usage matryoshka [parameters]");
        System.err.println("List of parameters:");
        System.err.println("    -i filename        : Name of the input file. (required)");
        System.err.println("    -o filename        : Name of the output file. (required)");
        System.err.println("    -E                 : Use big endian");
        System.err.println("    -e                 : Use little endian (default)");
        System.err.println(
                "    -p filename=data   : Embeds the content of 'data' within the input");
        System.err.println(
                "                         The executable can use 'filename' to locate the content");
        System.err.println(
                "                         (Mulitple -p can be used but at least one is required)");
    }

    private static void fail(String reason) {
        throw new RuntimeException(reason);
    }
}
