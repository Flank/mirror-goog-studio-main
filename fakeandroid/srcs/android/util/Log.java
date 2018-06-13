/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.util;

/** Skeleton class based on actual Log to act as a test mock. */
public final class Log {

    public static final int VERBOSE = 2;
    public static final int WARN = 5;
    public static final int ERROR = 6;

    private Log() {}

    public static int v(String tag, String msg) {
        return printlns(VERBOSE, tag, msg);
    }

    public static int w(String tag, String msg) {
        return printlns(WARN, tag, msg);
    }

    public static int e(String tag, String msg) {
        return printlns(ERROR, tag, msg);
    }

    public static int e(String tag, String msg, Throwable t) {
        return printlns(ERROR, tag, msg, t);
    }

    public static int printlns(int priority, String tag, String msg) {
        return printlns(priority, tag, msg, null);
    }

    public static int printlns(int priority, String tag, String msg, Throwable t) {
        return 0;
    }
}
