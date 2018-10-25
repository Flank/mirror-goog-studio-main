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

import android.util.Log;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class ActivityThreadInstrumentation {
    // ApplicationThreadConstants.PACKAGE_REPLACED
    private static final int PACKAGE_REPLACED = 3;

    private static boolean mRestart;
    private static Object mActivityThread;
    private static int mCmd;

    public static void setRestart(boolean restart) {
        mRestart = restart;
    }

    public static void handleDispatchPackageBroadcastEntry(
            Object activityThread, int cmd, String[] packages) {
        Log.v("SwapperAgent", "Package Entry Hook");
        mActivityThread = activityThread;
        mCmd = cmd;
    }

    public static void handleDispatchPackageBroadcastExit() {
        Log.v("SwapperAgent", "Package Exit Hook");
        if (mRestart && mCmd == PACKAGE_REPLACED) {
            updateApplicationInfo(mActivityThread);
            mActivityThread = null;
            mRestart = false;
        }
    }

    // Wrapper around ActivityThread#handleUpdateApplicationInfo(ApplicationInfo)
    public static native void updateApplicationInfo(Object activityThread);
}
