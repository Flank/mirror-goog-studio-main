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
import com.android.apkzlib.zip.StoredEntry;
import com.android.apkzlib.zip.ZFile;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Dex archive that is stored in a .jar file. All dex entries, {@link DexArchiveEntry}, are stored
 * in this file.
 */
final class JarDexArchive implements DexArchive {

    @NonNull private final ZFile zFile;

    public JarDexArchive(@NonNull ZFile zFile) {
        this.zFile = zFile;
    }

    @NonNull
    @Override
    public Path getRootPath() {
        return zFile.getFile().toPath();
    }

    @Override
    public void addFile(@NonNull Path relativePath, @NonNull InputStream inputStream)
            throws IOException {
        zFile.add(relativePath.toString(), inputStream);
    }

    @Override
    public void addFile(@NonNull Path relativePath, byte[] bytes, int offset, int end)
            throws IOException {
        zFile.add(relativePath.toString(), new ByteArrayInputStream(Arrays.copyOf(bytes, end)));
    }

    @Override
    public void removeFile(@NonNull Path relativePath) throws IOException {
        StoredEntry entry = zFile.get(relativePath.toString());
        if (entry != null) {
            entry.delete();
        }
    }

    @Override
    @NonNull
    public List<DexArchiveEntry> getFiles() throws IOException {
        ImmutableList.Builder<DexArchiveEntry> builder = ImmutableList.builder();

        for (StoredEntry entry : zFile.entries()) {
            byte[] content = entry.read();
            String relativePath = entry.getCentralDirectoryHeader().getName();

            builder.add(new DexArchiveEntry(content, Paths.get(relativePath)));
        }

        return builder.build();
    }

    @Override
    public void close() throws IOException {
        zFile.close();
    }
}
