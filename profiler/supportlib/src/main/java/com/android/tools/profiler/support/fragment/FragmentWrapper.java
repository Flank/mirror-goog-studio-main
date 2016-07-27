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

import java.util.HashMap;

public class FragmentWrapper {
  private static final String ID_SPLITTER = ".";

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
  private static native void sendFragmentOnAttach(String name, int hashCode);
  private static native void sendFragmentOnCreate(String name, int hashCode);
  private static native void sendFragmentOnCreateView(String name, int hashCode);
  private static native void sendFragmentOnActivityCreated(String name, int hashCode);
  private static native void sendFragmentOnStart(String name, int hashCode);
  private static native void sendFragmentOnResume(String name, int hashCode);
  private static native void sendFragmentOnPause(String name, int hashCode);
  private static native void sendFragmentOnStop(String name, int hashCode);
  private static native void sendFragmentOnDestroyView(String name, int hashCode);
  private static native void sendFragmentOnDestroy(String name, int hashCode);
  private static native void sendFragmentOnDetach(String name, int hashCode);

  /**
   * Record the last seen state of the current fragment object.
   */
  private static HashMap<Integer, String> lastState = new HashMap<Integer, String>();

  public static void sendFragmentOnAttach(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_ATTACH_STATE)) {
      return;
    }
    sendFragmentOnAttach(fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_ATTACH_STATE);
  }

  public static void sendFragmentOnCreate(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_CREATE_STATE)) {
      return;
    }
    sendFragmentOnCreate(fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_CREATE_STATE);
  }

  public static void sendFragmentOnCreateView(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_CREATE_VIEW_STATE)) {
      return;
    }
    sendFragmentOnCreateView(
        fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_CREATE_VIEW_STATE);
  }

  public static void sendFragmentOnActivityCreated(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_ACTIVITY_CREATED_STATE)) {
      return;
    }
    sendFragmentOnActivityCreated(
        fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_ACTIVITY_CREATED_STATE);
  }

  public static void sendFragmentOnStart(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_START_STATE)) {
      return;
    }
    sendFragmentOnStart(fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_START_STATE);
  }

  public static void sendFragmentOnResume(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_RESUME_STATE)) {
      return;
    }
    sendFragmentOnResume(fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_RESUME_STATE);
  }

  public static void sendFragmentOnPause(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_PAUSE_STATE)) {
      return;
    }
    sendFragmentOnPause(fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_PAUSE_STATE);
  }

  public static void sendFragmentOnStop(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_STOP_STATE)) {
      return;
    }
    sendFragmentOnStop(fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_STOP_STATE);
  }

  public static void sendFragmentOnDestroyView(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_DESTROY_VIEW_STATE)) {
      return;
    }
    sendFragmentOnDestroyView(
        fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_DESTROY_VIEW_STATE);
  }

  public static void sendFragmentOnDestroy(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_DESTROY_STATE)) {
      return;
    }
    sendFragmentOnDestroy(
        fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_DESTROY_STATE);
  }

  public static void sendFragmentOnDetach(Object fragmentObj, String nestedClassName) {
    if (lastState.containsKey(fragmentObj.hashCode())
        && lastState.get(fragmentObj.hashCode()).equals(ON_DETACH_STATE)) {
      return;
    }
    sendFragmentOnDetach(fragmentNameBuilder(fragmentObj, nestedClassName), fragmentObj.hashCode());
    lastState.put(fragmentObj.hashCode(), ON_DETACH_STATE);
  }

  private static String fragmentNameBuilder(Object fragmentObject, String nestedClassName) {
    String localClassName = fragmentObject.getClass().getSimpleName();
    int hashCode = fragmentObject.hashCode();
    StringBuilder sb = new StringBuilder();
    sb.append(nestedClassName).append(ID_SPLITTER).append(localClassName).append(hashCode);
    return sb.toString();
  }
}
