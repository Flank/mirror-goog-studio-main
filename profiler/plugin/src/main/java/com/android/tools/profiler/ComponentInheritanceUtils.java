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

package com.android.tools.profiler;

import java.util.HashMap;
import java.util.HashSet;

/**
 * An util class to query the relationship between the class and Android's components.
 */
// TODO: (b/30409153) ASM bytecode manipulator not traversing android.jar.
// Therefore, we cannot detect the class hierarchy inside android.jar.
// For example, TabActivity is derived from android.app.Activity,
// and both are defined in android.jar. If there is a user defined class which is a subclass of
// TabActivity, it is hard for us to automatically infer it is a subclass of android.app.Activity.
// Possible Solutions:
// 1. Traverse android.jar
// 2. In our code, we maintain a list of classes that are subclasses of android.app.Activity.
// This requires maintenance every time when android.jar is updated.
public class ComponentInheritanceUtils {

  /**
   * Build inheritance tree
   */
  public static void buildInheritance() {
    /**
     * Usage:
     * Used to determine whether current class belongs to system's component class
     */
    androidComponents.clear();
    androidComponents.add(ANDROID_APP_ACTIVITY);
    androidComponents.add(ANDROID_APP_FRAGMENT);
    androidComponents.add(ANDROID_SUPPORT_V4_FRAGMENT);
    HashSet<String> visited = new HashSet<>();
    for (String derivedClassName : directInheritances.keySet()) {
      if (!visited.contains(derivedClassName)) {
        buildInheritanceHelper(derivedClassName, visited);
      }
    }
  }

  /**
   * @param derivedClassName The name of the class for querying
   * @return If current class is derived from one of the components,
   *         return the component name else return null;
   */
  public static String getComponentAncestor(String derivedClassName) {
    if (!componentInheritances.containsKey(derivedClassName)) {
      return null;
    }
    return componentInheritances.get(derivedClassName);
  }

  /**
   * @param derivedClassName The name of the class for querying
   * @return If this class has a known parent that has been already recorded
   *         via recordDirectInheritance(), return the parent class; else return null
   */
  public static String getParentClass(String derivedClassName) {
    if (!directInheritances.containsKey(derivedClassName)) {
      return null;
    }
    return directInheritances.get(derivedClassName);
  }

  /**
   * @param derivedClassName derived class
   * @param superClassName super class
   */
  public static void recordDirectInheritance(String derivedClassName, String superClassName) {
    directInheritances.put(derivedClassName, superClassName);
  }

  private static final String ANDROID_APP_ACTIVITY = "android/app/Activity";
  private static final String ANDROID_APP_FRAGMENT = "android/app/Fragment";
  private static final String ANDROID_SUPPORT_V4_FRAGMENT = "android/support/v4/app/Fragment";
  private static final HashSet<String> androidComponents = new HashSet<>();

  private static HashMap<String, String> componentInheritances = new HashMap<>();
  private static HashMap<String, String> directInheritances = new HashMap<>();

  private static void buildInheritanceHelper(String derivedClassName, HashSet<String> visited) {
    HashSet<String> currentlyVisiting = new HashSet<>();
    while (!androidComponents.contains(derivedClassName)) {
      visited.add(derivedClassName);
      currentlyVisiting.add(derivedClassName);
      if (!directInheritances.containsKey(derivedClassName)) {
        return;
      }
      derivedClassName = directInheritances.get(derivedClassName);
    }
    for (String each : currentlyVisiting) {
      componentInheritances.put(each, derivedClassName);
    }
  }
}
