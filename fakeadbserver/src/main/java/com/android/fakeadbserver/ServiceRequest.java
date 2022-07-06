/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.fakeadbserver;

import com.android.annotations.NonNull;

/**
 * Parser for ADB service requests where each part is separated with a ":", e.g.
 * "host-serial:emulator-5554:get-serialno"
 */
class ServiceRequest {

    private static final char SEPARATOR = ':';

    private final String original;

    private String request;
    private String token;

    ServiceRequest(@NonNull String request) {
        this.original = request;
        this.request = request;
    }

    @NonNull
    public String peekToken() {
        int separatorIndex = request.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            return request;
        }
        return request.substring(0, separatorIndex);
    }

    @NonNull
    public String nextToken() {
        int separatorIndex = request.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            token = request;
            request = "";
            return token;
        }
        token = request.substring(0, separatorIndex);
        request = request.substring(separatorIndex + 1);
        return token;
    }

    @NonNull
    public String currToken() {
        return token;
    }

    @NonNull
    public String remaining() {
        return request;
    }

    @NonNull
    public String original() {
        return original;
    }
}
