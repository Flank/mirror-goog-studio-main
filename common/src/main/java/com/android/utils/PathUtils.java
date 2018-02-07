/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.utils;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Utility methods for {@link java.nio.file.Path}. */
public final class PathUtils {

    private PathUtils() {}

    /**
     * Deletes a file or a directory if it exists. If the directory is not empty, its contents will
     * be deleted recursively.
     *
     * @param path the file or directory to delete. The file/directory may not exist; if the
     *     directory exists, it may be non-empty.
     */
    public static void deleteRecursivelyIfExists(@NonNull Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> pathsInDir = Files.list(path)) {
                pathsInDir.forEach(
                        pathInDir -> {
                            try {
                                deleteRecursivelyIfExists(pathInDir);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }
        }

        Files.deleteIfExists(path);
    }

    /** Returns a system-independent path. */
    @NonNull
    public static String toSystemIndependentPath(@NonNull Path path) {
        String filePath = path.toString();
        if (!path.getFileSystem().getSeparator().equals("/")) {
            return filePath.replace(path.getFileSystem().getSeparator(), "/");
        }
        return filePath;
    }

    @NonNull
    public static Path createTmpToRemoveOnShutdown(@NonNull String prefix) throws IOException {
        Path tmpFile = Files.createTempFile(prefix, "");
        addRemovePathHook(tmpFile);
        return tmpFile;
    }

    @NonNull
    public static Path createTmpDirToRemoveOnShutdown(@NonNull String prefix) throws IOException {
        Path tmpDir = Files.createTempDirectory(prefix);
        addRemovePathHook(tmpDir);
        return tmpDir;
    }

    @NonNull
    public static List<Path> getClassPathItems(@NonNull String classPath) {
        Iterable<String> components = Splitter.on(File.pathSeparator).split(classPath);

        List<Path> classPathJars = Lists.newArrayList();
        PathMatcher zipOrJar =
                FileSystems.getDefault()
                        .getPathMatcher(
                                String.format(
                                        "glob:**{%s,%s}",
                                        SdkConstants.EXT_ZIP, SdkConstants.EXT_JAR));

        for (String component : components) {
            Path componentPath = Paths.get(component);
            if (Files.isRegularFile(componentPath)) {
                classPathJars.add(componentPath);
            } else {
                // this is a directory containing zips or jars, get them all
                try {
                    Files.walk(componentPath).filter(zipOrJar::matches).forEach(classPathJars::add);
                } catch (IOException ignored) {
                    // just ignore, users can specify non-existing dirs as class path
                }
            }
        }

        return classPathJars;
    }

    /**
     * Adds a hook to the shutdown event of the JVM which will delete all files and directories at
     * the given path (inclusive) when the JVM exits.
     *
     * @param path the path to delete
     */
    public static void addRemovePathHook(@NonNull Path path) {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        PathUtils.deleteRecursivelyIfExists(path);
                                    } catch (IOException e) {
                                        Logger.getLogger(PathUtils.class.getName())
                                                .log(Level.WARNING, "Unable to delete " + path, e);
                                    }
                                }));
    }
}
