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
import com.android.annotations.Nullable;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Implementation of the {@link DexArchive} that does not support incremental updates. */
final class NonIncrementalJarDexArchive implements DexArchive {

    private static final FileTime ZERO_TIME = FileTime.fromMillis(0);

    private final Path targetPath;
    @Nullable private JarOutputStream jarOutputStream;
    @Nullable private ZipFile readOnlyZipFile;

    public NonIncrementalJarDexArchive(Path targetPath) throws IOException {
        this.targetPath = targetPath;
        if (Files.isRegularFile(targetPath)) {
            // we should read this file
            this.readOnlyZipFile = new ZipFile(targetPath.toFile());
        } else {
            // we are creating this file
            this.jarOutputStream =
                    new JarOutputStream(
                            new BufferedOutputStream(
                                    Files.newOutputStream(
                                            targetPath,
                                            StandardOpenOption.WRITE,
                                            StandardOpenOption.CREATE_NEW)));
        }
    }

    @NonNull
    @Override
    public Path getRootPath() {
        return targetPath;
    }

    @Override
    public void addFile(@NonNull Path relativePath, byte[] bytes, int offset, int end)
            throws IOException {
        Preconditions.checkNotNull(jarOutputStream, "Archive is not writeable.");
        // Need to pre-compute checksum for STORED (uncompressed) entries)
        CRC32 checksum = new CRC32();
        checksum.update(bytes, offset, end);

        ZipEntry zipEntry = new ZipEntry(PathUtils.toSystemIndependentPath(relativePath));
        zipEntry.setLastModifiedTime(ZERO_TIME);
        zipEntry.setLastAccessTime(ZERO_TIME);
        zipEntry.setCreationTime(ZERO_TIME);
        zipEntry.setCrc(checksum.getValue());
        zipEntry.setSize(end - offset);
        zipEntry.setCompressedSize(end - offset);
        zipEntry.setMethod(ZipEntry.STORED);

        jarOutputStream.putNextEntry(zipEntry);
        jarOutputStream.write(bytes, offset, end);
        jarOutputStream.flush();
        jarOutputStream.closeEntry();
    }

    @Override
    public void removeFile(@NonNull Path relativePath) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    @Override
    public List<DexArchiveEntry> getFiles() throws IOException {
        Preconditions.checkNotNull(readOnlyZipFile, "Archive is not readable.");
        List<DexArchiveEntry> dexEntries = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = readOnlyZipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            try (BufferedInputStream inputStream =
                    new BufferedInputStream(readOnlyZipFile.getInputStream(zipEntry))) {
                byte[] content = ByteStreams.toByteArray(inputStream);
                dexEntries.add(new DexArchiveEntry(content, Paths.get(zipEntry.getName())));
            }
        }
        return dexEntries;
    }

    @Override
    public void close() throws IOException {
        if (jarOutputStream != null) {
            jarOutputStream.flush();
            jarOutputStream.close();
        } else if (readOnlyZipFile != null) {
            readOnlyZipFile.close();
        } else {
            throw new IllegalStateException("Dex archive is neither readable nor writable.");
        }
    }
}
