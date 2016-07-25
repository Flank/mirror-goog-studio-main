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

import java.util.HashSet;

/**
 * This Fragment Adapter will do the instrumentation on android's fragment's lifecycle functions.
 *     When the instrumented code executes, the inserted call will send the fragment
 *     data to perfd through a JNI function.
 *
 * The lifecycle states including: onAttach, onCreate, onCreateView,
 * onActivityCreated, onStart, onResume, onPause, onStop, onDestroyView, onDestroy, onDetach
 * Please refer https://developer.android.com/guide/components/fragments.html for more details.
 *
 * The target of this adapter is to get fragment lifecycle data without
 * modifying user's source code.
 */
public class FragmentAdapter extends ClassVisitor implements Opcodes {

  private static final String ANDROID_APP_FRAGMENT = "android/app/Fragment";
  private static final String ANDROID_SUPPORT_V4_FRAGMENT = "android/support/v4/app/Fragment";
  private static final String ANDROID_SUPPORT_V7_FRAGMENT = "android/support/v7/app/Fragment";

  private static final String ON_ATTACH = "onAttach";
  private static final String ON_CREATE = "onCreate";
  private static final String ON_CREATE_VIEW = "onCreateView";
  private static final String ON_ACTIVITY_CREATED = "onActivityCreated";
  private static final String ON_START = "onStart";
  private static final String ON_RESUME = "onResume";
  private static final String ON_PAUSE = "onPause";
  private static final String ON_STOP = "onStop";
  private static final String ON_DESTROY_VIEW = "onDestroyView";
  private static final String ON_DESTROY = "onDestroy";
  private static final String ON_DETACH = "onDetach";

  /**
   * The class which will provide the instrument methods.
   */
  private static final String WRAPPER_CLASS =
      "com/android/tools/profiler/support/fragment/FragmentWrapper";

  /**
   * The descriptor of the instrumented methods.
   */
  private static final String INSTRUMENTED_METHOD_DESCRIPTOR = "(Ljava/lang/Object;)V";

  /**
   * Instrumented methods name.
   */
  private static final String SEND_FRAGMENT_ATTACHED = "sendFragmentAttached";
  private static final String SEND_FRAGMENT_CREATED = "sendFragmentCreated";
  private static final String SEND_FRAGMENT_CREATED_VIEW = "sendFragmentCreatedView";
  private static final String SEND_FRAGMENT_ACTIVITY_CREATED = "sendFragmentActivityCreated";
  private static final String SEND_FRAGMENT_STARTED = "sendFragmentStarted";
  private static final String SEND_FRAGMENT_RESUMED = "sendFragmentResumed";
  private static final String SEND_FRAGMENT_PAUSED = "sendFragmentPaused";
  private static final String SEND_FRAGMENT_STOPPED = "sendFragmentStopped";
  private static final String SEND_FRAGMENT_DESTROYED_VIEW = "sendFragmentDestroyedView";
  private static final String SEND_FRAGMENT_DESTROYED = "sendFragmentDestroyed";
  private static final String SEND_FRAGMENT_DETACHED = "sendFragmentDetached";

  private static final HashSet<String> FRAGMENT_PACKAGE_SET = new HashSet<>();
  private static final HashSet<String> LIFECYCLE_METHOD_SET = new HashSet<>();

  private String mCurrentVisitingClassName;

