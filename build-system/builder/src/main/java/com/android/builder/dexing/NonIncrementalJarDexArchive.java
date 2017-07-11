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
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/** Implementation of the {@link DexArchive} that does not support incremental updates. */
public class NonIncrementalJarDexArchive implements DexArchive {

    public static final FileTime ZERO_TIME = FileTime.fromMillis(0);

    private final Path targetPath;
    private final JarOutputStream jarOutputStream;

    public NonIncrementalJarDexArchive(Path targetPath) throws IOException {
        this.targetPath = targetPath;
        this.jarOutputStream =
                new JarOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(
                                        targetPath,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.CREATE_NEW)));
    }

    @NonNull
    @Override
    public Path getRootPath() {
        return targetPath;
    }

    @Override
    public void addFile(@NonNull Path relativePath, @NonNull InputStream inputStream)
            throws IOException {
        ZipEntry zipEntry = new ZipEntry(relativePath.toString());
        zipEntry.setMethod(ZipEntry.STORED);
        zipEntry.setLastModifiedTime(ZERO_TIME);
        zipEntry.setLastAccessTime(ZERO_TIME);
        zipEntry.setCreationTime(ZERO_TIME);
        jarOutputStream.putNextEntry(zipEntry);
        ByteStreams.copy(inputStream, jarOutputStream);
        jarOutputStream.flush();
        jarOutputStream.closeEntry();
    }

    @Override
    public void addFile(@NonNull Path relativePath, byte[] bytes, int offset, int end)
            throws IOException {

        jarOutputStream.putNextEntry(new ZipEntry(relativePath.toString()));
        jarOutputStream.write(bytes, offset, end);
        jarOutputStream.flush();
        jarOutputStream.closeEntry();
    }

    @Override
    public void removeFile(@NonNull Path relativePath) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    @Override
    public List<DexArchiveEntry> getFiles() throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void close() throws IOException {
        jarOutputStream.flush();
        jarOutputStream.close();
    }
}
