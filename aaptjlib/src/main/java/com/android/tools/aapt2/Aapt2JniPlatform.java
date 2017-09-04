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
            HashCode.fromString("57bf459da6a740956630dec3f39d95ba0fbb6d2540f6b34729fa048a253c85be"),
            "libwinpthread-1.dll",
            "libaapt2_jni.dll"),
    WIN_32(
            "win32",
            HashCode.fromString("868550f705679e2d6a3f6a80f5d0872ee2c7d37f59c2c9bfb5d68a0460109cba"),
            "libwinpthread-1.dll",
            "libaapt2_jni.dll"),
    MAC_64(
            "mac64",
            HashCode.fromString("aecb30785e93480af0bc81013386850b0fb5d3d8921eec9bd2a026c07578dac4"),
            "libc++.dylib",
            "libaapt2_jni.dylib"),
    LINUX_64(
            "linux64",
            HashCode.fromString("b21d816cee1d00e30034af0a8c9b2c2711233c6cc5f4ff9c586df439215a24a6"),
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
