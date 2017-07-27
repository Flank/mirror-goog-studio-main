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

public class ZipArtifact implements Archive {
    private final Path artifact;
    private final Path contents;

    private ZipArtifact(@NonNull Path artifact, @NonNull FileSystem zipFileSystem)
            throws IOException {
        this.artifact = artifact;
        this.contents = Files.createTempDirectory(artifact.getFileName().toString());
        //for zip archives (which are AIA bundles), we unzip the outer zip contents to a temp folder
        //so that we show accurate file sizes for the top-level APKs in the ZIP file
        Files.walkFileTree(
                zipFileSystem.getPath("/"), new CopyPathFileVisitor(contents, zipFileSystem));
    }

    @NonNull
    public static ZipArtifact fromZippedBundle(@NonNull Path artifact) throws IOException {
        try (FileSystem fileSystem = FileUtils.createZipFilesystem(artifact)) {
            return new ZipArtifact(artifact, fileSystem);
        }
    }

    @NonNull
    @Override
    public Path getPath() {
        return artifact;
    }

    @Override
    @NonNull
    public Path getContentRoot() {
        return contents;
    }

    @Override
    public void close() throws IOException {
        FileUtils.deletePath(contents.toFile());
    }

    @Override
    public boolean isBinaryXml(@NonNull Path p, @NonNull byte[] content) {
        return false;
    }

    private static class CopyPathFileVisitor implements FileVisitor<Path> {
        private final Path source;
        private final Path destination;

        public CopyPathFileVisitor(@NonNull Path destination, @NonNull FileSystem source) {
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
