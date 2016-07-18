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

package com.android.tools.profiler.support.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

public class FragmentWrapper {
  private static final String ID_SPLITTER = ".";

  /**
   * Use reflection to get the following class to judge if current user class
   * derived from system's fragment class.
   */
  private static final String ANDROID_APP_FRAGMENT = "android.app.Fragment";
  private static final String SUPPORT_V4_FRAGMENT = "android.support.v4.app.Fragment";
  private static final String SUPPORT_V7_FRAGMENT = "android.support.v7.app.Fragment";

  /**
   * Use reflection to execute the following method.
   */
  private static final String GET_ACTIVITY_METHOD = "getActivity";
  private static final String GET_LOCAL_CLASS_NAME_METHOD = "getLocalClassName";

  /**
   * Fragment lifecycle states name.
   * Please refer https://developer.android.com/guide/components/fragments.html for more details.
   */
  private static final String ON_ATTACH_STATE = "onAttach";
  private static final String ON_CREATE_STATE = "onCreate";
  private static final String ON_CREATE_VIEW_STATE = "onCreateView";
  private static final String ON_ACTIVITY_CREATED_STATE = "onActivityCreated";
  private static final String ON_START_STATE = "onStart";
  private static final String ON_RESUME_STATE = "onResume";
  private static final String ON_PAUSE_STATE = "onPause";
  private static final String ON_STOP_STATE = "onStop";
  private static final String ON_DESTROY_VIEW_STATE = "onDestroyView";
  private static final String ON_DESTROY_STATE = "onDestroy";
  private static final String ON_DETACH_STATE = "onDetach";

  /**
   * The JNI functions to send fragment lifecycle data.
   * Defined at: tools/base/profiler/native/perfa/support/event_passthrough.cc
   */
  private static native void sendFragmentAttached(String name, int hashCode);
  private static native void sendFragmentCreated(String name, int hashCode);
  private static native void sendFragmentCreatedView(String name, int hashCode);
  private static native void sendFragmentActivityCreated(String name, int hashCode);
  private static native void sendFragmentStarted(String name, int hashCode);
  private static native void sendFragmentResumed(String name, int hashCode);
  private static native void sendFragmentPaused(String name, int hashCode);
  private static native void sendFragmentStopped(String name, int hashCode);
  private static native void sendFragmentDestroyedView(String name, int hashCode);
  private static native void sendFragmentDestroyed(String name, int hashCode);
  private static native void sendFragmentDetached(String name, int hashCode);

  /**
   * Record the last seen state of the current fragment object.
   */
  private static HashMap<Integer, String> lastState = new HashMap<Integer, String>();

  /**
   * Without importing the class, the typecasting cannot be done.
   *   [The return type of Class.forName() is Class<?>]
   * Using reflection to invoke the "getActivity()" method and "getLocalClassName()" method
   */
  private static String buildFragmentName(Object fragObject) {
    Method getActivityMethod = null;
    Object nestedActivity = null;
    Method getLocalClassNameMethod = null;
    Object nestedLocalClassName = null;
    String fragmentClassName = null;
    int fragmentObjectHashCode;
    StringBuilder mStringBuilder;

    /**
     * Get method "getActivity()" from this fragObject firstly
     * then invoke it to get nested class.
     */
    try {
      getActivityMethod = fragObject.getClass().getMethod(GET_ACTIVITY_METHOD);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
      System.exit(-1);
    }

    try {
      nestedActivity = getActivityMethod.invoke(fragObject);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
      System.exit(-1);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
      System.exit(-1);
    }

    /**
     * Get method "getLocalClassName()" from this activity object firstly
     * Then invoke it to get the local class name of this activity.
     */
    try {
      getLocalClassNameMethod = nestedActivity.getClass().getMethod(GET_LOCAL_CLASS_NAME_METHOD);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
      System.exit(-1);
    }

    try {
      nestedLocalClassName = getLocalClassNameMethod.invoke(nestedActivity);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
      System.exit(-1);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
      System.exit(-1);
    }

    /**
     * Get current fragment's class name and the hashcode of this fragment object.
     */
    fragmentClassName = fragObject.getClass().getSimpleName();
    fragmentObjectHashCode = fragObject.hashCode();

    /**
     * Build the data
     * For fragment the data format is:
     *   ActivityName.fragmentClassName454545
     *   The last digits represents the hashcode of the fragment object
     *   The ActivityName is packageName.activityClassName
     */
    mStringBuilder = new StringBuilder();
    mStringBuilder
        .append(nestedLocalClassName)
        .append(ID_SPLITTER)
        .append(fragmentClassName)
        .append(fragmentObjectHashCode);

    return mStringBuilder.toString();
  }

  /**
   * TODO: Combine the common code of sendFragmentAttached(), sendFragmentCreated(), etc
   *       into one routine. Use the switch statements to decide which JNI function
   *       should be called.
   */
  public static void sendFragmentAttached(Object obj) {
    /**
     * Make sure the fragment's lifecycle state will be sent only once.
     */
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_ATTACH_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentAttached(name, obj.hashCode());
    /**
     * Update the hash table to record the last seen state of current fragment object.
     */
    lastState.put(obj.hashCode(), ON_ATTACH_STATE);
  }

  public static void sendFragmentCreated(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_CREATE_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentCreated(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_CREATE_STATE);
  }

  public static void sendFragmentCreatedView(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_CREATE_VIEW_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentCreatedView(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_CREATE_VIEW_STATE);
  }

  public static void sendFragmentActivityCreated(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_ACTIVITY_CREATED_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentActivityCreated(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_ACTIVITY_CREATED_STATE);
  }

  public static void sendFragmentStarted(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_START_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentStarted(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_START_STATE);
  }

  public static void sendFragmentResumed(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_RESUME_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentResumed(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_RESUME_STATE);
  }

  public static void sendFragmentPaused(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_PAUSE_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentPaused(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_PAUSE_STATE);
  }

  public static void sendFragmentStopped(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_STOP_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentStopped(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_STOP_STATE);
  }

  public static void sendFragmentDestroyedView(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_DESTROY_VIEW_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentDestroyedView(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_DESTROY_VIEW_STATE);
  }

  public static void sendFragmentDestroyed(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_DESTROY_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentDestroyed(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_DESTROY_STATE);
  }

  public static void sendFragmentDetached(Object obj) {
    if (lastState.containsKey(obj.hashCode())
        && lastState.get(obj.hashCode()).equals(ON_DETACH_STATE)) {
      return;
    }
    String name = buildFragmentName(obj);
    sendFragmentDetached(name, obj.hashCode());
    lastState.put(obj.hashCode(), ON_DETACH_STATE);
  }
}
