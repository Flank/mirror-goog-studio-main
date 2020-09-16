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

/** Represents the result of the Version Check. */
public class VersionCheckerResult {

    // Note whenever making a change to this enum, the corresponding logic in JNI must be
    // updated as well to prevent it from crashing.
    public enum Status {
        /** Version check passed. Implying the version of the library is compatible. */
        COMPATIBLE,
        /**
         * Version check failed. Library is incompatible with the inspector. This is also set when
         * the version file of the library is present but couldn't be parsed.
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

    /** The version file that we checked. */
    public String versionFileName;

    public VersionCheckerResult(Status status, String message, String versionFileName) {
        this.status = status;
        this.message = message;
        this.versionFileName = versionFileName;
    }
}
