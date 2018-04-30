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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.Archive;
import com.android.utils.FileUtils;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

public class AppBundleArtifact implements Archive {
    /** The first byte of a proto XML file is always 0x0A. */
    public static final byte PROTO_XML_LEAD_BYTE = 0x0A;

    private final Path artifact;
    private final FileSystem fileSystem;

    private AppBundleArtifact(@NonNull Path artifact, @NonNull FileSystem fileSystem) {
        this.artifact = artifact;
        this.fileSystem = fileSystem;
    }

    @NonNull
    public static AppBundleArtifact fromBundleFile(@NonNull Path artifact) throws IOException {
        FileSystem fileSystem = FileUtils.createZipFilesystem(artifact);
        return new AppBundleArtifact(artifact, fileSystem);
    }

    @NonNull
    @Override
    public Path getPath() {
        return artifact;
    }

    @Override
    @NonNull
    public Path getContentRoot() {
        return fileSystem.getPath("/");
    }

    @Override
    public void close() throws IOException {
        fileSystem.close();
    }

    @Override
    public boolean isBinaryXml(@NonNull Path p, @NonNull byte[] content) {
        return false;
    }

    @Override
    public boolean isProtoXml(@NonNull Path p, @NonNull byte[] content) {
        if (!p.toString().endsWith(SdkConstants.DOT_XML)) {
            return false;
        }

        Path name = p.getFileName();
        if (name == null) {
            return false;
        }

        boolean manifest = isManifestFile(p);
        boolean insideResFolder = isInsideResFolder(p);
        boolean insideResRaw = isInsiderResRawFolder(p);
        boolean xmlResource = insideResFolder && !insideResRaw;
        if (!manifest && !xmlResource) {
            return false;
        }

        return (content.length > 0) && (content[0] == PROTO_XML_LEAD_BYTE);
    }

    private static boolean isManifestFile(@NonNull Path p) {
        return matchPathPrefix(
                p, PathEntry.any(), PathEntry.FD_MANIFEST, PathEntry.FN_ANDROID_MANIFEST_XML);
    }

    private static boolean isInsideResFolder(@NonNull Path p) {
        return matchPathPrefix(p, PathEntry.any(), PathEntry.name(SdkConstants.FD_RES));
    }

    private static boolean isInsiderResRawFolder(@NonNull Path p) {
        return matchPathPrefix(p, PathEntry.any(), PathEntry.FD_RES, PathEntry.FD_RES_RAW);
    }

    private static boolean matchPathPrefix(
            @NonNull Path path, @NonNull PathEntry... prefixEntries) {
        int index = 0;
        for (PathEntry entry : prefixEntries) {
            if (!entry.matches(path.getName(index))) {
                return false;
            }
            index++;
        }
        return true;
    }

    public abstract static class PathEntry {
        public abstract boolean matches(@NonNull Path name);

        @NonNull
        public static PathEntry any() {
            return AnyPathEntry.instance;
        }

        @NonNull
        public static PathEntry name(@NonNull String name) {
            return new NamePathEntry(name);
        }

        @NonNull public static final PathEntry FD_RES = name(SdkConstants.FD_RES);
        @NonNull public static final PathEntry FD_RES_RAW = name(SdkConstants.FD_RES_RAW);

        @NonNull
        public static final PathEntry FN_ANDROID_MANIFEST_XML =
                name(SdkConstants.FN_ANDROID_MANIFEST_XML);

        @NonNull public static final PathEntry FD_MANIFEST = name("manifest");
    }

    public static class AnyPathEntry extends PathEntry {
        @NonNull public static AnyPathEntry instance = new AnyPathEntry();

        @Override
        public boolean matches(@NonNull Path name) {
            return true;
        }
    }

    public static class NamePathEntry extends PathEntry {
        private final String name;

        public NamePathEntry(@NonNull String name) {
            this.name = name;
        }

        @Override
        public boolean matches(@NonNull Path name) {
            // For ZIP paths, we use strict string equality
            return name.toString().equals(this.name);
        }
    }
}
