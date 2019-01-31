/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.deployer;

/**
 * Represents a failed deployment. When installing, apply changes or apply code changes failed, this
 * will be raised containing the information needed by the UI (IDE or command line) to surface the
 * error and the possible actions to the user.
 */
public class DeployerException extends Exception {

    /** The type of error that occurred. */
    private final Error error;

    /**
     * A sub-error code, kept as a String but can only by created by an enum to ensure that it can
     * be logged safely.
     */
    private final String code;

    /** A possible multi-line, more verbose details on the issue that occurred. */
    private final String details;

    public enum Error {
        DUMP_FAILED,
        CANNOT_SWAP_NEW_CLASS,
        REMOTE_APK_NOT_FOUND_ON_DB,
        REDEFINER_ERROR,
        DIFFERENT_NUMBER_OF_APKS,
        DUMP_UNKNOWN_PACKAGE,
        INVALID_APK,
        FAILED_TO_SPLIT_DEXES,
        INTERRUPTED,
        CANNOT_SWAP_STATIC_LIB,
        CANNOT_SWAP_MANIFEST,
        CANNOT_SWAP_RESOURCE,
        ERROR_PUSHING_APK,
        DIFFERENT_NAMES_OF_APKS,
        INSTALL_FAILED,
        UNABLE_TO_PREINSTALL,
        OPERATION_NOT_SUPPORTED, // (yet)
    }

    public DeployerException(Error error, String message) {
        this(error, message, "");
    }

    public DeployerException(Error error, String message, String details) {
        this(error, "", message, details);
    }

    public <E extends Enum> DeployerException(Error error, E code, String message, String details) {
        this(error, code.name(), message, details);
    }

    private DeployerException(Error error, String code, String message, String details) {
        super(message);
        this.error = error;
        this.code = code;
        this.details = details;
    }

    public DeployerException(Error error, Throwable t) {
        super(t);
        this.error = error;
        this.code = "";
        this.details = null;
    }

    public Error getError() {
        return error;
    }

    public String getId() {
        return error.name() + (code.isEmpty() ? "" : ".") + code;
    }

    public String getDetails() {
        return details;
    }
}
