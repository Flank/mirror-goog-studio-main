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
package android.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Message;
import java.util.HashMap;
import java.util.Map;

public class ActivityThread {

    private static final ActivityThread sActivityThread = new ActivityThread();
    private static final Application sApplication = new Application();

    // Required to be called mActivities as it follows the expected standard in the
    // android framework. This field is accessed via reflection for events.
    private Map<Object, ActivityRecord> mActivities = new HashMap<>();

    public class ActivityRecord {
        boolean paused = false;
        Activity activity = null;

        public ActivityRecord(Activity activity, boolean paused) {
            this.activity = activity;
            this.paused = paused;
        }
    }

    public static Application currentApplication() {
        return sApplication;
    }

    public static ActivityThread currentActivityThread() {
        return sActivityThread;
    }

    public ApplicationThread getApplicationThread() {
        return new ApplicationThread();
    }

    public ActivityThread() {
        System.out.println("ActivityThread Created");
    }

    public void putActivity(Activity activity, boolean paused) {
        ActivityRecord record = new ActivityRecord(activity, paused);
        currentApplication().registerActivity(activity);
        mActivities.put(activity, record);
    }

    public Activity getActivity(String activityClass) {
        for (ActivityRecord record : mActivities.values()) {
            if (record.activity != null
                    && record.activity.getClass().getName().equals(activityClass)) {
                return record.activity;
            }
        }
        return null;
    }

    public static IPackageManager getPackageManager() {
        return new PackageManager();
    }

    public static class ApplicationThread {
        private H h = new H();

        public void scheduleApplicationInfoChanged(ApplicationInfo info) {
            Message msg = new Message();
            msg.what = H.APPLICATION_INFO_CHANGED;
            h.handleMessage(msg);
        }
    }

    public static class H {
        public static final int APPLICATION_INFO_CHANGED = 156;

        public void handleMessage(Message msg) {
            if (msg.what == APPLICATION_INFO_CHANGED) {
                System.out.println("APPLICATION_INFO_CHANGED triggered");
            }

            if (msg.what == -1) {
                System.out.println("APPLICATION_INFO_CHANGED aborted");
            }
        }
    }
}
