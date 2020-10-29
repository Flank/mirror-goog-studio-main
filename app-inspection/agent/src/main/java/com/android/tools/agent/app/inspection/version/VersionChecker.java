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

/**
 * Checks the minVersion provided by Android Studio against the version file embedded in the app's
 * APK META-INF.
 */
public class VersionChecker {

    private final VersionFileReader reader = new VersionFileReader();

    /**
     * Compares the version of the library against the provided min_version string.
     *
     * <p>The version of the library is found inside a version file in the APK's META-INF directory.
     *
     * @param artifactCoordinate represents the minimum supported library artifact.
     * @return a VersionChecker.Result object containing the result of the check and any errors.
     */
    public VersionCheckerResult checkVersion(ArtifactCoordinate artifactCoordinate) {
        String versionFile = artifactCoordinate.toVersionFileName();
        String minVersionString = artifactCoordinate.version;
        VersionFileReader.Result readResult = reader.readVersionFile(versionFile);
        switch (readResult.status) {
            case NOT_FOUND:
                return new VersionCheckerResult(
                        VersionCheckerResult.Status.NOT_FOUND,
                        "Failed to find version file " + versionFile,
                        artifactCoordinate,
                        null);
            case READ_ERROR:
                return new VersionCheckerResult(
                        VersionCheckerResult.Status.INCOMPATIBLE,
                        "Failed to read version file " + versionFile,
                        artifactCoordinate,
                        null);
        }
        Version version = Version.parseOrNull(readResult.versionString);
        if (version == null) {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.INCOMPATIBLE,
                    "Failed to parse version string "
                            + readResult.versionString
                            + " which is in "
                            + versionFile,
                    artifactCoordinate,
                    readResult.versionString);
        }
        Version minVersion = Version.parseOrNull(minVersionString);
        if (minVersion == null) {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.ERROR,
                    "Failed to parse provided min version " + minVersionString,
                    artifactCoordinate,
                    readResult.versionString);
        }
        if (version.compareTo(minVersion) >= 0) {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.COMPATIBLE,
                    null,
                    artifactCoordinate,
                    readResult.versionString);
        } else {
            return new VersionCheckerResult(
                    VersionCheckerResult.Status.INCOMPATIBLE,
                    "Library version "
                            + readResult.versionString
                            + " does not satisfy the inspector's min version requirement "
                            + minVersionString,
                    artifactCoordinate,
                    readResult.versionString);
        }
    }

}
