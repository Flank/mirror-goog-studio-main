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

package com.android.tools.profiler.support.profilers;

import com.android.tools.profiler.support.ProfilerService;
import com.android.tools.profiler.support.event.WindowProfilerCallback;

import android.app.*;
import android.os.Bundle;
import android.util.Log;
import android.view.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * EventProfiler class captures and reports all events that we track on an app. These events are
 * used for the event monitor.
 */
public class EventProfiler implements ProfilerComponent, Application.ActivityLifecycleCallbacks {

    private Set<Activity> myActivities = new HashSet<Activity>();
    private Class mSupportLibFragment;

    public EventProfiler() {
        initialize();
    }

    /**
     * This class handles updating the callback for any activities that are activated or created. We
     * construct a new wrapper around the callback because an application can have multiple
     * windows.
     *
     * @param activity The activity we get the window of and set the callback on.
     */
    private void updateCallback(Activity activity) {
        //TODO Poll to verify.
        //TODO Verify the order of this is fixed, can this happen before the users onActivityCreated, or is it always
        // after?
        Window window = activity.getWindow();
        if(!WindowProfilerCallback.class.isInstance(window.getCallback())) {
            window.setCallback(new WindowProfilerCallback(window.getCallback()));
        }
    }

    private void initialize() {
        try {
            Log.v(ProfilerService.STUDIO_PROFILER, "Acquiring Activity for Events");
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Application app = (Application) activityThreadClass.getMethod("currentApplication")
                    .invoke(null);
            if(app != null) {
                app.registerActivityLifecycleCallbacks(this);
            } else {
                Log.e(ProfilerService.STUDIO_PROFILER, "Failed to capture application");
            }
        } catch (ClassNotFoundException ex) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to get ActivityThread class");

        } catch (NoSuchMethodException ex) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to find currentApplication method");

        } catch (IllegalAccessException ex) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Insufficient privileges to get application handle");

        } catch (InvocationTargetException ex) {
            Log.e(ProfilerService.STUDIO_PROFILER, "Failed to call static function currentApplication");
        }

        try {
            // Attempt to load the FragmentActivity from the support lib. If this class does not
            // exist this is a good indication that the support lib is not used by this application.
            mSupportLibFragment = Class.forName("android.support.v4.app.FragmentActivity");
        } catch (ClassNotFoundException ex) {
            mSupportLibFragment = null;
        }
    }

    private void overrideFragmentManager(Activity activity) {
        Object fragmentManager = null;
        Object fragmentList = null;
        // Test if this activity is using the support lib Fragments or
        // android.app.Fragment.
        if(mSupportLibFragment != null &&
                mSupportLibFragment.isAssignableFrom(activity.getClass())) {
            try {
                Method fragmentManagerMethod = mSupportLibFragment
                        .getMethod("getSupportFragmentManager");
                fragmentManager = fragmentManagerMethod.invoke(activity);
                Class fragment = Class.forName("android.support.v4.app.Fragment");
                fragmentList = new FragmentList();
            } catch (NoSuchMethodException ex) {

            } catch (IllegalAccessException ex) {

            } catch (InvocationTargetException ex) {

            } catch (ClassNotFoundException ex) {

            }
        }
        // If we failed to set the fragment manager from the support lib, set it from
        // the android library.
        if(fragmentManager == null) {
            fragmentManager = activity.getFragmentManager();
            fragmentList = new FragmentList<android.app.Fragment>();
        }

        try {
            Class fragmentManagerImplClass = fragmentManager.getClass();
            Field activeField = fragmentManagerImplClass.getDeclaredField("mActive");
            activeField.setAccessible(true);
            activeField.set(fragmentManager, fragmentList);
            activeField.setAccessible(false);
        } catch (NoSuchFieldException ex) {
            Log.e(ProfilerService.STUDIO_PROFILER, "No Field: " + ex.getMessage());
        } catch (IllegalAccessException ex) {
            Log.e(ProfilerService.STUDIO_PROFILER, "No Access: " + ex.getMessage());
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        overrideFragmentManager(activity);
        updateCallback(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        // The user can override any of these functions and call setCallback, as such we need to update the callback
        // at each entry point.
        updateCallback(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        myActivities.add(activity);
        updateCallback(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        myActivities.remove(activity);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        myActivities.remove(activity);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    /**
     * This class is used as an injection point to capture fragment activated / deactivated
     * events. The way this is done is by using reflecting FragmentManager and replacing the
     * ArrayList<Fragment> internally with this class. We then intercept the add/set calls to
     * track the life of a fragment.
     * @param <E> This value should either be android.app.Fragment, or android.support.v4.app.Fragment.
     */
    // TODO Have fragment events get sent back to Android Studio
    private static class FragmentList<E> extends ArrayList<E> {

        @Override
        public boolean add(E fragment) {
            return super.add(fragment);
        }

        @Override
        public void add(int index, E fragment) {
            super.add(index, fragment);
        }

        @Override
        public E set(int index, E fragment) {
            return super.set(index, fragment);
        }
    }
}
