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
 * Wraps all instances of window.getCallback/setCallback with our own version.
 */
final class EventAdapter extends ClassVisitor implements Opcodes {

    private static final String WINDOW_CLASS = "android/view/Window";
    private static final String SET_CALLBACK_DESCRIPTOR = "(Landroid/view/Window$Callback;)V";
    private static final String GET_CALLBACK_DESCRIPTOR = "()Landroid/view/Window$Callback;";
    private static final String WRAPPER_CLASS = "com/android/tools/profiler/support/event/EventWrapper";
    private static final String ANDROID_SUPPORT_LIB = "android/support/";
    private String mCurrentClassName;
    EventAdapter(ClassVisitor classVisitor) {
        super(ASM5, classVisitor);
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

        // Don't instrument the support library else we will corrupt the stack due to introducing
        // a symbol into a library that doesn't have the support lib as a reference.
        if (mCurrentClassName.startsWith(ANDROID_SUPPORT_LIB)) {
            return mv;
        }

        return (mv != null) ? new MethodAdapter(mv) : null;
    }

    private static final class MethodAdapter extends MethodVisitor implements Opcodes {

        public MethodAdapter(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {

            if (opcode != INVOKEVIRTUAL || !owner.equals(WINDOW_CLASS)) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }

            assert !itf;
            if (name.equals("setCallback") && desc.equals(SET_CALLBACK_DESCRIPTOR)) {
                super.visitMethodInsn(INVOKESTATIC, WRAPPER_CLASS, "setCallback",
                        SET_CALLBACK_DESCRIPTOR, false);
            } else if (name.equals("getCallback") && desc.equals(GET_CALLBACK_DESCRIPTOR)) {
                super.visitMethodInsn(INVOKESTATIC, WRAPPER_CLASS, "getCallback",
                        GET_CALLBACK_DESCRIPTOR, false);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }
}
