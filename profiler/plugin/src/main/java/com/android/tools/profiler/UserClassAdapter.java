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
import org.objectweb.asm.Label;
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

  /**
   * Lifecycle method of Activity that we care about
   */
  private static final HashSet<String> ACTIVITY_METHOD_SET = new HashSet<>();

  /**
   * Lifecycle method of Fragment that we care about
   */
  private static final HashSet<String> FRAGMENT_METHOD_SET = new HashSet<>();

  /**
   * Set of Activity/Fragment methods which have return value
   */
  private static final HashSet<String> HAS_RETURN_VALUE_METHOD_SET = new HashSet<>();

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

  /**
   * Map activity class name to the set of methods that created by user.
   */
  private HashMap<String, Set<String>> myActivityMethodRecord;

  /**
   * Map fragment class name to the set of methods that created by user.
   */
  private HashMap<String, Set<String>> myFragmentMethodRecord;

  /**
   * The name of current visiting class
   */
  private String currentVisitingClass;

  /**
   * The name of current visting method
   */
  private String currentVisitingMethodName;

  /**
   * current method visitor
   */
  private MethodVisitor currentMethodVisitor;

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
    this.myActivityMethodRecord = new HashMap<>();
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

    HAS_RETURN_VALUE_METHOD_SET.add(FRAGMENT_ON_CREATE_VIEW);
  }

  @Override
  public MethodVisitor visitMethod(
      int access,
      String methodName,
      String methodDescriptor,
      String signature,
      String[] exceptions) {

    /**
     * Update the following two fields every time when you visit a new method
     */
    this.currentVisitingMethodName = methodName;
    this.currentMethodVisitor =
        super.visitMethod(access, methodName, methodDescriptor, signature, exceptions);

    if (this.currentVisitingClass.startsWith(ANDROID_SUPPORT_PREFIX)
        || this.currentVisitingClass.startsWith(ANDROID_APP_PREFIX)) {
      return this.currentMethodVisitor;
    }

    String ancestor = ComponentInheritanceUtils.getComponentAncestor(currentVisitingClass);
    if (ancestor == null) {
      return this.currentMethodVisitor;
    }

    if (ancestor.equals(ANDROID_APP_ACTIVITY)) {
      if (!this.myActivityMethodRecord.containsKey(currentVisitingClass)) {
        this.myActivityMethodRecord.put(currentVisitingClass, new HashSet<>());
      }
      this.myActivityMethodRecord.get(currentVisitingClass).add(methodName);
      return new ActivityMethodInstrumenter();
    } else {
      /**
       * If ancestor != null && ancestor != ANDROID_APP_ACTIVITY then the ancestor could only
       * be ANDROID_APP_FRAGMENT or ANDROID_SUPPORT_FRAGMENT which means it belongs to fragment component
       */
      if (!this.myFragmentMethodRecord.containsKey(currentVisitingClass)) {
        this.myFragmentMethodRecord.put(currentVisitingClass, new HashSet<>());
      }
      this.myFragmentMethodRecord.get(currentVisitingClass).add(methodName);
      return new FragmentMethodInstrumenter();
    }
  }

  /**
   * Create the method that has not been created by user on bytecode level
   * e.g. If the user has a fragment class which don't have the method onAttach
   *      Following function will create this onAttach method on bytecode level
   */
  @Override
  public void visitEnd() {
    String ancestor = ComponentInheritanceUtils.getComponentAncestor(currentVisitingClass);
    if (currentVisitingClass.startsWith(ANDROID_APP_PREFIX)
        || currentVisitingClass.startsWith(ANDROID_SUPPORT_PREFIX)
        || ancestor == null) {
      super.visitEnd();
      return;
    }
    if (ancestor.equals(ANDROID_APP_FRAGMENT)
        || ancestor.equals(ANDROID_SUPPORT_V4_APP_FRAGMENT)) {
      for (String fragmentMethod : FRAGMENT_METHOD_SET) {
        if (myFragmentMethodRecord.containsKey(currentVisitingClass)
            && !myFragmentMethodRecord.get(currentVisitingClass).contains(fragmentMethod)) {
          switch (fragmentMethod) {
            case FRAGMENT_ON_ATTACH:
              CreateLifecycleMethod(
                  FRAGMENT_ON_ATTACH,
                  "(Landroid/content/Context;)V",
                  SEND_FRAGMENT_ON_ATTACH,
                  2,
                  2);
              break;
            case FRAGMENT_ON_CREATE:
              CreateLifecycleMethod(
                  FRAGMENT_ON_CREATE, "(Landroid/os/Bundle;)V", SEND_FRAGMENT_ON_CREATE, 2, 2);
              break;
            case FRAGMENT_ON_CREATE_VIEW:
              CreateLifecycleMethod(
                  FRAGMENT_ON_CREATE_VIEW,
                  "(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;",
                  SEND_FRAGMENT_ON_CREATE_VIEW,
                  4,
                  4);
              break;
            case FRAGMENT_ON_ACTIVITY_CREATED:
              CreateLifecycleMethod(
                  FRAGMENT_ON_ACTIVITY_CREATED,
                  "(Landroid/os/Bundle;)V",
                  SEND_FRAGMENT_ON_ACTIVITY_CREATED,
                  2,
                  2);
              break;
            case FRAGMENT_ON_START:
              CreateLifecycleMethod(FRAGMENT_ON_START, "()V", SEND_FRAGMENT_ON_START, 2, 1);
              break;
            case FRAGMENT_ON_RESUME:
              CreateLifecycleMethod(FRAGMENT_ON_RESUME, "()V", SEND_FRAGMENT_ON_RESUME, 2, 1);
              break;
            case FRAGMENT_ON_PAUSE:
              CreateLifecycleMethod(FRAGMENT_ON_PAUSE, "()V", SEND_FRAGMENT_ON_PAUSE, 2, 1);
              break;
            case FRAGMENT_ON_STOP:
              CreateLifecycleMethod(FRAGMENT_ON_STOP, "()V", SEND_FRAGMENT_ON_STOP, 2, 1);
              break;
            case FRAGMENT_ON_DESTROY_VIEW:
              CreateLifecycleMethod(
                  FRAGMENT_ON_DESTROY_VIEW, "()V", SEND_FRAGMENT_ON_DESTROY_VIEW, 2, 1);
              break;
            case FRAGMENT_ON_DESTROY:
              CreateLifecycleMethod(FRAGMENT_ON_DESTROY, "()V", SEND_FRAGMENT_ON_DESTROY, 2, 1);
              break;
            case FRAGMENT_ON_DETACH:
              CreateLifecycleMethod(FRAGMENT_ON_DETACH, "()V", SEND_FRAGMENT_ON_DETACH, 2, 1);
              break;
          }
        }
      }
    } else {
      for (String activityMethod : ACTIVITY_METHOD_SET) {
        if (myActivityMethodRecord.containsKey(currentVisitingClass)
            && !myActivityMethodRecord.get(currentVisitingClass).contains(activityMethod)) {
          switch (activityMethod) {
            case ACTIVITY_ON_CREATE:
              CreateLifecycleMethod(
                  ACTIVITY_ON_CREATE, "(Landroid/os/Bundle;)V", SEND_ACTIVITY_ON_CREATE, 2, 2);
              break;
            case ACTIVITY_ON_START:
              CreateLifecycleMethod(ACTIVITY_ON_START, "()V", SEND_ACTIVITY_ON_START, 2, 1);
              break;
            case ACTIVITY_ON_RESUME:
              CreateLifecycleMethod(ACTIVITY_ON_RESUME, "()V", SEND_ACTIVITY_ON_RESUME, 2, 1);
              break;
            case ACTIVITY_ON_PAUSE:
              CreateLifecycleMethod(ACTIVITY_ON_PAUSE, "()V", SEND_ACTIVITY_ON_PAUSE, 2, 1);
              break;
            case ACTIVITY_ON_STOP:
              CreateLifecycleMethod(ACTIVITY_ON_STOP, "()V", SEND_ACTIVITY_ON_STOP, 2, 1);
              break;
            case ACTIVITY_ON_DESTROY:
              CreateLifecycleMethod(ACTIVITY_ON_DESTROY, "()V", SEND_ACTIVITY_ON_DESTROY, 2, 1);
              break;
            case ACTIVITY_ON_RESTART:
              CreateLifecycleMethod(ACTIVITY_ON_RESTART, "()V", SEND_ACTIVITY_ON_RESTART, 2, 1);
              break;
          }
        }
      }
    }
    super.visitEnd();
  }

  private class ActivityMethodInstrumenter extends MethodVisitor implements Opcodes {

    public ActivityMethodInstrumenter() {
      super(ASM5, currentMethodVisitor);
    }

    @Override
    public void visitCode() {
      super.visitCode();
      switch (currentVisitingMethodName) {
        case ACTIVITY_ON_CREATE:
          visitMethodInsnActivityWrapper(SEND_ACTIVITY_ON_CREATE);
          break;
        case ACTIVITY_ON_START:
          visitMethodInsnActivityWrapper(SEND_ACTIVITY_ON_START);
          break;
        case ACTIVITY_ON_RESUME:
          visitMethodInsnActivityWrapper(SEND_ACTIVITY_ON_RESUME);
          break;
        case ACTIVITY_ON_PAUSE:
          visitMethodInsnActivityWrapper(SEND_ACTIVITY_ON_PAUSE);
          break;
        case ACTIVITY_ON_STOP:
          visitMethodInsnActivityWrapper(SEND_ACTIVITY_ON_STOP);
          break;
        case ACTIVITY_ON_DESTROY:
          visitMethodInsnActivityWrapper(SEND_ACTIVITY_ON_DESTROY);
          break;
        case ACTIVITY_ON_RESTART:
          visitMethodInsnActivityWrapper(SEND_ACTIVITY_ON_RESTART);
          break;
        default:
          break;
      }
    }
  }

  private class FragmentMethodInstrumenter extends MethodVisitor implements Opcodes {

    public FragmentMethodInstrumenter() {
      super(ASM5, currentMethodVisitor);
    }

    @Override
    public void visitCode() {
      super.visitCode();
      switch (currentVisitingMethodName) {
        case FRAGMENT_ON_ATTACH:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_ATTACH);
          break;
        case FRAGMENT_ON_CREATE:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_CREATE);
          break;
        case FRAGMENT_ON_CREATE_VIEW:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_CREATE_VIEW);
          break;
        case FRAGMENT_ON_ACTIVITY_CREATED:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_ACTIVITY_CREATED);
          break;
        case FRAGMENT_ON_START:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_START);
          break;
        case FRAGMENT_ON_RESUME:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_RESUME);
          break;
        case FRAGMENT_ON_PAUSE:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_PAUSE);
          break;
        case FRAGMENT_ON_STOP:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_STOP);
          break;
        case FRAGMENT_ON_DESTROY_VIEW:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_DESTROY_VIEW);
          break;
        case FRAGMENT_ON_DESTROY:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_DESTROY);
          break;
        case FRAGMENT_ON_DETACH:
          visitMethodInsnFragmentWrapper(SEND_FRAGMENT_ON_DETACH);
          break;
        default:
          break;
      }
    }
  }

  /**
   * This method will insert the following line:
   *     ActivityWrapper.payloadMethodName(this, this.getLocalClassName())
   * to the very beginning of the instrumented method.
   * @param payloadMethodName sendActivityRestarted, sendActivityStarted etc
   */
  private void visitMethodInsnActivityWrapper(String payloadMethodName) {
    currentMethodVisitor.visitVarInsn(ALOAD, 0);
    currentMethodVisitor.visitVarInsn(ALOAD, 0);
    currentMethodVisitor.visitMethodInsn(
        INVOKEVIRTUAL, currentVisitingClass, "getLocalClassName", "()Ljava/lang/String;", false);
    currentMethodVisitor.visitMethodInsn(
        INVOKESTATIC,
        INSTRUMENTED_ACTIVITY_METHOD_OWNER_CLASS,
        payloadMethodName,
        INSTRUMENTED_METHOD_DESCRIPTOR,
        false);
  }

  /**
   * This method will insert the following line:
   *     FragmentWrapper.methodName(this, this.getActivity().getLocalClassName())
   * to the very beginning of the instrumented method.
   * @param payloadMethodName sendFragmentOnCreate, sendFragmentOnStart etc
   */
  private void visitMethodInsnFragmentWrapper(String payloadMethodName) {
    currentMethodVisitor.visitVarInsn(ALOAD, 0);
    currentMethodVisitor.visitVarInsn(ALOAD, 0);

    String ancestor = ComponentInheritanceUtils.getComponentAncestor(currentVisitingClass);
    boolean isSupportFragmentComponent = true;
    if (ancestor.equals(ANDROID_APP_FRAGMENT)) {
      isSupportFragmentComponent = false;
    }

    if (isSupportFragmentComponent) {
      currentMethodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          currentVisitingClass,
          "getActivity",
          "()Landroid/support/v4/app/FragmentActivity;",
          false);
      currentMethodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/support/v4/app/FragmentActivity",
          "getLocalClassName",
          "()Ljava/lang/String;",
          false);
    } else {
      currentMethodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, currentVisitingClass, "getActivity", "()Landroid/app/Activity;", false);
      currentMethodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/app/Activity",
          "getLocalClassName",
          "()Ljava/lang/String;",
          false);
    }
    currentMethodVisitor.visitMethodInsn(
        INVOKESTATIC,
        INSTRUMENTED_FRAGMENT_METHOD_OWNER_CLASS,
        payloadMethodName,
        INSTRUMENTED_METHOD_DESCRIPTOR,
        false);
  }

  /**
   * The following function will create the following method on bytecode level
   * public createMethodName (createMethodDescriptor) {
   *     // which will call the jni function
   *     payloadMethodName(Object, String);
   *     super.createMethodName(createMethodDescriptor);
   * }
   *
   * @param createMethodName The name of method which will be created
   * @param createMethodDescriptor The descriptor of the method which will be created
   * @param payloadMethodName The method name which will be inserted
   * @param maxStack maximum stack size of the method
   * @param maxLocals maximum number of local variables for the method
   */
  private void CreateLifecycleMethod(
      String createMethodName,
      String createMethodDescriptor,
      String payloadMethodName,
      int maxStack,
      int maxLocals) {
    /**
     * Judging if the method createMethodName has return value.
     */
    boolean hasReturnValue = HAS_RETURN_VALUE_METHOD_SET.contains(createMethodName);

    String parentClassName = ComponentInheritanceUtils.getParentClass(currentVisitingClass);
    boolean isActivityComponent = false;

    String componentAncestor = ComponentInheritanceUtils.getComponentAncestor(currentVisitingClass);
    if (componentAncestor.equals(ANDROID_APP_ACTIVITY)) {
      isActivityComponent = true;
    }

    /**
     * Inserting the instrumented method at the very beginning of the function
     * Update the method visitor every time when creating a new function
     */
    currentMethodVisitor =
        cv.visitMethod(ACC_PUBLIC, createMethodName, createMethodDescriptor, null, null);
    currentMethodVisitor.visitCode();
    Label l0 = new Label();
    currentMethodVisitor.visitLabel(l0);
    if (isActivityComponent) {
      visitMethodInsnActivityWrapper(payloadMethodName);
    } else {
      visitMethodInsnFragmentWrapper(payloadMethodName);
    }

    /**
     * Calling super.createMethodName(parameter1, parameter2, parameter3...);
     */
    Label l1 = new Label();
    currentMethodVisitor.visitLabel(l1);
    for (int i = 0; i < maxLocals; i++) {
      currentMethodVisitor.visitVarInsn(ALOAD, i);
    }
    currentMethodVisitor.visitMethodInsn(
        INVOKESPECIAL, parentClassName, createMethodName, createMethodDescriptor, false);

    /**
     * The end of the method, when the method has a return value, using ARETURN
     * when the method doesn't have return value, using RETURN
     */
    if (hasReturnValue) {
      currentMethodVisitor.visitInsn(ARETURN);
      Label l2 = new Label();
      currentMethodVisitor.visitLabel(l2);
    } else {
      Label l2 = new Label();
      currentMethodVisitor.visitLabel(l2);
      currentMethodVisitor.visitInsn(RETURN);

      Label l3 = new Label();
      currentMethodVisitor.visitLabel(l3);
    }
    currentMethodVisitor.visitMaxs(maxStack, maxLocals);
    currentMethodVisitor.visitEnd();
  }
}
