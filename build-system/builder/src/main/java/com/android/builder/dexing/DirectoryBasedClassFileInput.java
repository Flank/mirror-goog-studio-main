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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DirectoryBasedClassFileInput implements ClassFileInput {

    @NonNull private final Path rootPath;

    public DirectoryBasedClassFileInput(@NonNull Path rootPath) {
        this.rootPath = rootPath;
    }

    @NonNull
    @Override
    public Path getRootPath() {
        return rootPath;
    }

    @Override
    public void close() throws IOException {
        // nothing to do for folders.
    }

    public static class FileBasedClassFile implements ClassFileEntry {

        final Path relativePath;
        private final Path path;

        public FileBasedClassFile(@NonNull Path relativePath, @NonNull Path path) {
            this.relativePath = relativePath;
            this.path = path;
        }

        @Override
        public String name() {
            return path.getFileName().toString();
        }

        @Override
        public long getSize() throws IOException {
            return Files.size(path);
        }

        @Override
        public Path getRelativePath() {
            return relativePath;
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public int readAllBytes(byte[] bytes) throws IOException {
            try (SeekableByteChannel sbc = Files.newByteChannel(path);
                    InputStream in = Channels.newInputStream(sbc)) {
                long size = sbc.size();
                if (size > bytes.length)
                    throw new OutOfMemoryError("Required array size too large");

                return in.read(bytes, 0, (int) size);
            }
        }
    }

    @Override
    @NonNull
    public Stream<ClassFileEntry> entries(Predicate<Path> filter) throws IOException {
        return Files.walk(rootPath)
                .filter(((Predicate<Path>) classMatcher::matches).and(filter))
                .map(this::createEntryFromPath);
    }

    @NonNull
    public ClassFileEntry createEntryFromPath(@NonNull Path path) {
        return new FileBasedClassFile(rootPath.relativize(path), path);
    }
}
