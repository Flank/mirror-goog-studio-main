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

package com.android.testutils;

import com.android.annotations.NonNull;
import com.android.testutils.filesystemdiff.Action;
import com.android.testutils.filesystemdiff.ActionExecutor;
import com.android.testutils.filesystemdiff.FileSystemEntry;
import com.android.testutils.filesystemdiff.Script;
import com.android.testutils.filesystemdiff.SymbolicLinkDefinition;
import com.android.testutils.filesystemdiff.TreeBuilder;
import com.android.testutils.filesystemdiff.TreeDifferenceEngine;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class BazelRunfilesManifestProcessor {
    public static final String RUNFILES_MANIFEST_FILE_ENV = "RUNFILES_MANIFEST_FILE";
    public static final String TEST_SRCDIR_ENV = "TEST_SRCDIR";
    public static final ILogger logger = new StdLogger(StdLogger.Level.INFO);

    private static boolean isWindows() {
        return OsType.getHostOs() == OsType.WINDOWS;
    }

    public static void setUpRunfiles() {
        if (!isWindows()) {
            return;
        }
        setUpRunfiles(System.getenv());
    }

    @VisibleForTesting
    static void setUpRunfiles(Map<String, String> env) {
        String manifestFilename = env.get(RUNFILES_MANIFEST_FILE_ENV);
        if (manifestFilename == null) {
            return;
        }

        Path testSourcePath = Paths.get(env.get(TEST_SRCDIR_ENV));
        if (testSourcePath == null) {
            return;
        }

        long startTime, endTime;
        startTime = System.nanoTime();
        Path manifestPath = Paths.get(manifestFilename).toAbsolutePath();
        List<SymbolicLinkDefinition> links = readRunfilesManifest(manifestPath, testSourcePath);
        endTime = System.nanoTime();
        logger.info("RUNFILES: Loaded runfiles manifest \"%s\" in %,d msec",
                manifestPath, (endTime - startTime) / 1_000_000);

        startTime = System.nanoTime();
        FileSystemEntry fileSystemRoot = TreeBuilder.buildFromFileSystem(testSourcePath);
        FileSystemEntry manifestRoot = TreeBuilder.buildFromSymbolicLinkDefinitions(testSourcePath, links);
        endTime = System.nanoTime();
        logger.info("RUNFILES: Traversed existing file system (%,d entries) in %,d msec",
                countEntries(fileSystemRoot), (endTime - startTime) / 1_000_000);

        startTime = System.nanoTime();
        Script script = TreeDifferenceEngine.computeEditScript(fileSystemRoot, manifestRoot);
        endTime = System.nanoTime();
        logger.info("RUNFILES: Computed file system edit script (%,d actions) in %,d msec",
                script.getActions().size(), (endTime - startTime) / 1_000_000);

        startTime = System.nanoTime();
        script.execute(logger, new ActionExecutor() {
            @Override
            public void execute(ILogger logger, Action action) {
                // Ignore operations on bazel runfiles manifest
                if (action.getSourceEntry().getPath().equals(manifestPath)) {
                    return;
                }
                super.execute(logger, action);
            }
        });
        endTime = System.nanoTime();
        logger.info("RUNFILES: Synchronized file system in %,d msec",
                (endTime - startTime) / 1_000_000);
    }

    private static int countEntries(FileSystemEntry fileSystemRoot) {
        int result = 1;
        for (FileSystemEntry child : fileSystemRoot.getChildEntries()) {
            result += countEntries(child);
        }
        return result;
    }

    @NonNull
    private static List<SymbolicLinkDefinition> readRunfilesManifest(Path manifestPath,
            Path testSourcePath) {
        List<SymbolicLinkDefinition> links = new ArrayList<>();
        try {
            try (Stream<String> stream = Files.lines(manifestPath)) {
                Iterator<String> it = stream.iterator();
                int lineNumber = 0;
                while (it.hasNext()) {
                    lineNumber++;
                    String line = it.next();
                    String[] splitLine = line.split(" "); // This is buggy when the path contains spaces
                    if (splitLine.length != 2) {
                        logger.warning("Skipping runfile line %d because the format is invalid " +
                                "(%d elements instead of exactly 2).",
                                lineNumber, splitLine.length);
                        continue;
                    }

                    links.add(new SymbolicLinkDefinition(testSourcePath.resolve(splitLine[0]), Paths
                            .get(splitLine[1])));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading bazel MANIFEST file", e);
        }
        return links;
    }
}
