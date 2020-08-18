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

/**
 * Checks the minVersion provided by Android Studio against the version file embedded in the app's
 * APK META-INF.
 */
public class VersionChecker {

    /** Represents the result of the Version Check. */
    public static class Result {

        public enum Status {
            /** Version check passed. Implying the version of the library is compatible. */
            COMPATIBLE,
            /**
             * Version check failed. Library is incompatible with the inspector. This is also set
             * when the version file of the library is present but couldn't be parsed.
             */
            INCOMPATIBLE,
            /** The version file of the library could not be found. */
            NOT_FOUND,
            /**
             * This signals an error was encountered while trying to perform the version check. For
             * example: IOException when reading from file stream.
             */
            ERROR
        }

        /** Status of the check. */
        public Status status;

        /** Interesting message set when the check fails or encounters an error. */
        public String message;

        public Result(Status status, String message) {
            this.status = status;
            this.message = message;
        }
    }

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

    /**
     * Compares the version of the library against the provided min_version string.
     *
     * <p>The version of the library is found inside a version file in the APK's META-INF directory.
     *
     * @param versionFile the path of the version file relative to APK root.
     * @param minVersionString the provided min_version string to compare against.
     * @return a VersionChecker.Result object containingthe result of the check and any errors.
     */
    public static Result checkVersion(String versionFile, String minVersionString) {
        InputStream inputStream = loadVersionFile(versionFile);
        if (inputStream == null) {
            return new Result(
                    Result.Status.NOT_FOUND, "Failed to find version file " + versionFile);
        }
        String libraryVersionString = readVersionStringOrNull(inputStream);
        if (libraryVersionString == null) {
            return new Result(
                    Result.Status.INCOMPATIBLE, "Failed to read version file " + versionFile);
        }
        Version version = Version.parseOrNull(libraryVersionString);
        if (version == null) {
            return new Result(
                    Result.Status.INCOMPATIBLE,
                    "Failed to parse version string "
                            + libraryVersionString
                            + " which is in "
                            + versionFile);
        }
        Version minVersion = Version.parseOrNull(minVersionString);
        if (minVersion == null) {
            return new Result(
                    Result.Status.ERROR,
                    "Failed to parse provided min version " + minVersionString);
        }
        if (version.compareTo(minVersion) >= 0) {
            return new Result(Result.Status.COMPATIBLE, null);
        } else {
            return new Result(
                    Result.Status.INCOMPATIBLE,
                    "Library version "
                            + libraryVersionString
                            + " does not satisfy the inspector's min version requirement "
                            + minVersionString);
        }
    }
}
