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

package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import java.nio.file.Path;

public abstract class ArchiveEntry {
    /** The archive containing this entry. */
    @NonNull private final Archive archive;
    /**
     * This is the relative path in the archive. For inner archive root nodes, it's the relative
     * path in the outer archive. For inner archive non-root nodes, it's the relative path in the
     * inner archive.
     */
    @NonNull private final Path path;
    /**
     * This is an arbitrary path prefix string used to build the display path returned by the {@link
     * #getSummaryDisplayString()} method. The string can be empty, in which case {@link
     * #getSummaryDisplayString()} returns the same string as <code>{@link #getPath()}.toString()
     * </code>.
     */
    @NonNull private final String pathPrefix;

    public ArchiveEntry(@NonNull Archive archive, @NonNull Path path, @NonNull String pathPrefix) {
        assert archive.getContentRoot().getFileSystem() == path.getFileSystem();

        this.archive = archive;
        this.path = path;
        this.pathPrefix = pathPrefix;
    }

    public void setRawFileSize(long rawFileSize) {}

    public long getRawFileSize() {
        return 0;
    }

    public void setDownloadFileSize(long downloadFileSize) {}

    public long getDownloadFileSize() {
        return 0;
    }

    @NonNull
    public Path getPath() {
        return path;
    }

    @NonNull
    public String getPathPrefix() {
        return pathPrefix;
    }

    @NonNull
    public Archive getArchive() {
        return archive;
    }

    /**
     * Returns a short description string that can be used when displaying this entry in the context
     * of its parent entry, for example the filename, without the parent path, for an entry
     * corresponding to a file.
     */
    @NonNull
    public abstract String getNodeDisplayString();

    /**
     * Returns a description string that summarizes the content of this entry and its parent(s), for
     * example a full path if the entry corresponding to a file.
     */
    @NonNull
    public abstract String getSummaryDisplayString();

    @Override
    public String toString() {
        return String.format(
                "%s: pathPrefix=\"%s\", path=\"%s\"", getClass().getSimpleName(), pathPrefix, path);
    }
}
