/*
 * Copyright (C) 2017 The Android Open Source Project
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

/** Wraps OkHttp library APIs to monitor networking activities. */
final class OkHttpAdapter extends ClassVisitor implements Opcodes {

    private static final String OKHTTP2_CLIENT_CLASS = "com/squareup/okhttp/OkHttpClient";
    private static final String OKHTTP2_WRAPPER =
            "com/android/tools/profiler/support/network/okhttp/OkHttp2Wrapper";
    private static final String OKHTTP3_BUILDER_CLASS = "okhttp3/OkHttpClient$Builder";
    private static final String OKHTTP3_WRAPPER =
            "com/android/tools/profiler/support/network/okhttp/OkHttp3Wrapper";

    OkHttpAdapter(ClassVisitor classVisitor) {
        super(ASM5, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return (mv != null) ? new MethodAdapter(mv) : null;
    }

    private static final class MethodAdapter extends MethodVisitor implements Opcodes {

        public MethodAdapter(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String desc, boolean itf) {
            if (owner.equals(OKHTTP3_BUILDER_CLASS) && isConstructor(opcode, name, desc) && !itf) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                super.visitInsn(DUP);
                invoke(OKHTTP3_WRAPPER, "addInterceptorToBuilder", "(Ljava/lang/Object;)V");
            } else if (owner.equals(OKHTTP2_CLIENT_CLASS)
                    && isConstructor(opcode, name, desc)
                    && !itf) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                super.visitInsn(DUP);
                invoke(OKHTTP2_WRAPPER, "addInterceptorToClient", "(Ljava/lang/Object;)V");
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }

        private static boolean isConstructor(int opcode, String name, String desc) {
            return opcode == INVOKESPECIAL && name.equals("<init>") && desc.equals("()V");
        }

        /** Invokes a static method on our wrapper class. */
        private void invoke(String wrapper, String method, String desc) {
            super.visitMethodInsn(INVOKESTATIC, wrapper, method, desc, false);
        }
    }
}
