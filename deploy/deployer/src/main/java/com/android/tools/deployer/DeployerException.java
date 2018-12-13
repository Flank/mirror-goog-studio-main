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

public class DeployerException extends Exception {

    private final Error error;


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
        super(message);
        this.error = error;
    }


    public DeployerException(Error error, Throwable t) {
        super(t);
        this.error = error;
    }

    public Error getError() {
        return error;
    }

}
