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

package com.android.builder.files;

import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.StoredEntry;
import com.android.builder.internal.packaging.zip.StoredEntryType;
import com.android.builder.internal.packaging.zip.ZFile;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utilities to handle {@link RelativeFile}.
 */
public final class RelativeFiles {

    private RelativeFiles() {}

    /**
     * Loads all files in a directory recursively.
     *
     * @param directory the directory, must exist and be a readable directory
     * @return all files in the directory, sub-directories included
     */
    @NonNull
    public static Set<RelativeFile> fromDirectory(@NonNull File directory) {
        return fromDirectory(directory, directory);
    }

    /**
     * Loads all files in a directory recursively, filtering the results with a predicate.
     * Filtering is only done at the end so, even if a directory is excluded from the filter,
     * its files will be included if they are accepted by the filter.
     *
     * @param directory the directory, must exist and be a readable directory
     * @param filter a predicate to filter which files should be included in the result; only
     * files to whom the filter application results in {@code true} are included in the result
     * @return all files in the directory, sub-directories included
     */
    @NonNull
    public static ImmutableSet<RelativeFile> fromDirectory(
            @NonNull File directory,
            @NonNull final Predicate<RelativeFile> filter) {
        return ImmutableSet.copyOf(Sets.filter(fromDirectory(directory, directory), filter::test));
    }

    /**
     * Loads all files in a directory recursively. Creates al files relative to another directory.
     *
     * @param base the directory to use for relative files
     * @param directory the directory to get files from, must exist and be a readable directory
     * @return all files in the directory, sub-directories included
     */
    @NonNull
    private static ImmutableSet<RelativeFile> fromDirectory(
            @NonNull File base,
            @NonNull File directory) {
        Preconditions.checkArgument(base.isDirectory(), "!base.isDirectory()");
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory()");

        Set<RelativeFile> files = Sets.newHashSet();
        File[] directoryFiles =
                Verify.verifyNotNull(directory.listFiles(), "directory.listFiles() == null");
        for (File file : directoryFiles) {
            files.add(new RelativeFile(base, file));

            if (file.isDirectory()) {
                files.addAll(fromDirectory(base, file));
            }
        }

        return ImmutableSet.copyOf(files);
    }

    /**
     * Constructs a predicate over relative files from a predicate over paths, applying it to the
     * normalized relative path contained in the relative file.
     *
     * @param predicate the file predicate
     * @return the relative file predicate built upon {@code predicate}
     */
    @NonNull
    public static Predicate<RelativeFile> fromPathPredicate(@NonNull Predicate<String> predicate) {
        return rf -> predicate.test(rf.getOsIndependentRelativePath());
    }

    /**
     * Reads a zip file and adds all files in the file in a new relative set.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableSet<RelativeFile> fromZip(@NonNull File zip) throws IOException {
        Preconditions.checkArgument(zip.isFile(), "!zip.isFile()");

        Set<RelativeFile> files = Sets.newHashSet();

        try (ZFile zipReader = new ZFile(zip)) {
            for (StoredEntry entry : zipReader.entries()) {
                if (entry.getType() == StoredEntryType.FILE) {
                    File file = new File(zip, FileUtils.toSystemDependentPath(
                            entry.getCentralDirectoryHeader().getName()));
                    files.add(new RelativeFile(zip, file));
                }
            }
        }

        return ImmutableSet.copyOf(files);
    }
}
