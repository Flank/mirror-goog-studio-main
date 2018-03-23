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

package com.android.build.gradle.internal.transforms;

import static com.android.utils.FileUtils.deleteIfExists;
import static com.android.utils.FileUtils.mkdirs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.packaging.PackagingFileAction;
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Predicate;

/**
 * Defines a file filter contract which will use {@link PackagingOptions} to take appropriate
 * action.
 */
@VisibleForTesting
public class FileFilter implements Predicate<String> {

    public static class SubStream {
        private final File folder;
        private final String name;

        SubStream(File folder, String name) {
            this.folder = folder;
            this.name = name;
        }

        public File getFolder() {
            return folder;
        }

        public String getName() {
            return name;
        }
    }

    @Nullable
    private final ParsedPackagingOptions packagingOptions;
    @NonNull
    private final List<SubStream> expandedFolders;

    public FileFilter(
            @NonNull List<SubStream> expandedFolders,
            @Nullable PackagingOptions packagingOptions) {
        this.expandedFolders = ImmutableList.copyOf(expandedFolders);
        this.packagingOptions = new ParsedPackagingOptions(packagingOptions);
    }

    /**
     * Implementation of the {@link Predicate<String>} contract which only cares about copying or
     * ignoring files since merging is handled differently.
     *
     * @param archivePath the archive file path of the entry
     * @return true if the archive entry satisfies the filter, false otherwise.
     */
    @Override
    public boolean test(@NonNull String archivePath) {
        PackagingFileAction action = getPackagingAction(archivePath);
        switch(action) {
            case EXCLUDE:
                return false;
            case PICK_FIRST:
                List<File> allFiles = getAllFiles(archivePath);
                return allFiles.isEmpty();
            case MERGE:
            case NONE:
                return true;
            default:
                throw new RuntimeException("Unhandled action " + action);
        }
    }

    /**
     * Notification of an incremental file changed since last successful run of the task.
     *
     * Usually, we just copy the changed file into the merged folder. However, if the user
     * specified {@link PackagingFileAction#PICK_FIRST}, the file will only be copied if it the
     * first pick. Also, if the user specified {@link PackagingFileAction#MERGE}, all the files
     * with the same entry archive path will be re-merged.
     *
     * @param outputDir merged resources folder.
     * @param changedFile changed file located in a temporary expansion folder
     * @throws IOException
     */
    void handleChanged(@NonNull File outputDir, @NonNull File changedFile)
            throws IOException {
        String archivePath = getArchivePath(changedFile);
        PackagingFileAction action = getPackagingAction(archivePath);
        switch (action) {
            case EXCLUDE:
                return;
            case MERGE:
                // one of the merged file has changed, re-merge all of them.
                mergeAll(outputDir, archivePath);
                return;
            case PICK_FIRST:
                copy(changedFile, outputDir, archivePath);
                return;
            case NONE:
                copy(changedFile, outputDir, archivePath);
        }
    }

    /**
     * Notification of a file removal.
     *
     * file was removed, we need to check that it was not a pickFirst item (since we
     * may now need to pick another one) or a merged item since we would need to re-merge
     * all remaining items.
     *
     * @param outputDir expected merged output directory.
     * @param removedFilePath removed file path from the temporary resources folders.
     * @throws IOException
     */
    public void handleRemoved(@NonNull File outputDir, @NonNull String removedFilePath)
            throws IOException {


        // first delete the output file, it will be eventually replaced.
        File outFile = new File(outputDir, removedFilePath);
        if (outFile.exists()) {
            if (!outFile.delete()) {
                throw new IOException("Cannot delete " + outFile.getAbsolutePath());
            }
        }
        PackagingFileAction itemAction = getPackagingAction(removedFilePath);

        switch(itemAction) {
            case NONE:
            case PICK_FIRST:
                // this was a picked up item, make sure we copy the first still available
                com.google.common.base.Optional<File> firstPick = getFirstPick(removedFilePath);
                if (firstPick.isPresent()) {
                    copy(firstPick.get(), outputDir, removedFilePath);
                }
                return;
            case MERGE:
                // re-merge all
                mergeAll(outputDir, removedFilePath);
                return;
            case EXCLUDE:
                // do nothing
                return;
            default:
                throw new RuntimeException("Unhandled package option"
                        + itemAction);

        }
    }

    private static void copy(@NonNull File inputFile,
            @NonNull File outputDir,
            @NonNull String archivePath) throws IOException {

        File outputFile = new File(outputDir, archivePath);
        mkdirs(outputFile.getParentFile());
        Files.copy(inputFile, outputFile);
    }

