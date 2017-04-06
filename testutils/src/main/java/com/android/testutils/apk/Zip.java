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

package com.android.testutils.apk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.google.common.base.Preconditions;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Immutable
public class Zip implements AutoCloseable {

    @NonNull final String displayName;
    @NonNull private final Path file;
    @NonNull private final FileSystem zip;
    private final boolean exists;

    // Cache for opening inner zip files.
    @NonNull private final Map<String, Zip> innerZips;

    public Zip(@NonNull Path file) throws IOException {
        this(file, file.toString());
    }

    public Zip(@NonNull File file) throws IOException {
        this(file.toPath());
    }

    private Zip(@NonNull Path file, @NonNull String displayName) throws IOException {
        this.file = file;
        this.displayName = displayName;
        if (!Files.isRegularFile(file)) { // Empty zip.
            this.exists = false;
            this.zip = Jimfs.newFileSystem(Configuration.unix());
        } else if (file.getFileSystem() != FileSystems.getDefault()) {
            throw new IllegalArgumentException(
                    "Cannot create zip from non default fs, use getEntryAsZip() instead");
        } else {
            this.exists = true;
            this.zip = FileSystems.newFileSystem(file, null);
        }
        this.innerZips = new HashMap<>();
    }

    @NonNull
    public final Path getFile() {
        return file;
    }

    /* Returns a zip entry given a name, returns null if it does not exist */
    @Nullable
    public final Path getEntry(String name) {
        Path entry = zip.getPath(name);
        return Files.exists(entry) ? entry : null;
    }

    @NonNull
    public final List<Path> getEntries() throws IOException {
        return Collections.unmodifiableList(
                Files.walk(zip.getPath("/"))
                        .filter(path -> Files.isRegularFile(path))
                        .collect(Collectors.toList()));
    }

    @NonNull
    public final List<Path> getEntries(@NonNull Pattern pattern) throws IOException {
        return Collections.unmodifiableList(
                Files.walk(zip.getPath("/"))
                        .filter(path -> Files.isRegularFile(path))
                        .filter(path -> pattern.matcher(path.toString()).matches())
                        .collect(Collectors.toList()));
    }

    @NonNull
    public final Zip getEntryAsZip(@NonNull String name) throws IOException {
        Zip cached = innerZips.get(name);
        if (cached != null) {
            return cached;
        }

        // Extract inner zip into temporary location.
        Path zipPath = getEntry(name);
        Preconditions.checkNotNull(zipPath, "Entry %s should exist ", name);
        Path temp = Files.createTempFile(file.getFileName().toString(), "_inner_zip.zip");
        Files.copy(zipPath, temp, StandardCopyOption.REPLACE_EXISTING);
        temp.toFile().deleteOnExit();
        Zip created = new Zip(temp, displayName + ":" + name);

        innerZips.put(name, created);
        return created;
    }

    public boolean exists() {
        return exists;
    }

    @Override
    public String toString() {
        return "Zip<" + displayName + ">";
    }

    @Override
    public void close() throws Exception {
        zip.close();
        for (Zip innerZip : innerZips.values()) {
            innerZip.close();
        }
    }
}
