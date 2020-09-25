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

package com.android.tools.agent.app.inspection.version;

import com.android.tools.agent.app.inspection.ClassLoaderUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens a stream to the targeted version file and reads its first line.
 *
 * <p>This class caches the version strings by their file names.
 */
class VersionFileReader {

    /** Result object representing the outcome of the read. */
    static class Result {
        enum Status {
            /** Read was successful. Version string was obtained. */
            SUCCESS,
            /** Version file was not found. */
            NOT_FOUND,
            /** An error was encountered while reading the version file. */
            READ_ERROR
        }

        public Status status;

        /** The version string if the read was successful. Otherwise null. */
        public String versionString;

        public Result(Status status, String versionString) {
            this.status = status;
            this.versionString = versionString;
        }
    }

    private final ConcurrentHashMap<String, Result> mVersions = new ConcurrentHashMap<>();

    /**
     * Reads the specified version file in the APK's META-INF directory.
     *
     * <p>This uses the main class loader and will throw a runtime exception if it's not found.
     *
     * @param versionFile the full name of the version file
     * @return input stream of the version file
     */
    private static InputStream loadVersionFile(String versionFile) {
        ClassLoader mainClassLoader = ClassLoaderUtils.mainThreadClassLoader();
        if (mainClassLoader == null) {
            throw new RuntimeException("main class loader not found");
        }
        return mainClassLoader.getResourceAsStream("META-INF/" + versionFile);
    }

    /**
     * Reads the first line of the provided input stream of the version file.
     *
     * <p>This assumes the first and only line of the file is the version string. Format is the same
     * as what Gradle uses, e.g. 1.2.3-alpha04 See also:
     * https://docs.gradle.org/current/userguide/single_versions.html
     *
     * @param inputStream the stream of the version file
     * @return the first line read. null if IOException was encountered.
     */
    private static String readVersionStringOrNull(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private Result newResult(Result.Status status, String versionFile, String versionString) {
        Result newResult = new Result(status, versionString);
        mVersions.putIfAbsent(versionFile, newResult);
        return newResult;
    }

    /**
     * Opens an input stream to the provided version file if it exists, and reads its first line.
     *
     * @param versionFile the file to be read
     * @return the result of the read which can be of 3 statuses. SUCCESS, NOT_FOUND, and
     *     READ_ERROR. See enum declaration for more details.
     */
    public Result readVersionFile(String versionFile) {
        if (mVersions.containsKey(versionFile)) {
            return mVersions.get(versionFile);
        }

        InputStream inputStream = loadVersionFile(versionFile);
        if (inputStream == null) {
            return newResult(Result.Status.NOT_FOUND, versionFile, null);
        }

        String versionString = readVersionStringOrNull(inputStream);
        if (versionString == null) {
            return newResult(Result.Status.READ_ERROR, versionFile, null);
        } else {
            return newResult(Result.Status.SUCCESS, versionFile, versionString);
        }
    }
}
