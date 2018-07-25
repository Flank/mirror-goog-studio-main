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

package com.android.tools.deploy.instrument;

import android.os.Message;
import android.util.Log;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class ActivityThreadHandlerWrapper {
    private static final String TAG = "SwapperInstrumented";

    // The flag for an application info change event.
    private static int appInfoChanged = getApplicationInfoChangedValue();

    // Whether or not the handler is hot swapping.
    private static boolean isHotSwapping = true;

    public static void entryHook(Object handler, Message msg) {
        synchronized (ActivityThreadHandlerWrapper.class) {
            boolean isRestarting = msg.what == appInfoChanged;
            if (!isHotSwapping || !isRestarting) {
                return;
            }

            if (!tryRedefineClasses()) {
                Log.w(TAG, "Redefine classes failed!");
                msg.what = -1;
            } else {
                Log.v(TAG, "Redefine classes succeeded!");
            }

            isHotSwapping = false;
            return;
        }
    }

    // Gating the entry hook behind a boolean prevents the instrumentation from attempting to
    // redefine classes every time a non-instrumentation change occurs.
    public static void prepareForHotSwap() {
        synchronized (ActivityThreadHandlerWrapper.class) {
            isHotSwapping = true;
        }
    }

    // The message code for APPLICATION_INFO_CHANGED in android/app/ActivityThread$H could change
    // between android versions. Because it is a static final field of a package-private inner
    // class, we cannot access it directly here. We instead retrieve the value via JNI.
    // TODO: Will this trigger greylist or whitelist complaints? Current behavior sometimes pops
    // a toast, but mostly does not; light-greylist warnings are expected to appear in logcat,
    // but do not.
    public static native int getApplicationInfoChangedValue();

    public static native boolean tryRedefineClasses();
}
