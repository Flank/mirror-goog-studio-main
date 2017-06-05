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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Class to call instead of Android {@link Log}.
 *
 * <p>In order to avoid spamming the user's console, messages will be rate-limited by their
 * hashcode, and a message may be dropped if it is sent too frequently.
 */
public final class StudioLog {
    private static final String TAG = "StudioProfiler";
    /** Only send one log message per this rate-limited time duration. */
    private static final long RATE_LIMIT_NS = TimeUnit.MINUTES.toNanos(1);

    /** Map of a message's hashcode to the nanosecond timestamp that the message was logged. */
    private static final Map<Integer, Long> MSG_TIMESTAMPS_NS = new HashMap<Integer, Long>();

    private static final String ERROR_HEADER =
            "Studio Profilers encountered an unexpected error. "
                    + "Consider reporting a bug, including logcat output below.\n"
                    + "See also: https://developer.android.com/studio/report-bugs.html#studio-bugs\n\n";

    public static void e(String msg) {
        if (allowLog(msg)) {
            Log.e(TAG, ERROR_HEADER + msg);
        }
    }

    public static void e(String msg, Throwable tr) {
        if (allowLog(msg)) {
            Log.e(TAG, ERROR_HEADER + msg, tr);
        }
    }

    public static void v(String msg) {
        if (allowLog(msg)) {
            Log.v(TAG, msg);
        }
    }

    private static boolean allowLog(String msg) {
        long now = System.nanoTime();
        int msgHash = msg.hashCode();
        Long timestamp = MSG_TIMESTAMPS_NS.get(msgHash);
        if (timestamp == null || timestamp + RATE_LIMIT_NS < now) {
            MSG_TIMESTAMPS_NS.put(msgHash, now);
            return true;
        } else {
            return false;
        }
    }
}
