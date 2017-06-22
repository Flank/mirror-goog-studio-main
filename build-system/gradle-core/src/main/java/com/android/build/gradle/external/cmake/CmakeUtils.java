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

package com.android.build.gradle.external.cmake;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/** Cmake utility class. */
public class CmakeUtils {
    private static final String CMAKE_VERSION_LINE_PREFIX = "cmake version ";

    /**
     * Parses the Cmake (from the given install path) version string into a structure.
     *
     * @return Revision for the version string.
     * @throws IOException I/O failure
     */
    @NonNull
    public static Revision getVersion(@NonNull File cmakeInstallPath) throws IOException {
        final String versionString = getVersionString(cmakeInstallPath);
        return Revision.parseRevision(versionString);
    }

    @NonNull
    public static Revision getVersion(@NonNull String cmakeVersionString) {
        return Revision.parseRevision(cmakeVersionString);
    }

    /**
     * Reads the first line of the version output for the current Cmake and returns the version
     * string. For the version output 'cmake version 3.8.0-rc2' the function return '3.8.0-rc2'
     *
     * @return Current Cmake version as a string
     * @throws IOException I/O failure
     */
    @NonNull
    private static String getVersionString(@NonNull File cmakeInstallPath) throws IOException {
        final String versionOutput = getCmakeVersionLinePrefix(cmakeInstallPath);
        if (!versionOutput.startsWith(CMAKE_VERSION_LINE_PREFIX)) {
            throw new RuntimeException(
                    "Did not recognize stdout line as a cmake version: " + versionOutput);
        }
        return versionOutput.substring(CMAKE_VERSION_LINE_PREFIX.length());
    }

    /**
     * Reads the version output for the current Cmake and returns the first line read. Example:
     *
     * <p>$ ./cmake --version
     *
     * <p>cmake version 3.8.0-rc2
     *
     * <p>CMake suite maintained and supported by Kitware (kitware.com/cmake).
     *
     * <p>This function for the above example would return 'cmake version 3.8.0-rc2'
     *
     * @return Current Cmake version output as string
     * @throws IOException I/O failure
     */
    private static String getCmakeVersionLinePrefix(@NonNull File cmakeInstallPath)
            throws IOException {
        File cmakeExecutable = new File(cmakeInstallPath, "cmake");
        ProcessBuilder processBuilder =
                new ProcessBuilder(cmakeExecutable.getAbsolutePath(), "--version");
        processBuilder.redirectErrorStream();
        Process process = processBuilder.start();
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(process.getInputStream());
            try {
                bufferedReader = new BufferedReader(inputStreamReader);
                return bufferedReader.readLine();
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
        } finally {
            if (inputStreamReader != null) {
                inputStreamReader.close();
            }
        }
    }
}
