/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deploy.instrument;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class Overlay {
    private static final String OVERLAY_PATH_FORMAT = "/data/data/%s/code_cache/.overlay/";

    private final Path overlayPath;

    public Overlay(String packageName) {
        String pathString = String.format(OVERLAY_PATH_FORMAT, packageName);
        overlayPath = Paths.get(pathString);
    }

    public Path getOverlayRoot() {
        return overlayPath;
    }

    public List<File> getApkDirs() throws IOException {
        ArrayList<File> apkDirs = new ArrayList<>();
        if (!overlayPathExists()) {
            return apkDirs;
        }

        // Exploded APKs are directories with an APK file extension that contain the contents
        // of the original non-exploded APK at the same paths as the original.
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(overlayPath, "*.apk")) {
            for (Path path : dir) {
                final File apk = path.toFile();
                if (apk.isDirectory()) {
                    apkDirs.add(apk);
                }
            }
        }
        return apkDirs;
    }

    public List<File> getDexFiles() throws IOException {
        ArrayList<File> dexFiles = new ArrayList<>();
        if (!overlayPathExists()) {
            return dexFiles;
        }

        // Ensure that swapped dex take precedence over installed dex by adding them to the class
        // path first. Swapped dex are currently stored in the top-level overlay directory.
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(overlayPath, "*.dex")) {
            for (Path dex : dir) {
                dexFiles.add(dex.toFile());
            }
        }

        for (File apk : getApkDirs()) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(apk.toPath(), "*.dex")) {
                for (Path dex : dir) {
                    dexFiles.add(dex.toFile());
                }
            }
        }
        return dexFiles;
    }

    public List<File> getNativeLibraryDirs() throws IOException {
        ArrayList<File> nativeLibraryDirs = new ArrayList<>();
        if (!overlayPathExists()) {
            return nativeLibraryDirs;
        }

        for (File apk : getApkDirs()) {
            Path libPath = apk.toPath().resolve("lib");
            if (!Files.exists(libPath)) {
                continue;
            }
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(libPath)) {
                for (Path abi : dir) {
                    nativeLibraryDirs.add(abi.toFile());
                }
            }
        }
        return nativeLibraryDirs;
    }

    private boolean overlayPathExists() {
        return overlayPath.toFile().exists();
    }
}
