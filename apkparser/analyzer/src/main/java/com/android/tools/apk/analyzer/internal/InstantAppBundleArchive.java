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

package com.android.tools.apk.analyzer.internal;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.Archive;
import com.android.utils.FileUtils;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Implementation of {@link Archive} for an Instant App bundle zip file.
 *
 * <p>The contents of the <code>zip</code> file (i.e. <code>APK</code> files) are extracted into a
 * temporary directory. The {@link #close()} method deletes this temporary directory.
 */
public class InstantAppBundleArchive implements Archive {
    @NonNull private final Path bundleFilePath;
    @NonNull private final Path extractedFilesPath;

    private InstantAppBundleArchive(@NonNull Path bundleFilePath) throws IOException {
        this.bundleFilePath = bundleFilePath;
        this.extractedFilesPath =
                Files.createTempDirectory(bundleFilePath.getFileName().toString());

        // For zip archives (which are AIA bundles), we unzip the outer zip contents to a temp folder
        // so that we show accurate file sizes for the top-level APKs in the ZIP file.
        extractArchiveContents(bundleFilePath);
    }

    private void extractArchiveContents(@NonNull Path artifact) throws IOException {
        try (FileSystem fileSystem = FileUtils.createZipFilesystem(artifact)) {
            Files.walkFileTree(
                    fileSystem.getPath("/"),
                    new CopyPathFileVisitor(fileSystem, extractedFilesPath));
        }
    }

    @NonNull
    public static InstantAppBundleArchive fromZippedBundle(@NonNull Path artifact)
            throws IOException {
        return new InstantAppBundleArchive(artifact);
    }

    @NonNull
    @Override
    public Path getPath() {
        return bundleFilePath;
    }

    @Override
    @NonNull
    public Path getContentRoot() {
        return extractedFilesPath;
    }

    @Override
    public void close() throws IOException {
        FileUtils.deletePath(extractedFilesPath.toFile());
    }

    @Override
    public boolean isBinaryXml(@NonNull Path p, @NonNull byte[] content) {
        return false;
    }

    @Override
    public boolean isProtoXml(@NonNull Path p, @NonNull byte[] content) {
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s: path=\"%s\"", getClass().getSimpleName(), bundleFilePath);
    }

    private static class CopyPathFileVisitor implements FileVisitor<Path> {
        private final Path source;
        private final Path destination;

        public CopyPathFileVisitor(@NonNull FileSystem source, @NonNull Path destination) {
            this.source = source.getPath("/");
            this.destination = destination;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
            Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.copy(file, destination.resolve(source.relativize(file).toString()));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }
}
