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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class UserClassAdapter extends ClassVisitor implements Opcodes {
  public static final String ANDROID_APP_PREFIX = "android/app";
  public static final String ANDROID_SUPPORT_PREFIX = "android/support";
  public static final String ANDROID_APP_ACTIVITY = "android/app/Activity";
  public static final String ANDROID_SUPPORT_V4_APP_FRAGMENT = "android/support/v4/app/Fragment";
  public static final String ANDROID_APP_FRAGMENT = "android/app/Fragment";

  private static final HashSet<String> ACTIVITY_METHOD_SET = new HashSet<>();
  private static final HashSet<String> FRAGMENT_METHOD_SET = new HashSet<>();

  public static final String INSTRUMENTED_METHOD_DESCRIPTOR =
      "(Ljava/lang/Object;Ljava/lang/String;)V";
  private static final String INSTRUMENTED_FRAGMENT_METHOD_OWNER_CLASS =
      "com/android/tools/profiler/support/fragment/FragmentWrapper";
  private static final String INSTRUMENTED_ACTIVITY_METHOD_OWNER_CLASS =
      "com/android/tools/profiler/support/activity/ActivityWrapper";

  private static final String ACTIVITY_ON_CREATE = "onCreate";
  private static final String ACTIVITY_ON_START = "onStart";
  private static final String ACTIVITY_ON_RESUME = "onResume";
  private static final String ACTIVITY_ON_PAUSE = "onPause";
  private static final String ACTIVITY_ON_STOP = "onStop";
  private static final String ACTIVITY_ON_DESTROY = "onDestroy";
  private static final String ACTIVITY_ON_RESTART = "onRestart";

  private static final String FRAGMENT_ON_ATTACH = "onAttach";
  private static final String FRAGMENT_ON_CREATE = "onCreate";
  private static final String FRAGMENT_ON_CREATE_VIEW = "onCreateView";
  private static final String FRAGMENT_ON_ACTIVITY_CREATED = "onActivityCreated";
  private static final String FRAGMENT_ON_START = "onStart";
  private static final String FRAGMENT_ON_RESUME = "onResume";
  private static final String FRAGMENT_ON_PAUSE = "onPause";
  private static final String FRAGMENT_ON_STOP = "onStop";
  private static final String FRAGMENT_ON_DESTROY_VIEW = "onDestroyView";
  private static final String FRAGMENT_ON_DESTROY = "onDestroy";
  private static final String FRAGMENT_ON_DETACH = "onDetach";

  public static final String SEND_FRAGMENT_ON_ATTACH = "sendFragmentOnAttach";
  public static final String SEND_FRAGMENT_ON_CREATE = "sendFragmentOnCreate";
  public static final String SEND_FRAGMENT_ON_CREATE_VIEW = "sendFragmentOnCreateView";
  public static final String SEND_FRAGMENT_ON_ACTIVITY_CREATED = "sendFragmentOnActivityCreated";
  public static final String SEND_FRAGMENT_ON_START = "sendFragmentOnStart";
  public static final String SEND_FRAGMENT_ON_RESUME = "sendFragmentOnResume";
  public static final String SEND_FRAGMENT_ON_PAUSE = "sendFragmentOnPause";
  public static final String SEND_FRAGMENT_ON_STOP = "sendFragmentOnStop";
  public static final String SEND_FRAGMENT_ON_DESTROY_VIEW = "sendFragmentOnDestroyView";
  public static final String SEND_FRAGMENT_ON_DESTROY = "sendFragmentOnDestroy";
  public static final String SEND_FRAGMENT_ON_DETACH = "sendFragmentOnDetach";

  private static final String SEND_ACTIVITY_ON_CREATE = "sendActivityOnCreate";
  private static final String SEND_ACTIVITY_ON_START = "sendActivityOnStart";
  private static final String SEND_ACTIVITY_ON_RESUME = "sendActivityOnResume";
  private static final String SEND_ACTIVITY_ON_PAUSE = "sendActivityOnPause";
  private static final String SEND_ACTIVITY_ON_STOP = "sendActivityOnStop";
  private static final String SEND_ACTIVITY_ON_DESTROY = "sendActivityOnDestroy";
  private static final String SEND_ACTIVITY_ON_RESTART = "sendActivityOnRestart";

  private HashMap<String, Set<String>> myActivityMethodRecord;
  private HashMap<String, Set<String>> myFragmentMethodRecord;
  private String currentVisitingClass;

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {

    super.visit(version, access, name, signature, superName, interfaces);
    this.currentVisitingClass = name;
  }

  public UserClassAdapter(ClassVisitor cv) {
    super(ASM5, cv);
    /**
     * Map activity object to set of existed method.
     */
    this.myActivityMethodRecord = new HashMap<>();

    /**
     * Map fragment object to set of existed method.
     */
    this.myFragmentMethodRecord = new HashMap<>();

    ACTIVITY_METHOD_SET.add(ACTIVITY_ON_CREATE);
    ACTIVITY_METHOD_SET.add(ACTIVITY_ON_START);
    ACTIVITY_METHOD_SET.add(ACTIVITY_ON_RESUME);
    ACTIVITY_METHOD_SET.add(ACTIVITY_ON_PAUSE);
    ACTIVITY_METHOD_SET.add(ACTIVITY_ON_STOP);
    ACTIVITY_METHOD_SET.add(ACTIVITY_ON_DESTROY);
    ACTIVITY_METHOD_SET.add(ACTIVITY_ON_RESTART);

    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_ATTACH);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_CREATE);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_CREATE_VIEW);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_ACTIVITY_CREATED);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_START);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_RESUME);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_PAUSE);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_STOP);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_DESTROY_VIEW);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_DESTROY);
    FRAGMENT_METHOD_SET.add(FRAGMENT_ON_DETACH);
  }

  @Override
  public MethodVisitor visitMethod(
      int access,
      String methodName,
      String methodDescriptor,
      String signature,
      String[] exceptions) {

    MethodVisitor mv =
        super.visitMethod(access, methodName, methodDescriptor, signature, exceptions);
    if (this.currentVisitingClass.startsWith(ANDROID_SUPPORT_PREFIX)
        || this.currentVisitingClass.startsWith(ANDROID_APP_PREFIX)) {
      return mv;
    }

    String queryResult = ComponentInheritanceUtils.getComponentAncestor(currentVisitingClass);
    if (queryResult == null) {
      return mv;
    }

    if (queryResult.equals(ANDROID_APP_ACTIVITY) && ACTIVITY_METHOD_SET.contains(methodName)) {
      if (!this.myActivityMethodRecord.containsKey(currentVisitingClass)) {
        this.myActivityMethodRecord.put(currentVisitingClass, new HashSet<>());
      }
      this.myActivityMethodRecord.get(currentVisitingClass).add(methodName);
      return new ActivityMethodInstrumenter(mv, methodName, currentVisitingClass);
    } else if ((queryResult.equals(ANDROID_SUPPORT_V4_APP_FRAGMENT))
        && FRAGMENT_METHOD_SET.contains(methodName)) {
      if (!this.myFragmentMethodRecord.containsKey(currentVisitingClass)) {
        this.myFragmentMethodRecord.put(currentVisitingClass, new HashSet<>());
      }
      this.myFragmentMethodRecord.get(currentVisitingClass).add(methodName);
      return new FragmentMethodInstrumenter(mv, methodName, currentVisitingClass, true);
    } else {
      if (!this.myFragmentMethodRecord.containsKey(currentVisitingClass)) {
        this.myFragmentMethodRecord.put(currentVisitingClass, new HashSet<>());
      }
      this.myFragmentMethodRecord.get(currentVisitingClass).add(methodName);
      return new FragmentMethodInstrumenter(mv, methodName, currentVisitingClass, false);
    }
  }

  private static final class ActivityMethodInstrumenter extends MethodVisitor implements Opcodes {
    private String methodName;
    private String currentVisitingClass;

    public ActivityMethodInstrumenter(
        MethodVisitor mv, String methodName, String currentVisitingClass) {
      super(ASM5, mv);
      this.methodName = methodName;
      this.currentVisitingClass = currentVisitingClass;
    }

    @Override
    public void visitCode() {
      super.visitCode();
      switch (methodName) {
        case ACTIVITY_ON_CREATE:
          visitMethodInsnActivityWrapper(mv, SEND_ACTIVITY_ON_CREATE, currentVisitingClass);
          break;
        case ACTIVITY_ON_START:
          visitMethodInsnActivityWrapper(mv, SEND_ACTIVITY_ON_START, currentVisitingClass);
          break;
        case ACTIVITY_ON_RESUME:
          visitMethodInsnActivityWrapper(mv, SEND_ACTIVITY_ON_RESUME, currentVisitingClass);
          break;
        case ACTIVITY_ON_PAUSE:
          visitMethodInsnActivityWrapper(mv, SEND_ACTIVITY_ON_PAUSE, currentVisitingClass);
          break;
        case ACTIVITY_ON_STOP:
          visitMethodInsnActivityWrapper(mv, SEND_ACTIVITY_ON_STOP, currentVisitingClass);
          break;
        case ACTIVITY_ON_DESTROY:
          visitMethodInsnActivityWrapper(mv, SEND_ACTIVITY_ON_DESTROY, currentVisitingClass);
          break;
        case ACTIVITY_ON_RESTART:
          visitMethodInsnActivityWrapper(mv, SEND_ACTIVITY_ON_RESTART, currentVisitingClass);
          break;
      }
    }
  }

  private static final class FragmentMethodInstrumenter extends MethodVisitor implements Opcodes {
    private String methodName;
    private String currentVisitingClass;
    private boolean myIsSupportLib;

    public FragmentMethodInstrumenter(
        MethodVisitor mv, String methodName, String currentVisitingClass, boolean isSupport) {
      super(ASM5, mv);
      this.methodName = methodName;
      this.currentVisitingClass = currentVisitingClass;
      this.myIsSupportLib = isSupport;
    }

    @Override
    public void visitCode() {
      super.visitCode();
      switch (methodName) {
        case FRAGMENT_ON_ATTACH:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_ATTACH, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_CREATE:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_CREATE, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_CREATE_VIEW:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_CREATE_VIEW, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_ACTIVITY_CREATED:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_ACTIVITY_CREATED, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_START:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_START, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_RESUME:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_RESUME, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_PAUSE:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_PAUSE, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_STOP:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_STOP, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_DESTROY_VIEW:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_DESTROY_VIEW, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_DESTROY:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_DESTROY, currentVisitingClass, myIsSupportLib);
          break;
        case FRAGMENT_ON_DETACH:
          visitMethodInsnFragmentWrapper(
              mv, SEND_FRAGMENT_ON_DETACH, currentVisitingClass, myIsSupportLib);
          break;
      }
    }
  }

  /**
   * This method will insert the following line:
   *     ActivityWrapper.instrumentedMethodName(this, this.getLocalClassName())
   * to the very beginning of the instrumented method.
   * @param mv Method Visitor
   * @param instrumentedMethodName sendActivityRestarted, sendActivityStarted etc
   * @param currentVisitingClass The name of current visiting class
   */
  private static void visitMethodInsnActivityWrapper(
      MethodVisitor mv, String instrumentedMethodName, String currentVisitingClass) {
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(
        INVOKEVIRTUAL, currentVisitingClass, "getLocalClassName", "()Ljava/lang/String;", false);
    mv.visitMethodInsn(
        INVOKESTATIC,
        INSTRUMENTED_ACTIVITY_METHOD_OWNER_CLASS,
        instrumentedMethodName,
        INSTRUMENTED_METHOD_DESCRIPTOR,
        false);
  }

  /**
   * This method will insert the following line:
   *     FragmentWrapper.methodName(this, this.getActivity().getLocalClassName())
   * to the very beginning of the instrumented method.
   * @param mv Method Visitor
   * @param instrumentedMethodName sendFragmentOnCreate, sendFragmentOnStart etc
   * @param currentVisitingClass The name of current visiting class
   * @param isSupportLib If current fragment class is derived from android.support.v4.app.Fragment
   *                     set this field to true, else (current fragment class derived
   *                     from android.app.Fragment) set this field to false
   */
  private static void visitMethodInsnFragmentWrapper(
      MethodVisitor mv,
      String instrumentedMethodName,
      String currentVisitingClass,
      boolean isSupportLib) {
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ALOAD, 0);
    if (isSupportLib) {
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          currentVisitingClass,
          "getActivity",
          "()Landroid/support/v4/app/FragmentActivity;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/support/v4/app/FragmentActivity",
          "getLocalClassName",
          "()Ljava/lang/String;",
          false);
    } else {
      mv.visitMethodInsn(
          INVOKEVIRTUAL, currentVisitingClass, "getActivity", "()Landroid/app/Activity;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/app/Activity",
          "getLocalClassName",
          "()Ljava/lang/String;",
          false);
    }
    mv.visitMethodInsn(
        INVOKESTATIC,
        INSTRUMENTED_FRAGMENT_METHOD_OWNER_CLASS,
        instrumentedMethodName,
        INSTRUMENTED_METHOD_DESCRIPTOR,
        false);
  }
}
