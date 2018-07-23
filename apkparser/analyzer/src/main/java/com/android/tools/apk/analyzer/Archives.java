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

import static com.android.SdkConstants.EXT_APP_BUNDLE;
import static com.android.SdkConstants.EXT_ZIP;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.apk.analyzer.internal.AndroidArtifact;
import com.android.tools.apk.analyzer.internal.AppBundleArtifact;
import com.android.tools.apk.analyzer.internal.ZipArtifact;
import com.android.utils.FileUtils;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;

public class Archives {

    /** Opens an archive file from the local file system */
    @NonNull
    public static Archive open(@NonNull Path archive) throws IOException {
        if (hasFileExtension(archive, EXT_ZIP)) {
            // We assume this is an AIA bundle, which we give special handling
            return ZipArtifact.fromZippedBundle(archive);
        } else if (hasFileExtension(archive, EXT_APP_BUNDLE)) {
            // Android App Bundle (.aab) archive
            return AppBundleArtifact.fromBundleFile(archive);
        } else {
            // APK file archive
            FileSystem fileSystem = FileUtils.createZipFilesystem(archive);
            return new AndroidArtifact(archive, fileSystem);
        }
    }

    /**
     * Opens an APK file extracted from an AIA bundle
     *
     * <p>Note: APK files extracted from AIA bundle file should be abstracted in a more transparent
     * manner.
     */
    @NonNull
    static Archive openInnerZip(@NonNull Path archive) throws IOException {
        FileSystem fileSystem = FileUtils.createZipFilesystem(archive);
        return new AndroidArtifact(archive, fileSystem);
    }

    /**
     * Returns the {@link ArchiveEntry} corresponding to the "main" {@code AndroidManifest.xml} file
     * of the archive.
     */
    @Nullable
    public static ArchiveEntry getFirstManifestArchiveEntry(@NonNull ArchiveNode input) {
        // APK file has their manifest in the top level node
        if (input.getData().getArchive() instanceof AndroidArtifact) {
            Archive archive = input.getData().getArchive();
            return getTopLevelManifestEntry(input, archive);
        }

        // AIA bundle files contain multiple APK files. Look for the first one that contains
        // a manifest at the top level
        if (input.getData().getArchive() instanceof ZipArtifact) {
            return input.getChildren()
                    .stream()
                    .map(
                            node -> {
                                if (node.getData() instanceof InnerArchiveEntry) {
                                    ArchiveEntry innerEntry =
                                            ((InnerArchiveEntry) node.getData()).asArchiveEntry();
                                    return getTopLevelManifestEntry(node, innerEntry.getArchive());
                                }
                                return null;
                            })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        // App bundle contain one node for the base module and one for each dynamic feature
        // module. The "main" manifest is the one of the base module.
        if (input.getData().getArchive() instanceof AppBundleArtifact) {
            AppBundleArtifact appBundleArchive = (AppBundleArtifact) input.getData().getArchive();
            ArchiveNode baseDir =
                    getChild(input, appBundleArchive.getContentRoot().resolve("base/"));
            if (baseDir == null) {
                return null;
            }
            ArchiveNode manifestDir =
                    getChild(baseDir, baseDir.getData().getPath().resolve("manifest/"));
            if (manifestDir == null) {
                return null;
            }
            ArchiveNode manifest =
                    getChild(
                            manifestDir,
                            manifestDir
                                    .getData()
                                    .getPath()
                                    .resolve(SdkConstants.FN_ANDROID_MANIFEST_XML));
            if (manifest == null) {
                return null;
            }
            return manifest.getData();
        }
        return null;
    }

    @Nullable
    private static ArchiveNode getChild(@NonNull ArchiveNode input, @NonNull Path path) {
        return input.getChildren()
                .stream()
                .filter(node -> node.getData().getPath().equals(path))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static ArchiveEntry getTopLevelManifestEntry(
            @NonNull ArchiveNode input, Archive archive) {
        Path path = archive.getContentRoot().resolve(SdkConstants.FN_ANDROID_MANIFEST_XML);
        return input.getChildren()
                .stream()
                .filter(x -> x.getData().getPath().equals(path))
                .map(ArchiveNode::getData)
                .findFirst()
                .orElse(null);
    }

    private static boolean hasFileExtension(@NonNull Path path, @NonNull String extension) {
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
        return path.getFileName().toString().toLowerCase().endsWith(extension);
    }
}
