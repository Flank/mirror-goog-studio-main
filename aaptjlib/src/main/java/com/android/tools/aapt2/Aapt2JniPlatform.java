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
            HashCode.fromString("5fd4f522ce05c0d5a0095a7d63e05d57aa315f868a59b579cd6e192b33cbbff3"),
            "libwinpthread-1.dll",
            "libaapt2_jni.dll"),
    WIN_32(
            "win32",
            HashCode.fromString("70ae9066ed73453808289d5a7b683bb73db3a59d52773c8347e2232d65df0248"),
            "libwinpthread-1.dll",
            "libaapt2_jni.dll"),
    MAC_64(
            "mac64",
            HashCode.fromString("6cb63d4b0a119228c57bc485eadd67939c95faa2f74e5de23bd5687e3c042576"),
            "libc++.dylib",
            "libaapt2_jni.dylib"),
    LINUX_64(
            "linux64",
            HashCode.fromString("df690ad929660af573e0b7c44598b9be2da83a664dac1f0e06489f28ad700d6a"),
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
