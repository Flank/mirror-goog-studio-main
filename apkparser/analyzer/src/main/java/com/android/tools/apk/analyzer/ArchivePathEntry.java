/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import java.nio.file.Path;

public class ArchivePathEntry extends ArchiveEntry {
    private long rawFileSize = -1;
    private long downloadFileSize = -1;

    public ArchivePathEntry(
            @NonNull Archive archive, @NonNull Path path, @NonNull String pathPrefix) {
        super(archive, path, pathPrefix);
    }

    @Override
    public void setRawFileSize(long rawFileSize) {
        this.rawFileSize = rawFileSize;
    }

    @Override
    public long getRawFileSize() {
        return rawFileSize;
    }

    @Override
    public void setDownloadFileSize(long downloadFileSize) {
        this.downloadFileSize = downloadFileSize;
    }

    @Override
    public long getDownloadFileSize() {
        return downloadFileSize;
    }

    @Override
    @NonNull
    public String getNodeDisplayString() {
        Path base = getPath().getFileName();
        String name = base == null ? "" : base.toString();
        return trimEnd(name, "/");
    }

    @Override
    @NonNull
    public String getSummaryDisplayString() {
        return getPathPrefix() + getPath().toString();
    }

    @NonNull
    private static String trimEnd(@NonNull String s, @NonNull String suffix) {
        boolean endsWith = s.endsWith(suffix);
        if (endsWith) {
            return s.substring(0, s.length() - suffix.length());
        }
        return s;
    }
}
