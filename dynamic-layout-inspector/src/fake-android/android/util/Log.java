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

package android.util;

import androidx.annotation.VisibleForTesting;

/**
 * During testing this is used instead of the version in android.jar, since all the methods there
 * are stubbed out.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Log {
    @VisibleForTesting public static final boolean DEBUG_LOG_IN_TESTS = false;

    public static int w(String tag, String msg) {
        if (DEBUG_LOG_IN_TESTS) {
            System.err.println(msg);
        }
        return 0;
    }

    public static int w(String tag, String msg, Throwable t) {
        if (DEBUG_LOG_IN_TESTS) {
            System.err.println(msg);
            t.printStackTrace();
        }
        return 0;
    }
}
