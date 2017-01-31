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
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

/**
 * Interface describing the DEX archive. It contains one DEX file per .class file that was processed
 * i.e. each DEX file in it, contains definition of exactly one class. Also, the directory structure
 * is preserved e.g. if a file com/example/tools/A.class was processed, it will have relative path
 * com/example/tools/A.dex in the archive.
 *
 * <p>Dex archives can be updated by adding new files, or removing existing ones.
 *
 * <p>When using instances of {@link DexArchive} make sure that you invoke {@link #close()} after
 * you are done using it.
 */
public interface DexArchive extends Closeable {

    /**
     * Returns the path to this DEX archive.
     *
     * @return path to this archive
     */
    @NonNull
    Path getRootPath();

    /**
     * Adds a DEX file to this dex archive. Adding is performed relative to the {@link
     * #getRootPath()}. In case file with the same relative path already exists, implementations of
     * this method should overwrite it with the new version of the file.
     *
     * @param relativePath file to be added to this archive
     * @param inputStream data to write
     */
    void addFile(@NonNull Path relativePath, @NonNull InputStream inputStream) throws IOException;

    /**
     * Removes the specified DEX file from the dex archive. In case the file does not exist, nothing
     * is done.
     *
     * @param relativePath file to be removed from the dex archive, relative to the {@link
     *     #getRootPath()}
     */
    void removeFile(@NonNull Path relativePath) throws IOException;

    /**
     * Returns collection of all entries, {@link DexArchiveEntry}, in this dex archive. The entries
     * contain information about the relative path, {@link
     * DexArchiveEntry#getRelativePathInArchive()} as well as the actual DEX file content, {@link
     * DexArchiveEntry#getDexFileContent()}.
     *
     * @return all entries of the archive
     */
    @NonNull
    Set<DexArchiveEntry> getFiles() throws IOException;
}
