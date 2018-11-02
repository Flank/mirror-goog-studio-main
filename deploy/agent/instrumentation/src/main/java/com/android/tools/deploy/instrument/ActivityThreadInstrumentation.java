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
import java.util.Collection;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class ActivityThreadInstrumentation {
    // ApplicationThreadConstants.PACKAGE_REPLACED
    private static final int PACKAGE_REPLACED = 3;
    private static final String TAG = "studio.deploy";

    private static boolean mRestart;
    private static Object mActivityThread;
    private static int mCmd;

    public static void setRestart(boolean restart) {
        mRestart = restart;
    }

    public static void handleDispatchPackageBroadcastEntry(
            Object activityThread, int cmd, String[] packages) {
        mActivityThread = activityThread;
        mCmd = cmd;
    }

    public static void handleDispatchPackageBroadcastExit() {
        if (mCmd != PACKAGE_REPLACED) {
            return;
        }

        try {
            Log.v(TAG, "Fixing application context");
            Object newResourcesImpl = fixAppContext(mActivityThread);
            if (mRestart) {
                // If we're restarting, the activities will be fixed by the call to updateApplicationInfo.
                updateApplicationInfo(mActivityThread);
            } else {
                // If we're not restarting, update each activity so its internal resources point to the
                // proper LoadedApk; that is, the application's LoadedApk.
                Log.v(TAG, "Fixing activity contexts");
                for (Object activity : getActivityClientRecords(mActivityThread)) {
                    fixActivityContext(activity, newResourcesImpl);
                }
            }
        } catch (Exception ex) {
            // The actual risks of the patch are unknown; although it seems to be safe, we're using some
            // defensive exception handling to prevent any application hard-crashes.
            Log.e(TAG, "Error in package installer patch", ex);
        } finally {
            mRestart = false;
        }
    }

    // ResourcesImpl fixAppContext(ActivityThread activityThread)
    public static native Object fixAppContext(Object activityThread);

    // Collection<ActivityClientRecord> getActivityClientRecords(ActivityThread activityThread)
    public static native Collection<? extends Object> getActivityClientRecords(
            Object activityThread);

    // void fixActivityContext(ActivityClientRecord activityRecord, ResourcesImpl newResourcesImpl)
    public static native void fixActivityContext(Object activityRecord, Object newResourcesImpl);

    // Wrapper around ActivityThread#handleUpdateApplicationInfo(ApplicationInfo)
    public static native void updateApplicationInfo(Object activityThread);
}