  public FragmentAdapter(ClassVisitor cv) {
    super(ASM5, cv);

    /**
     * Adding all fragment libraries into FRAGMENT_PACKAGE_SET.
     * Currently the class visitor will not go through android.app.Fragment class
     */
    FRAGMENT_PACKAGE_SET.add(ANDROID_APP_FRAGMENT);
    FRAGMENT_PACKAGE_SET.add(ANDROID_SUPPORT_V4_FRAGMENT);
    FRAGMENT_PACKAGE_SET.add(ANDROID_SUPPORT_V7_FRAGMENT);

    /**
     * Adding fragment lifecycle states into LIFECYCLE_METHOD_SET.
     * Currently only focus on the following 11 lifecycle states.
     */
    LIFECYCLE_METHOD_SET.add(ON_ATTACH);
    LIFECYCLE_METHOD_SET.add(ON_CREATE);
    LIFECYCLE_METHOD_SET.add(ON_CREATE_VIEW);
    LIFECYCLE_METHOD_SET.add(ON_ACTIVITY_CREATED);
    LIFECYCLE_METHOD_SET.add(ON_START);
    LIFECYCLE_METHOD_SET.add(ON_RESUME);
    LIFECYCLE_METHOD_SET.add(ON_PAUSE);
    LIFECYCLE_METHOD_SET.add(ON_STOP);
    LIFECYCLE_METHOD_SET.add(ON_DESTROY_VIEW);
    LIFECYCLE_METHOD_SET.add(ON_DESTROY);
    LIFECYCLE_METHOD_SET.add(ON_DETACH);
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    if (cv != null) {
      this.mCurrentVisitingClassName = name;

      /**
       * TODO: Skip the classes that don't belong to FRAGMENT_PACKAGE_SET
       */
      cv.visit(version, access, name, signature, superName, interfaces);
    }
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

    /**
     * If current method belongs to our targets. Create the method visitor and do instrumentation.
     */
    if (FRAGMENT_PACKAGE_SET.contains(mCurrentVisitingClassName)
        && LIFECYCLE_METHOD_SET.contains(name)
        && mv != null) {
      return new MethodInstrumenter(mv, name);
    } else {
      return mv;
    }
  }

  /**
   * The method visitor which do the instrumentation on our target function.
   */
  private static final class MethodInstrumenter extends MethodVisitor implements Opcodes {
    public MethodInstrumenter(MethodVisitor mv, String name) {
      super(ASM5, mv);
      this.methodName = name;
    }

    /**
     * visitCode will be executed once the method visitor enter the function.
     */
    @Override
    public void visitCode() {
      super.visitCode();

      /**
       * Loading the parameter 'this' into the stack.
       * Will be passed to the instrumented method as the parameter.
       * The parameter 0 means the first parameter in the local stack of the function
       * we are visiting, i.e. 'this'.
       */
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      switch (this.methodName) {
        case ON_ATTACH:
          visitMethodInsnWrapper(SEND_FRAGMENT_ATTACHED);
          break;
        case ON_CREATE:
          visitMethodInsnWrapper(SEND_FRAGMENT_CREATED);
          break;
        case ON_CREATE_VIEW:
          visitMethodInsnWrapper(SEND_FRAGMENT_CREATED_VIEW);
          break;
        case ON_ACTIVITY_CREATED:
          visitMethodInsnWrapper(SEND_FRAGMENT_ACTIVITY_CREATED);
          break;
        case ON_START:
          visitMethodInsnWrapper(SEND_FRAGMENT_STARTED);
          break;
        case ON_RESUME:
          visitMethodInsnWrapper(SEND_FRAGMENT_RESUMED);
          break;
        case ON_PAUSE:
          visitMethodInsnWrapper(SEND_FRAGMENT_PAUSED);
          break;
        case ON_STOP:
          visitMethodInsnWrapper(SEND_FRAGMENT_STOPPED);
          break;
        case ON_DESTROY_VIEW:
          visitMethodInsnWrapper(SEND_FRAGMENT_DESTROYED_VIEW);
          break;
        case ON_DESTROY:
          visitMethodInsnWrapper(SEND_FRAGMENT_DESTROYED);
          break;
        case ON_DETACH:
          visitMethodInsnWrapper(SEND_FRAGMENT_DETACHED);
          break;
      }
    }

    /**
     * The name of current visiting name;
     */
    private String methodName;

    /**
     * Usage:
     *   The wrapper function which simplifies the API of visitMethodInsn in ASM library.
     * @param instrumentedMethodName The name of the method we want to instrument.
     */
    private void visitMethodInsnWrapper(String instrumentedMethodName) {
      /**
       * Invoke the static function to do the instrumentation.
       * INVOKESTATIC: The instrumented function is a static function.
       * WRAPPER_CLASS: The owner of the instrumented function.
       * instrumentedMethodName: The name of the method we want to instrument.
       * INSTRUMENTED_METHOD_DESCRIPTOR: The descriptor of the instrumented function.
       * false: The owner of the instrumented function is not an interface.
       */
      super.visitMethodInsn(
          INVOKESTATIC,
          WRAPPER_CLASS,
          instrumentedMethodName,
          INSTRUMENTED_METHOD_DESCRIPTOR,
          false);
    }
  }
}
