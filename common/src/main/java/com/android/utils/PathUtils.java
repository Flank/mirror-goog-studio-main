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

import com.android.annotations.NonNull;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

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
}