    private void mergeAll(@NonNull File outputDir, @NonNull String archivePath)
            throws IOException {

        File outputFile = new File(outputDir, archivePath);
        deleteIfExists(outputFile);
        mkdirs(outputFile.getParentFile());
        List<File> allFiles = getAllFiles(archivePath);
        if (!allFiles.isEmpty()) {
            OutputStream os = null;
            try {
                os = new BufferedOutputStream(new FileOutputStream(outputFile));
                // take each file in order and merge them.
                for (File file : allFiles) {
                    Files.copy(file, os);
                }
            } finally {
                if (os != null) {
                    os.close();
                }
            }
        }
    }

    /**
     * Return the first file from the temporary expansion folders that satisfy the archive path.
     * @param archivePath the entry archive path.
     * @return the first file reference of {@link com.google.common.base.Optional#absent()} if
     * none exist in any temporary expansion folders.
     */
    @NonNull
    private com.google.common.base.Optional<File> getFirstPick(
            @NonNull final String archivePath) {

        return com.google.common.base.Optional.fromNullable(
                forEachExpansionFolder(new FolderAction() {
                    @Nullable
                    @Override
                    public File on(File folder) {
                        File expandedFile = new File(folder, archivePath);
                        if (expandedFile.exists()) {
                            return expandedFile;
                        }
                        return null;
                    }
                }));
    }

    /**
     * Returns all files from temporary expansion folders with the same archive path.
     * @param archivePath the entry archive path.
     * @return a list possibly empty of {@link File}s that satisfy the archive path.
     */
    @NonNull
    private List<File> getAllFiles(@NonNull final String archivePath) {
        final ImmutableList.Builder<File> matchingFiles = ImmutableList.builder();
        forEachExpansionFolder(new FolderAction() {
            @Nullable
            @Override
            public File on(File folder) {
                File expandedFile = new File(folder, archivePath);
                if (expandedFile.exists()) {
                    matchingFiles.add(expandedFile);
                }
                return null;
            }
        });
        return matchingFiles.build();
    }

    /**
     * An action on a folder.
     */
    private interface FolderAction {

        /**
         * Perform an action on a folder and stop the processing if something is returned
         * @param folder the folder to perform the action on.
         * @return a file to stop processing or null to continue to the next expansion folder
         * if any.
         */
        @Nullable
        File on(File folder);
    }

    /**
     * Perform the passed action on each expansion folder.
     * @param action the action to perform on each folder.
     * @return a file if any action returned a value, or null if none returned a value.
     */
    @Nullable
    private File forEachExpansionFolder(@NonNull FolderAction action) {
        for (SubStream subStream : expandedFolders) {
            if (subStream.getFolder().isDirectory()) {
                File value = action.on(subStream.getFolder());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Returns the expansion folder for an expanded file. This represents the location
     * where the packaged jar our source directories java resources were extracted into.
     * @param expandedFile the java resource file.
     * @return the expansion folder used to extract the java resource into.
     */
    @NonNull
    private File getExpansionFolder(@NonNull final File expandedFile) {
        File expansionFolder = forEachExpansionFolder(new FolderAction() {
            @Nullable
            @Override
            public File on(File folder) {
                return expandedFile.getAbsolutePath().startsWith(folder.getAbsolutePath())
                        ? folder : null;
            }
        });
        if (expansionFolder == null) {
            throw new RuntimeException("Cannot determine expansion folder for " + expandedFile
                    + " with folders "  + Joiner.on(",").join(expandedFolders));
        }
        return expansionFolder;
    }

    /**
     * Determines the archive entry path relative to its expansion folder. The archive entry
     * path is the path that was used to save the entry in the original .jar file that got
     * expanded in the expansion folder.
     * @param expandedFile the expanded file to find the relative archive entry from.
     * @return the expanded file relative path to its expansion folder.
     */
    @NonNull
    private String getArchivePath(@NonNull File expandedFile) {
        File expansionFolder = getExpansionFolder(expandedFile);
        return expandedFile.getAbsolutePath()
                .substring(expansionFolder.getAbsolutePath().length() + 1);
    }

    /**
     * Determine the user's intention for a particular archive entry.
     * @param archivePath the archive entry
     * @return a {@link PackagingFileAction} as provided by the user in the build.gradle
     */
    @NonNull
    private PackagingFileAction getPackagingAction(@NonNull String archivePath) {
        if (packagingOptions != null) {
            return packagingOptions.getAction(archivePath);
        }
        return PackagingFileAction.NONE;
    }
}
