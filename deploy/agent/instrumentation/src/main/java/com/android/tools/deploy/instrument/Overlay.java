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

import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class Overlay {
    private static final String TAG = "studio.deploy";
    private static final String OVERLAY_PATH_FORMAT = "/data/data/%s/code_cache/.overlay/";

    private final Path overlayPath;
    private final Path libraryPath;

    public Overlay(String packageName) {
        String pathString = String.format(OVERLAY_PATH_FORMAT, packageName);
        overlayPath = Paths.get(pathString);
        libraryPath = Paths.get(pathString, "lib");
    }

    public Path getOverlayRoot() {
        return overlayPath;
    }

    public List<File> getDexFiles() {
        ArrayList<File> dexFiles = new ArrayList<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(overlayPath, "*.dex")) {
            for (Path dex : dir) {
                dexFiles.add(dex.toFile());
            }
        } catch (IOException io) {
            Log.e(TAG, "Could not enumerate overlay dex files", io);
        }
        return dexFiles;
    }

    public List<File> getNativeLibraryDirs() {
        ArrayList<File> nativeLibraryDirs = new ArrayList<>();

        if (!Files.exists(libraryPath)) {
            return nativeLibraryDirs;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libraryPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    nativeLibraryDirs.add(entry.toFile());
                }
            }
        } catch (IOException io) {
            Log.e(TAG, "Could not enumerate overlay library files", io);
        }

        return nativeLibraryDirs;
    }
}
