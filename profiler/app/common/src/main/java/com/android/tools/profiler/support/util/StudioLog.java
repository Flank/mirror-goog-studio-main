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

package com.android.tools.profiler.support.util;

import android.util.Log;
import java.util.HashSet;
import java.util.Set;

/**
 * Class to call instead of Android {@link Log}.
 *
 * <p>In order to avoid spamming the user's console, messages will only print once per unique
 * instance.
 */
public final class StudioLog {
    private static final String TAG = "StudioProfiler";
    private static final Set<Integer> SEEN_MSG_HASHCODES = new HashSet<Integer>();
    private static final String ERROR_HEADER =
            "Studio Profilers encountered an unexpected error. "
                    + "Consider reporting a bug, including logcat output below.\n"
                    + "See also: https://developer.android.com/studio/report-bugs.html#studio-bugs\n\n";

    public static void e(String msg) {
        if (SEEN_MSG_HASHCODES.add(msg.hashCode())) {
            Log.e(TAG, ERROR_HEADER + msg);
        }
    }

    public static void e(String msg, Throwable tr) {
        if (SEEN_MSG_HASHCODES.add(msg.hashCode())) {
            Log.e(TAG, ERROR_HEADER + msg, tr);
        }
    }

    public static void v(String msg) {
        if (SEEN_MSG_HASHCODES.add(msg.hashCode())) {
            Log.v(TAG, msg);
        }
    }
}
