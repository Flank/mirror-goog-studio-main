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

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.Window;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import com.android.tools.profiler.support.event.InputConnectionWrapper;
import com.android.tools.profiler.support.event.WindowProfilerCallback;
import com.android.tools.profiler.support.util.StudioLog;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * EventProfiler class captures and reports all events that we track on an app. These events are
 * used for the event monitor.
 */
public class EventProfiler implements ProfilerComponent, Application.ActivityLifecycleCallbacks {

    private static final int UNINITIALIZED_ROTATION = -1;
    private static final int MAX_SLEEP_BACKOFF_MS = 500;
    private Set<Activity> myActivities = new HashSet<Activity>();
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

    private native void sendRotationEvent(int rotationValue);

    /**
     * This class handles updating the callback for any activities that are activated or created. We
     * construct a new wrapper around the callback because an application can have multiple windows.
     *
     * @param activity The activity we get the window of and set the callback on.
     */
    private void updateCallback(Activity activity) {
        //TODO Poll to verify.
        //TODO Verify the order of this is fixed, can this happen before the users onActivityCreated, or is it always
        // after?
        Window window = activity.getWindow();
        if (!WindowProfilerCallback.class.isInstance(window.getCallback())) {
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
        final EventProfiler profiler = this;

        // Setting up the initializer as a thread, we need to do this because some applications
        // may have a delay in starting the application object. If this is the case then,
        // we need to poll for the object.
        CountDownLatch latch = new CountDownLatch(1);
        ActivityInitialization initializer = new ActivityInitialization(profiler, latch);
        Thread initThread = new Thread(initializer);
        initThread.start();

        // Due to a potential race condition we should wait for atleast one thread
        // tick of our ActivityInitialization before we check the current state.
        // this will ensure that if we have an Application we are listening for state changes
        // before we check the current state.
        try {
            // wait untill latch counted down to 0
            latch.await();
        } catch (InterruptedException e) {
            StudioLog.e("Failed to block for ActivityInitialization");
        }
        captureCurrentActivityState();
    }

    private void initalizeInputConnection() {
        // This setups a thread to poll for an active InputConnection, once we have one
        // We replace it with an override that acts as a passthorugh. This override allows us
        // to intercept strings / keys sent from the softkeybaord to the application.
        Thread inputConnectionPoller = new Thread(new InputConnectionHandler());
        inputConnectionPoller.start();
    }

    // This change will look at the ActivityThread and find any stored activities.
    // If they are not paused it will send a resume event to perfd.
    // This ensures that a delayed attachment will capture the current state of the
    // world and show the proper events with JVMTI.
    private void captureCurrentActivityState() {
        try {
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread =
                    activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesObject = activitiesField.get(activityThread);
            // Verify the object is a map as we expect, if it is not log an error.
            // The map is an inernal collection of IBinder to ActivityClientRecord
            if (Map.class.isAssignableFrom(activitiesObject.getClass())) {
                Map<Object, Object> activities = (Map<Object, Object>) activitiesObject;
                for (Object activityRecord : activities.values()) {
                    Class activityRecordClass = activityRecord.getClass();
                    Field pausedField = activityRecordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        Field activityField = activityRecordClass.getDeclaredField("activity");
                        activityField.setAccessible(true);
                        Object activityObject = activityField.get(activityRecord);
                        if (activitiesObject != null
                                && activityObject instanceof Activity
                                // Due to a race condition where we add the events then check the start up state,
                                // an activity could be added before we get to this point. We are not able to put this
                                // in a lock as the activity add/remove events happen on the system thread.
                                && !myActivities.contains(activityObject)) {
                            onActivityResumed((Activity) activityObject);
                        }
                    }
                }
            } else {
                StudioLog.v(
                        String.format(
                                "Failed to assign mActivities map: %s",
                                activitiesObject.getClass()));
            }
        } catch (ClassNotFoundException ex) {
            StudioLog.e("Failed to get ActivityThread class");
        } catch (NoSuchMethodException ex) {
            StudioLog.e("Failed to find currentActivityThread method");
        } catch (IllegalAccessException ex) {
            StudioLog.e("Insufficient privileges to get activity information");
        } catch (InvocationTargetException ex) {
            StudioLog.e("Failed to call static function currentActivityThread");
        } catch (NoSuchFieldException ex) {
            StudioLog.e("Failed to get field", ex);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        updateCallback(activity);
        sendActivityCreated(activity.getLocalClassName(), activity.hashCode());
    }

    @Override
    public void onActivityStarted(Activity activity) {
        // The user can override any of these functions and call setCallback, as such we need
        // update the callback at each entry point. We do not need add this activity to
        // {@link myActivities} as the activity started is not displayed.
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
     * Input connection handler is a threaded class that polls for an Inputconnection. An
     * InputConnection is only established if an editable (editorview) control is active, and the
     * softkeyboard is active. This means the thread cannot be torn down because the soft
     * uplokeyboard state is transient and each time it is torndown the imm stops accepting text and
     * can clean up internal state.
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
                InputConnectionWrapper inputConnectionWrapper = new InputConnectionWrapper();
                while (true) {
                    Thread.sleep(SLEEP_TIME);
                    // If we are accepting text that means we have an input connection
                    boolean acceptingText = imm.isAcceptingText();
                    if (acceptingText) {
                        // Grab the inputconnection wrapper internally
                        Field wrapper = clazz.getDeclaredField("mServedInputConnectionWrapper");
                        wrapper.setAccessible(true);
                        Object connection = wrapper.get(imm);

                        // This can happen in some cases with O/P devices
                        if (connection == null) {
                            continue;
                        }

                        // Grab the lock and the input connection object
                        Class connectionWrapper = connection.getClass().getSuperclass();
                        Object lockObject = new Object();

                        // For the marshmallow OS and before they don't have a lock field.
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                            Field lock = connectionWrapper.getDeclaredField("mLock");
                            lock.setAccessible(true);
                            lockObject = lock.get(connection);
                        }

                        synchronized (lockObject) {
                            Field ic = connectionWrapper.getDeclaredField("mInputConnection");
                            ic.setAccessible(true);
                            // Replace the object with a wrapper
                            Object input = ic.get(connection);
                            if (input != null) {
                                InputConnection inputConnection = null;
                                boolean isWeakReference =
                                        input.getClass().isAssignableFrom(WeakReference.class);
                                if (isWeakReference) {
                                    inputConnection =
                                            ((WeakReference<InputConnection>) input).get();
                                } else if (InputConnection.class.isInstance(input)) {
                                    // Only set the input connection object if it is of type input connection.
                                    // on HTC Honor devices they modified the WeakReference to be a SoftReference as such
                                    // we do not want to default to this case.
                                    inputConnection = (InputConnection) input;
                                }
                                if (inputConnection != null
                                        && !InputConnectionWrapper.class.isInstance(
                                                inputConnection)) {
                                    if (isWeakReference) {
                                        // Store this instance of the wrapper on a thread local
                                        // variable this prevents the wrapper from getting cleaned
                                        // while still potentially in use. This thread does not get
                                        // terminated until the application is terminated.
                                        inputConnectionWrapper.setTarget(inputConnection);
                                        ic.set(
                                                connection,
                                                new WeakReference<InputConnection>(
                                                        inputConnectionWrapper));
                                    } else {
                                        inputConnectionWrapper.setTarget((InputConnection) input);
                                        ic.set(connection, inputConnectionWrapper);
                                    }
                                }
                            }
                            //Clean up and set state so we don't do this more than once.
                            ic.setAccessible(false);
                        }
                    }
                }
            } catch (InterruptedException ex) {
                StudioLog.e("InputConnectionHandler interrupted");
            } catch (NoSuchMethodException ex) {
                StudioLog.e("No such method", ex);
            } catch (NoSuchFieldException ex) {
                StudioLog.e("No such field", ex);
            } catch (IllegalAccessException ex) {
                StudioLog.e("No Access", ex);
            } catch (InvocationTargetException ex) {
                StudioLog.e("Invalid object", ex);
            }
        }
    }

    private class ActivityInitialization implements Runnable {
        private boolean myInitialized = false;
        private CountDownLatch myLatch;
        private final EventProfiler myProfiler;

        public ActivityInitialization(EventProfiler profiler, CountDownLatch latch) {
            myProfiler = profiler;
            myLatch = latch;
        }

        @Override
        public void run() {
            long sleepBackoffMs = 10;
            boolean logErrorOnce = false;
            while (!myInitialized) {
                try {
                    Class activityThreadClass = Class.forName("android.app.ActivityThread");
                    Application app =
                            (Application)
                                    activityThreadClass
                                            .getMethod("currentApplication")
                                            .invoke(null);
                    if (app != null) {
                        StudioLog.v("Acquiring Application for Events");
                        myInitialized = true;
                        app.registerActivityLifecycleCallbacks(myProfiler);
                        // The activity could have started before the callback is added. If this happens, the activity
                        // and touch events are not shown in the events bar.
                        myProfiler.captureCurrentActivityState();
                    } else if (!logErrorOnce) {
                        StudioLog.e("Failed to capture application");
                        logErrorOnce = true;
                    }
                    // Only tick our latch once then null it out, as we only
                    // care if we attempted to get the current application
                    // the first time.
                    if (myLatch != null) {
                        myLatch.countDown();
                        myLatch = null;
                    }

                    // If we are initialized then break the wait loop.
                    if (myInitialized) {
                        break;
                    }
                } catch (ClassNotFoundException ex) {
                    StudioLog.e("Failed to get ActivityThread class");
                } catch (NoSuchMethodException ex) {
                    StudioLog.e("Failed to find currentApplication method");
                } catch (IllegalAccessException ex) {
                    StudioLog.e("Insufficient privileges to get application handle");
                } catch (InvocationTargetException ex) {
                    StudioLog.e("Failed to call static function currentApplication");
                }

                try {
                    Thread.sleep(sleepBackoffMs);
                    if (sleepBackoffMs < MAX_SLEEP_BACKOFF_MS) {
                        sleepBackoffMs *= 2;
                    } else {
                        sleepBackoffMs = MAX_SLEEP_BACKOFF_MS;
                    }
                } catch (InterruptedException ex) {
                    // Do nothing.
                }
            }
        }
    }
}
