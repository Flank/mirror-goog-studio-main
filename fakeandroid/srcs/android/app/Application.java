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

package android.app;

import android.os.Bundle;
import java.util.HashSet;
import java.util.Set;

/** Empty class to act as a test mock */
public class Application {

    Set<ActivityLifecycleCallbacks> myCallbacks = new HashSet<>();

    LoadedApk mLoadedApk = null;

    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, Bundle savedInstanceState);

        void onActivityStarted(Activity activity);

        void onActivityResumed(Activity activity);

        void onActivityPaused(Activity activity);

        void onActivityStopped(Activity activity);

        void onActivitySaveInstanceState(Activity activity, Bundle outState);

        void onActivityDestroyed(Activity activity);
    }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        myCallbacks.add(callback);
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        myCallbacks.remove(callback);
    }

    public void registerActivity(Activity activity) {
        mLoadedApk = new LoadedApk(activity.getClass().getClassLoader());
        for (ActivityLifecycleCallbacks callback : myCallbacks) {
            callback.onActivityCreated(activity, null);
            callback.onActivityStarted(activity);
            callback.onActivityResumed(activity);
        }
    }
}
