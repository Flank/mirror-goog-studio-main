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

package com.android.build.gradle.integration.common.utils;

import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

/**
 * Utility for sanity checking protos that are uploaded.
 */
public class ProfileViewer {

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length != 1) {
            System.err.println("Usage: ProfileViewer <proto_file>");
            System.exit(1);
        }
        listProtoContent(args[0]);
    }

    private static void listProtoContent(String filename) throws IOException {
        // This logic allows specifying wildcards and ~ in paths w/o using the shell
        // (so it can be launched from within the IDE).
        Path pattern = new File(filename.replace("~", System.getProperty("user.home"))).toPath();
        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(pattern.getParent(), pattern.getFileName().toString())) {
            listProtoContent(stream);
        }
    }

    private static void listProtoContent(DirectoryStream<Path> stream) throws IOException {
        for (Path path : stream) {
            if (Files.isDirectory(path)) {
                listProtoContent(Files.newDirectoryStream(path));
            } else {

                System.out.println("================================================");
                System.out.println(path.toString());
                System.out.println("================================================");

                Logging.GradleBenchmarkResult result =
                        Logging.GradleBenchmarkResult.parseFrom(Files.readAllBytes(path));

                System.out.println("Benchmark:      " + result.getBenchmark());
                System.out.println("Benchmark Mode: " + result.getBenchmarkMode());
                System.out.println("Timestamp:      " + Timestamps.toString(result.getTimestamp()));
                System.out.println("User:           " + result.getUsername());
                System.out.println("Host:           " + result.getHostname());
                System.out.println("Memory sample:  " + result.getProfile().getMemorySample(0));

                System.out.println("================================================");
                System.out.println();
                System.out.println();
                System.out.println();
            }
        }
    }
}
