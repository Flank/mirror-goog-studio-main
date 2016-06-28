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

/**
 * Redirects wake lock related operations to wake lock trackers in supportlib.
 */
final class EnergyAdapter extends ClassVisitor implements Opcodes {
    private static final String WINDOW_CLASS = "android/view/Window";
    private static final String POWER_MANAGER_CLASS = "android/os/PowerManager";
    private static final String WAKE_LOCK_CLASS = "android/os/PowerManager$WakeLock";
    private static final String WINDOW_LOGGER_CLASS
            = "com/android/tools/profiler/support/energy/WindowWakeLockTracker";
    private static final String POWER_MANAGER_LOGGER_CLASS
            = "com/android/tools/profiler/support/energy/PowerManagerWakeLockTracker";

    private String mCurrentClassName;

    EnergyAdapter(ClassVisitor classVisitor) {
        super(ASM5, classVisitor);
        mCurrentClassName = null;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        if (cv != null) {
            mCurrentClassName = name;
            cv.visit(version, access, name, signature, superName, interfaces);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        if (mv == null) {
            return null;
        }

        // Don't instrument the logger class or else it will cause an infinite loop.
        if (mCurrentClassName.equals(WINDOW_LOGGER_CLASS) ||
                mCurrentClassName.equals(POWER_MANAGER_LOGGER_CLASS)) {
            return mv;
        }

        return new RedirectWakeLockMethodsAdapter(mv);
    }

    /**
     * Finds occurrences wake lock related method calls and redirects them to wake lock tracker.
     */
    private static final class RedirectWakeLockMethodsAdapter extends MethodVisitor
            implements Opcodes {

        public RedirectWakeLockMethodsAdapter(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {

            // Logging for window (full) wake locks.
            if (opcode == INVOKEVIRTUAL && owner.equals(WINDOW_CLASS)) {
                if (name.equals("addFlags") && desc.equals("(I)V")) {
                    redirectToTracker(WINDOW_LOGGER_CLASS, "wrapAddFlags",
                            "(Landroid/view/Window;I)V");
                    return;

                } else if (name.equals("setFlags") && desc.equals("(II)V")) {
                    redirectToTracker(WINDOW_LOGGER_CLASS, "wrapSetFlags",
                            "(Landroid/view/Window;II)V");
                    return;

                } else if (name.equals("clearFlags") && desc.equals("(I)V")) {
                    redirectToTracker(WINDOW_LOGGER_CLASS, "wrapClearFlags",
                            "(Landroid/view/Window;I)V");
                    return;
                }
            }

            // Logging for creation of new PowerManager wake locks.
            if (opcode == INVOKEVIRTUAL && owner.equals(POWER_MANAGER_CLASS) &&
                    name.equals("newWakeLock") &&
                    desc.equals("(ILjava/lang/String;)Landroid/os/PowerManager$WakeLock;")) {

                redirectToTracker(POWER_MANAGER_LOGGER_CLASS, "wrapNewWakeLock",
                        "(Landroid/os/PowerManager;ILjava/lang/String;)"
                                + "Landroid/os/PowerManager$WakeLock;");
                return;
            }

            // Logging for acquiring, releasing, and configuring PowerManager wake locks.
            if (opcode == INVOKEVIRTUAL && owner.equals(WAKE_LOCK_CLASS)) {
                if (name.equals("setReferenceCounted") && desc.equals("(Z)V")) {

                    redirectToTracker(POWER_MANAGER_LOGGER_CLASS, "wrapSetReferenceCounted",
                            "(Landroid/os/PowerManager$WakeLock;Z)V");
                    return;

                } else if (name.equals("acquire") && desc.equals("()V")) {

                    redirectToTracker(POWER_MANAGER_LOGGER_CLASS, "wrapAcquire",
                            "(Landroid/os/PowerManager$WakeLock;)V");
                    return;

                } else if (name.equals("acquire") && desc.equals("(J)V")) {

                    redirectToTracker(POWER_MANAGER_LOGGER_CLASS, "wrapAcquire",
                            "(Landroid/os/PowerManager$WakeLock;J)V");
                    return;

                } else if (name.equals("release") && desc.equals("()V")) {

                    redirectToTracker(POWER_MANAGER_LOGGER_CLASS, "wrapRelease",
                            "(Landroid/os/PowerManager$WakeLock;)V");
                    return;

                } else if (name.equals("release") && desc.equals("(I)V")) {

                    redirectToTracker(POWER_MANAGER_LOGGER_CLASS, "wrapRelease",
                            "(Landroid/os/PowerManager$WakeLock;I)V");
                    return;
                }
            }

            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        /**
         * Redirects method calls to wake lock tracker class.
         */
        private void redirectToTracker(String clazz, String method, String desc) {
            super.visitMethodInsn(INVOKESTATIC, clazz, method, desc, false);
        }
    }
}
