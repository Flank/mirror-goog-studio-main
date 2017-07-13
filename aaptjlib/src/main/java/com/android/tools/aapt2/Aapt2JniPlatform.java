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

package com.android.tools.aapt2;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.io.Resources;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The AAPT2 jni libraries needed for each platform.
 *
 * <p>When updating AAPT2 jni {@code Aapt2JniPlatformTest} will fail, and the expected hashes of the
 * artifacts should be updated here as speficied by the test output.
 */
enum Aapt2JniPlatform {
    WIN_64(
            "win64",
            HashCode.fromString("247a7e1d8a2d41bbc78d70e955aba2e0e0f28510667b008e4767e2dbc09d129d"),
            "libwinpthread-1.dll",
            "libaapt2_jni.dll"),
    WIN_32(
            "win32",
            HashCode.fromString("89edc789ff014204e00c6c227d85343d1fa758d3ac85c432462b8498a1b4f1ca"),
            "libwinpthread-1.dll",
            "libaapt2_jni.dll"),
    MAC_64(
            "mac64",
            HashCode.fromString("76acbf9b3587ba8339b3a07c7b4cdba8a3b8364611e7ee63fcade9699e3bb8d4"),
            "libc++.dylib",
            "libaapt2_jni.dylib"),
    LINUX_64(
            "linux64",
            HashCode.fromString("be6ce5d680fbd21271d2de31bf53589c4003cff3fcb17cbdbf7261af7c868b1e"),
            "libc++.so",
            "libaapt2_jni.so"),
    ;

    private final String directoryName;
    private final HashCode cacheKey;
    private final ImmutableList<String> fileNames;

    Aapt2JniPlatform(
            @NonNull String directoryName,
            @NonNull HashCode cacheKey,
            @NonNull String... fileNames) {
        this.directoryName = directoryName;
        this.cacheKey = cacheKey;
        this.fileNames = ImmutableList.copyOf(fileNames);
    }

    static Aapt2JniPlatform getCurrentPlatform() {
        boolean is64Bit = System.getProperty("os.arch").contains("64");
        switch (SdkConstants.CURRENT_PLATFORM) {
            case SdkConstants.PLATFORM_WINDOWS:
                return is64Bit ? WIN_64 : WIN_32;
            case SdkConstants.PLATFORM_DARWIN:
                if (!is64Bit) {
                    throw new Aapt2Exception("32-bit JVM is not supported");
                }
                return MAC_64;
            case SdkConstants.PLATFORM_LINUX:
                if (!is64Bit) {
                    throw new Aapt2Exception("32-bit JVM is not supported");
                }
                return LINUX_64;
            default:
                throw new IllegalStateException("Unknown platform");
        }
    }

    /** The unique identifer for these artifacts so they can be stored in the build cache. */
    @NonNull
    HashCode getCacheKey() {
        return cacheKey;
    }

    /** Writes the libraries for this platform into the given directory. */
    void writeToDirectory(@NonNull Path directory) throws IOException {
        for (String fileName : fileNames) {
            URL url = getResource(fileName);
            try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
                Files.copy(inputStream, directory.resolve(fileName));
            }
        }
    }

    /**
     * Given an already populated directory either from running {@link #writeToDirectory(Path)} or
     * from a cache of a previous run returns the file names of the libraries in that directory.
     */
    @NonNull
    List<Path> getFiles(@NonNull Path cacheDirectory) {
        return fileNames.stream().map(cacheDirectory::resolve).collect(Collectors.toList());
    }

    @NonNull
    private URL getResource(@NonNull String fileName) {
        return Resources.getResource(directoryName + "/" + fileName);
    }
}
