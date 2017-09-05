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
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Utility methods for {@link java.nio.file.Path}. */
public final class PathUtils {

    private PathUtils() {}

    public static void deleteIfExists(@NonNull Path path) throws IOException {
        if (!java.nio.file.Files.exists(path)) {
            return;
        }

        java.nio.file.Files.walkFileTree(
                path,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        java.nio.file.Files.deleteIfExists(file);
                        return super.visitFile(file, attrs);
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        java.nio.file.Files.deleteIfExists(dir);
                        return super.postVisitDirectory(dir, exc);
                    }
                });
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

    private static void addRemovePathHook(@NonNull Path path) {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        PathUtils.deleteIfExists(path);
                                    } catch (IOException e) {
                                        Logger.getLogger(PathUtils.class.getName())
                                                .log(Level.WARNING, "Unable to delete " + path, e);
                                    }
                                }));
    }
}
