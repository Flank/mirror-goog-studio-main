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

import android.app.*;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import com.android.tools.profiler.support.ProfilerService;
import com.android.tools.profiler.support.event.InputConnectionWrapper;
import com.android.tools.profiler.support.event.WindowProfilerCallback;
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

    private static final int UNINITIALIZED_ROTATION = -1;
    private Set<Activity> myActivities = new HashSet<Activity>();
    private Class mSupportLibFragment;
    private int myCurrentRotation = UNINITIALIZED_ROTATION;

    public EventProfiler() {
        initialize();
        initalizeInputConnection();
    }

    // Native activity functions to send activity events to perfd.
    // TODO: Revisit how we expose enum state to native. This is consistent with
    // other profiler components, however we may want to expose the proto enum to java.
    private native void sendActivityCreated(String name, int hashCode);
    private native void sendActivityStarted(String name, int hashCode);
    private native void sendActivityResumed(String name, int hashCode);
    private native void sendActivityPaused(String name, int hashCode);
    private native void sendActivityStopped(String name, int hashCode);
    private native void sendActivitySaved(String name, int hashCode);
    private native void sendActivityDestroyed(String name, int hashCode);
    private native void sendFragmentAdded(String name, int hashCode, int activityHash);

    private native void sendFragmentRemoved(String name, int hashCode, int activityHash);
    private native void sendRotationEvent(int rotationValue);

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

    /**
     * Function to send rotation event, this function is called each time an activity is resumed.
     * Given there is no orientation changed callback we set the orientation on the first activity
     * we get, then only send an event if the orientation changes. This works because activities get
     * saved and resumed when the screen is rotated.
     */
    private void sendRotationEventIfNeeded(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        if (myCurrentRotation != rotation && myCurrentRotation != UNINITIALIZED_ROTATION) {
            sendRotationEvent(rotation);
        }
        myCurrentRotation = rotation;
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

    private void initalizeInputConnection() {
        // This setups a thread to poll for an active InputConnection, once we have one
        // We replace it with an override that acts as a passthorugh. This override allows us
        // to intercept strings / keys sent from the softkeybaord to the application.
        Thread inputConnectionPoller = new Thread(new InputConnectionHandler());
        inputConnectionPoller.start();
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
                fragmentList = new FragmentList(activity.hashCode());
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
            fragmentList = new FragmentList<android.app.Fragment>(activity.hashCode());
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
        sendActivityCreated(activity.getLocalClassName(), activity.hashCode());
    }

    @Override
    public void onActivityStarted(Activity activity) {
        // The user can override any of these functions and call setCallback, as such we need to update the callback
        // at each entry point.
        updateCallback(activity);
        sendActivityStarted(activity.getLocalClassName(), activity.hashCode());
    }

    @Override
    public void onActivityResumed(Activity activity) {
        myActivities.add(activity);
        updateCallback(activity);
        sendRotationEventIfNeeded(activity);
        sendActivityResumed(activity.getLocalClassName(), activity.hashCode());
    }

    @Override
    public void onActivityPaused(Activity activity) {
        myActivities.remove(activity);
        sendActivityPaused(activity.getLocalClassName(), activity.hashCode());
    }

    @Override
    public void onActivityStopped(Activity activity) {
        myActivities.remove(activity);
        sendActivityStopped(activity.getLocalClassName(), activity.hashCode());
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        sendActivitySaved(activity.getLocalClassName(), activity.hashCode());
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        sendActivityDestroyed(activity.getLocalClassName(), activity.hashCode());
    }

    /**
     * This class is used as an injection point to capture fragment activated / deactivated
     * events. The way this is done is by using reflecting FragmentManager and replacing the
     * ArrayList<Fragment> internally with this class. We then intercept the add/set calls to
     * track the life of a fragment.
     * @param <E> This value should either be android.app.Fragment, or android.support.v4.app.Fragment.
     */
    // TODO Have fragment events get sent back to Android Studio
    private class FragmentList<E> extends ArrayList<E> {

        private final int myActivityHash;

        public FragmentList(int activityHash) {
            myActivityHash = activityHash;
        }

        @Override
        public boolean add(E fragment) {
            sendFragmentAdded(fragment.getClass().getName(), fragment.hashCode(), myActivityHash);
            return super.add(fragment);
        }

        @Override
        public void add(int index, E fragment) {
            sendFragmentAdded(fragment.getClass().getName(), fragment.hashCode(), myActivityHash);
            super.add(index, fragment);
        }

        @Override
        public E set(int index, E fragment) {
            if (fragment == null) {
                sendFragmentRemoved(
                        get(index).getClass().getName(), get(index).hashCode(), myActivityHash);
            } else {
                sendFragmentAdded(
                        fragment.getClass().getName(), fragment.hashCode(), myActivityHash);
            }
            return super.set(index, fragment);
        }
    }

    /**
     * Input connection handler is a threaded class that polls for an Inputconnection. An
     * InputConnection is only established if an editable (editorview) control is active, and the
     * softkeyboard is active.
     */
    private class InputConnectionHandler implements Runnable {
        private static final int SLEEP_TIME = 100;

        @Override
        public void run() {
            try {
                // First grab access to the InputMethodManager
                Class clazz = InputMethodManager.class;
                Method instance = clazz.getMethod("getInstance");
                instance.setAccessible(true);
                InputMethodManager imm = (InputMethodManager) instance.invoke(null);
                while (true) {
                    Thread.sleep(SLEEP_TIME);
                    // If we are accepting text that means we have an input connection
                    boolean acceptingText = imm.isAcceptingText();
                    if (acceptingText) {
                        // Grab the inputconnection wrapper internally
                        Field wrapper = clazz.getDeclaredField("mServedInputConnectionWrapper");
                        wrapper.setAccessible(true);
                        Object connection = wrapper.get(imm);

                        // Grab the lock and the input connection object
                        Class connectionWrapper = connection.getClass().getSuperclass();
                        Field lock = connectionWrapper.getDeclaredField("mLock");
                        lock.setAccessible(true);
                        Object lockObject = lock.get(connection);
                        synchronized (lockObject) {
                            Field ic = connectionWrapper.getDeclaredField("mInputConnection");
                            ic.setAccessible(true);
                            //Replace the object with a wrapper
                            Object input = ic.get(connection);
                            if (!InputConnectionWrapper.class.isInstance(input)) {
                                ic.set(
                                        connection,
                                        new InputConnectionWrapper((InputConnection) input));
                            }
                            //Clean up and set state so we don't do this more than once.
                            ic.setAccessible(false);
                        }
                        lock.setAccessible(false);
                    }
                }
            } catch (InterruptedException ex) {
                Log.e(ProfilerService.STUDIO_PROFILER, "InputConnectionHandler interrupted");
            } catch (NoSuchMethodException ex) {
                Log.e(ProfilerService.STUDIO_PROFILER, "No such method: " + ex.getMessage());
            } catch (NoSuchFieldException ex) {
                Log.e(ProfilerService.STUDIO_PROFILER, "No such field: " + ex.getMessage());
            } catch (IllegalAccessException ex) {
                Log.e(ProfilerService.STUDIO_PROFILER, "No Access: " + ex.getMessage());
            } catch (InvocationTargetException ex) {
                Log.e(ProfilerService.STUDIO_PROFILER, "Invalid object: " + ex.getMessage());
            }
        }
    }
}
