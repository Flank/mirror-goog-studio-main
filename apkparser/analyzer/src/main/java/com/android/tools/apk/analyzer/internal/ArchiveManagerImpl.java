/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.SdkConstants.EXT_ANDROID_PACKAGE;
import static com.android.SdkConstants.EXT_APP_BUNDLE;
import static com.android.SdkConstants.EXT_ZIP;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.apk.analyzer.Archive;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.ArchiveManager;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.TraceUtils;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipError;
import java.util.zip.ZipInputStream;

public class ArchiveManagerImpl implements ArchiveManager {
    /**
     * List of extensions we're likely to find inside APK files (or AIA bundles or AARs) that are to
     * be treated as internal archives that can be browsed in APK analyzer
     */
    private static final List<String> INNER_ZIP_EXTENSIONS =
            ImmutableList.of(".zip", ".apk", ".jar");

    @NonNull private final ILogger logger;
    @NonNull private final Map<Path, Archive> archives = new HashMap<>();

    @NonNull
    private final Map<Archive, Path> tempDirectories = new TreeMap<>(new ArchivePathComparator());

    public ArchiveManagerImpl(@NonNull ILogger logger) {
        this.logger = logger;
    }

    @NonNull
    @Override
    public ArchiveContext openArchive(@NonNull Path path) throws IOException {
        Archive archive = MapUtils.computeIfAbsent(archives, path, this::openArchiveWorker);
        return new ArchiveContextImpl(this, archive);
    }

    @Nullable
    @Override
    public Archive openInnerArchive(@NonNull Archive archive, @NonNull Path childPath)
            throws IOException {
        // Return null if extension is not supported
        String childFileName = childPath.getFileName().toString();
        if (INNER_ZIP_EXTENSIONS.stream().noneMatch(childFileName::endsWith)) {
            return null;
        }

        logger.info(
                String.format(
                        "Opening inner archive \"%s\" of \"%s\"", childPath, archive.getPath()));

        // Create (or re-use existing) temporary folder
        Path tempFolder = createTempDirectory(archive);
        Path contentRoot = archive.getContentRoot();
        Path tempFile = tempFolder.resolve(contentRoot.relativize(childPath).toString());

        // Create (or re-use existing) archive
        return MapUtils.computeIfAbsent(
                archives,
                tempFile,
                file -> {
                    logger.info(String.format("Extracting inner archive \"%s\"", file));
                    Files.createDirectories(file.getParent());
                    Files.copy(childPath, file);
                    try {
                        return openInnerArchiveWorker(file);
                    } catch (IOException | ZipError e) {
                        logger.warning(
                                String.format(
                                        "Error loading entry from archive \"%s\"\n\"%s\"",
                                        file, TraceUtils.getStackTrace(e)));
                        throw e;
                    }
                });
    }

    @Override
    public void close() throws IOException {
        // Close all archives
        for (Archive archive : archives.values()) {
            logger.info(String.format("Closing archive \"%s\"", archive.getPath()));
            archive.close();
        }
        archives.clear();

        // Delete all temporary directories
        for (Path dir : tempDirectories.values()) {
            logger.info(String.format("Deleting temp directory \"%s\"", dir));
            FileUtils.deleteRecursivelyIfExists(dir.toFile());
        }
        tempDirectories.clear();
    }

    @NonNull
    private Path createTempDirectory(@NonNull Archive archive) throws IOException {
        return MapUtils.computeIfAbsent(
                tempDirectories,
                archive,
                archive1 -> {
                    Path dir =
                            Files.createTempDirectory(archive1.getPath().getFileName().toString());
                    logger.info(
                            String.format(
                                    "Creating temp directory \"%s\" for archive \"%s\"",
                                    dir, archive1.getPath()));
                    return dir;
                });
    }

    @NonNull
    private Archive openArchiveWorker(@NonNull Path path) throws IOException {
        logger.info(String.format("Opening archive \"%s\"", path));
        if (hasFileExtension(path, EXT_ZIP)) {
            // We assume this is an AIA bundle, which we give special handling
            return InstantAppBundleArchive.fromZippedBundle(path);
        } else if (hasFileExtension(path, EXT_APP_BUNDLE)) {
            // Android App Bundle (.aab) archive
            return AppBundleArchive.fromBundleFile(path);
        } else if (hasFileExtension(path, EXT_ANDROID_PACKAGE)) {
            // APK file archive
            return new ApkArchive(path);
        } else {
            return new ZipArchive(path);
        }
    }

    @NonNull
    private static Archive openInnerArchiveWorker(@NonNull Path archive) throws IOException {
        if (hasFileExtension(archive, EXT_ANDROID_PACKAGE)) {
            return new ApkArchive(archive);
        } else {
            // Since https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8037394
            // still exists in the current version of JDK, workaround is provided here
            // to avoid leaving the file channel unclosed.
            validateZipFile(archive);
            return new ZipArchive(archive);
        }
    }

    /**
     * Ensures the path points to a valid ZIP archive, throws ZipError if the archive is not valid
     *
     * @param archive
     */
    private static void validateZipFile(@NonNull Path archive) throws IOException {
        try (FileInputStream fis = new FileInputStream(archive.toString());
                ZipInputStream zis = new ZipInputStream(fis)) {

            // Check null first, since ZipInputStream#readLOC returns null
            // for some bad zip file cases, say encrypted zip files.
            if (zis.getNextEntry() == null) {
                throw new ZipError("No valid contents inside");
            }
            // Go through all entries to make sure the zip file is valid.
            // Invalid entries (e.g. bad crc, unexpected EOF, etc.)
            // result in an IOException being thrown
            while (zis.getNextEntry() != null) {
                // Nothing to do
            }
        }
    }

    private static boolean hasFileExtension(@NonNull Path path, @NonNull String extension) {
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
        return path.getFileName().toString().toLowerCase().endsWith(extension);
    }

    private static class ArchivePathComparator implements Comparator<Archive> {
        @Override
        public int compare(Archive o1, Archive o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    }
}
