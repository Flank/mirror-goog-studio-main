/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.profiler.support.energy;

import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

/**
 * Contains methods for tracking window wake locks.
 */
public class WindowWakeLockTracker {

    public static void wrapAddFlags(Window window, int flags) {
        if (containsFlagKeepScreenOn(flags)) {
            onWindowWakeLockAcquired();
        }
        window.addFlags(flags);
    }

    public static void wrapSetFlags(Window window, int flags, int mask) {
        if (containsFlagKeepScreenOn(mask)) {
            if (containsFlagKeepScreenOn(flags)) {
                onWindowWakeLockAcquired();
            } else {
                onWindowWakeLockReleased();
            }
        }
        window.setFlags(flags, mask);
    }

    public static void wrapClearFlags(Window window, int flags) {
        if (containsFlagKeepScreenOn(flags)) {
            onWindowWakeLockReleased();
        }
        window.clearFlags(flags);
    }

    private static boolean containsFlagKeepScreenOn(int flags) {
        return ((flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) > 0);
    }

    private static native void onWindowWakeLockAcquired();

    private static native void onWindowWakeLockReleased();
}
