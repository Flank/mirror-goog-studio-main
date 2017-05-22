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
import com.android.annotations.Nullable;
import com.android.tools.apk.analyzer.ArchiveNode;
import java.nio.file.Path;

public class ApkDiffEntry implements ApkEntry {
    @NonNull private final String myName;
    @Nullable private final ArchiveNode myOldFile;
    @Nullable private final ArchiveNode myNewFile;
    private final long myOldSize;
    private final long myNewSize;

    ApkDiffEntry(
            @NonNull String name,
            @Nullable ArchiveNode oldFile,
            @Nullable ArchiveNode newFile,
            long oldSize,
            long newSize) {
        if (oldFile == null && newFile == null) {
            throw new IllegalArgumentException("Both files can't be null");
        }
        this.myName = name;
        this.myOldFile = oldFile;
        this.myNewFile = newFile;
        this.myOldSize = oldSize;
        this.myNewSize = newSize;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public Path getPath() {
        assert myOldFile != null || myNewFile != null;
        return myOldFile != null ? myOldFile.getData().getPath() : myNewFile.getData().getPath();
    }

    @Override
    public long getSize() {
        return myNewSize - myOldSize;
    }

    public long getOldSize() {
        return myOldSize;
    }

    public long getNewSize() {
        return myNewSize;
    }

    public static long getOldSize(@NonNull ApkEntry apkEntry) {
        if (apkEntry instanceof ApkDiffEntry) {
            return ((ApkDiffEntry) apkEntry).getOldSize();
        }
        return apkEntry.getSize();
    }

    public static long getNewSize(@NonNull ApkEntry apkEntry) {
        if (apkEntry instanceof ApkDiffEntry) {
            return ((ApkDiffEntry) apkEntry).getNewSize();
        }
        return apkEntry.getSize();
    }

    @Override
    public String toString() {
        return getName();
    }
}
