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

package com.android.tools.profiler.support.activity;

import java.util.HashMap;

public class ActivityWrapper {
  private static final String ACTIVITY_ON_CREATE = "onCreate";
  private static final String ACTIVITY_ON_START = "onStart";
  private static final String ACTIVITY_ON_RESUME = "onResume";
  private static final String ACTIVITY_ON_PAUSE = "onPause";
  private static final String ACTIVITY_ON_STOP = "onStop";
  private static final String ACTIVITY_ON_DESTROY = "onDestroy";
  private static final String ACTIVITY_ON_RESTART = "onRestart";

  private static native void sendActivityOnRestart(String name, int hashCode);

  private static HashMap<Integer, String> lastState = new HashMap<Integer, String>();

  public static void sendActivityOnCreate(Object object, String name) {
    /**
     * The android.app.Activity has been imported, do the typecasting instead of reflection.
     */
    lastState.put(object.hashCode(), ACTIVITY_ON_CREATE);
  }

  public static void sendActivityOnStart(Object object, String name) {
    lastState.put(object.hashCode(), ACTIVITY_ON_START);
  }

  public static void sendActivityOnResume(Object object, String name) {
    lastState.put(object.hashCode(), ACTIVITY_ON_RESUME);
  }

  public static void sendActivityOnPause(Object object, String name) {
    lastState.put(object.hashCode(), ACTIVITY_ON_PAUSE);
  }

  public static void sendActivityOnStop(Object object, String name) {
    lastState.put(object.hashCode(), ACTIVITY_ON_STOP);
  }

  public static void sendActivityOnDestroy(Object object, String name) {
    lastState.put(object.hashCode(), ACTIVITY_ON_DESTROY);
  }

  public static void sendActivityOnRestart(Object object, String name) {
    if (lastState.containsKey(object.hashCode())
        && lastState.get(object.hashCode()).equals(ACTIVITY_ON_RESTART)) {
      return;
    }
    sendActivityOnRestart(name, object.hashCode());
    lastState.put(object.hashCode(), ACTIVITY_ON_RESTART);
  }
}
