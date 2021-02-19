/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.io.FileOp;
import com.android.repository.io.impl.FileOpImpl;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * Mock version of {@link FileOpImpl} that wraps some common {@link File} operations on files and
 * folders.
 *
 * <p>This version does not perform any file operation. Instead it records a textual representation
 * of all the file operations performed.
 *
 * <p>To avoid cross-platform path issues (e.g. Windows path), the methods here should always use
 * rooted (aka absolute) unix-looking paths, e.g. "/dir1/dir2/file3". When processing {@link File},
 * you can convert them using {@link #getPlatformSpecificPath(File)}.
 *
 * @deprecated Use {@link com.google.common.jimfs.Jimfs}/{@link InMemoryFileSystems} and
 *     {@code com.android.testutils.file.DelegatingFileSystemProvider} for mocking file system.
 */
@Deprecated
public class MockFileOp extends FileOp {
    private FileSystem mFileSystem;

    public MockFileOp() {
        mFileSystem = InMemoryFileSystems.createInMemoryFileSystem();
    }

    public MockFileOp(@NonNull FileSystem fileSystem) {
        mFileSystem = fileSystem;
    }

    @Override
    public FileSystem getFileSystem() {
        return mFileSystem;
    }

    /** Resets the internal state, as if the object had been newly created. */
    public void reset() {
        mFileSystem = InMemoryFileSystems.createInMemoryFileSystem();
    }

    @Override
    public void deleteOnExit(File file) {
        // nothing
    }

    @Override
    public boolean canWrite(@NonNull File file) {
        return InMemoryFileSystems.canWrite(toPath(file));
    }

    @NonNull
    public String getPlatformSpecificPath(@NonNull File file) {
        return getPlatformSpecificPath(file.getAbsolutePath());
    }

    @NonNull
    public String getPlatformSpecificPath(@NonNull String path) {
        return InMemoryFileSystems.getPlatformSpecificPath(path);
    }

    /**
     * Records a new absolute file path.
     * Parent folders are automatically created.
     */
    public void recordExistingFile(@NonNull File file) {
        recordExistingFile(file.getAbsolutePath(), 0, (byte[]) null);
    }

    public void recordExistingFile(@NonNull Path path) {
        recordExistingFile(path, 0, null);
    }

    /**
     * Records a new absolute file path.
     * Parent folders are automatically created.
     */
    public void recordExistingFile(@NonNull String absFilePath) {
        recordExistingFile(absFilePath, 0, (byte[])null);
    }

    /**
     * Records a new absolute file path and its input stream content. Parent folders are
     * automatically created.
     *
     * @param absFilePath The file path, e.g. "/dir/file" (any platform) or "c:\dir\file" (windows)
     * @param inputStream A non-null byte array of content to return via {@link
     *     #newFileInputStream(File)}.
     */
    public void recordExistingFile(@NonNull String absFilePath, @Nullable byte[] inputStream) {
        recordExistingFile(absFilePath, 0, inputStream);
    }

    /**
     * Records a new absolute file path and its input stream content. Parent folders are
     * automatically created.
     *
     * @param absFilePath The file path, e.g. "/dir/file" (any platform) or "c:\dir\file" (windows)
     * @param content A non-null UTF-8 content string to return via {@link
     *     #newFileInputStream(File)}.
     */
    public void recordExistingFile(@NonNull String absFilePath, @NonNull String content) {
        recordExistingFile(absFilePath, 0, content.getBytes(Charsets.UTF_8));
    }

    /**
     * Records a new absolute file path and its input stream content. Parent folders are
     * automatically created.
     *
     * @param absFilePath The file path, e.g. "/dir/file" (any platform) or "c:\dir\file" (windows)
     * @param inputStream A non-null byte array of content to return via {@link
     *     #newFileInputStream(File)}.
     */
    public void recordExistingFile(
            @NonNull String absFilePath, long lastModified, @Nullable byte[] inputStream) {
        recordExistingFile(
                mFileSystem.getPath(getPlatformSpecificPath(absFilePath)),
                lastModified,
                inputStream);
    }

    public void recordExistingFile(
            @NonNull Path path, long lastModified, @Nullable byte[] inputStream) {
        assert path.getFileSystem().equals(mFileSystem);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path,
              inputStream == null ? new byte[0] : inputStream);
            Files.setLastModifiedTime(path, FileTime.fromMillis(lastModified));
        } catch (IOException e) {
            assert false : e.getMessage();
        }
    }

    /**
     * Records a new absolute file path and its input stream content. Parent folders are
     * automatically created.
     *
     * @param absFilePath The file path, e.g. "/dir/file" (any platform) or "c:\dir\file" (windows)
     * @param content A non-null UTF-8 content string to return via {@link
     *     #newFileInputStream(File)}.
     */
    public void recordExistingFile(
            @NonNull String absFilePath, long lastModified, @NonNull String content) {
        recordExistingFile(absFilePath, lastModified, content.getBytes(Charsets.UTF_8));
    }

    /**
     * Records a new absolute folder path.
     * Parent folders are automatically created.
     */
    public void recordExistingFolder(File folder) {
        recordExistingFolder(folder.getAbsolutePath());
    }

    /**
     * Records a new absolute folder path. Parent folders are automatically created.
     *
     * @param absFolderPath The file path, e.g. "/dir/file" (any platform) or "c:\dir\file"
     *     (windows)
     */
    public void recordExistingFolder(String absFolderPath) {
        try {
            Files.createDirectories(mFileSystem.getPath(getPlatformSpecificPath(absFolderPath)));
        } catch (IOException e) {
            assert false : e.getMessage();
        }
    }

    /**
     * Returns true if a folder with the given path has been recorded.
     */
    public boolean hasRecordedExistingFolder(File folder) {
        return exists(folder) && isDirectory(folder);
    }

    /**
     * Returns the list of paths added using {@link #recordExistingFile(String)} and eventually
     * updated by {@link #delete(File)} operations.
     *
     * <p>The returned list is sorted by alphabetic absolute path string.
     */
    @NonNull
    public List<String> getExistingFiles() {
        return InMemoryFileSystems.getExistingFiles(mFileSystem);
    }

    /**
     * Returns the list of folder paths added using {@link #recordExistingFolder(String)} and
     * eventually updated {@link #delete(File)} or {@link #mkdirs(File)} operations.
     *
     * <p>The returned list is sorted by alphabetic absolute path string.
     */
    @NonNull
    public List<String> getExistingFolders() {
        return InMemoryFileSystems.getExistingFolders(mFileSystem);
    }

    @Override
    public File ensureRealFile(@NonNull File in) throws IOException {
        if (!exists(in)) {
            return in;
        }
        File result = File.createTempFile("MockFileOp", null);
        result.deleteOnExit();
        try (OutputStream os = new FileOutputStream(result)) {
            ByteStreams.copy(newFileInputStream(in), os);
        }
        return result;
    }

    public byte[] getContent(File file) {
        try {
            return Files.readAllBytes(toPath(file));
        }
        catch (IOException e) {
            return new byte[0];
        }
    }

    @NonNull
    @Override
    public Path toPath(@NonNull String path) {
        return getFileSystem().getPath(getPlatformSpecificPath(path));
    }

    @NonNull
    @Override
    public File toFile(@NonNull Path path) {
        return new File(path.toString());
    }
}
