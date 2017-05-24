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

package com.android.tools.profiler.support.network.okhttp;

final class OkHttpUtils {
    static StackTraceElement[] getCallstack(String okHttpPackage) {
        StackTraceElement[] callstack = new Throwable().getStackTrace();
        int okHttpCallstackStart = -1;
        int okHttpCallstackEnd = 0;
        for (int i = 0; i < callstack.length; i++) {
            String className = callstack[i].getClassName();
            if (okHttpCallstackStart < 0 && className.startsWith(okHttpPackage)) {
                okHttpCallstackStart = i;
            } else if (okHttpCallstackStart >= 0 && !className.startsWith(okHttpPackage)) {
                okHttpCallstackEnd = i;
                break;
            }
        }
        if (okHttpCallstackStart >= 0) {
            return java.util.Arrays.copyOfRange(callstack, okHttpCallstackEnd, callstack.length);
        } else {
            return new StackTraceElement[] {};
        }
    }
}
