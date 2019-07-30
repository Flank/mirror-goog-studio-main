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
 * Adds initialization hooks to initialize the ProfilerService as soon as possible.
 * It looks for the application entry point objects and adds the initialize hook at the
 * end of their constructors.
 *
 * TODO: There is no API for external plugins to look at the manifest, once that is in place
 * we could just instrument whatever the manifest identifies as the entry points.
 */
final class InitializerAdapter extends ClassVisitor implements Opcodes {

    public static final String ANDROID_APPLICATION = "android/app/Application";
    public static final String ANDROID_ACTIVITY = "android/app/Activity";
    public static final String PROFILER_APPLICATION_CLASSNAME
            = "com/android/tools/profiler/support/ProfilerService";
    private static final String SERVICE_ADDRESS_PROPERTY = "profiler.service.address";
    private String superName;
    private final boolean myUnifiedPipeline;

    public InitializerAdapter(ClassVisitor classVisitor, boolean unifiedPipeline) {
        super(ASM5, classVisitor);
        myUnifiedPipeline = unifiedPipeline;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.superName = superName;

    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (superName.equals(ANDROID_ACTIVITY) ||
            superName.equals(ANDROID_APPLICATION)) {
            if (name.equals("<init>")) {
                return new MethodAdapter(mv, myUnifiedPipeline);
            }
        }
        return mv;
    }

    private static final class MethodAdapter extends MethodVisitor implements Opcodes {
        private final boolean myUnifiedPipeline;

        public MethodAdapter(MethodVisitor mv, boolean unifiedPipeline) {
            super(ASM5, mv);
            myUnifiedPipeline = unifiedPipeline;
        }

        @Override
        public void visitInsn(int opcode) {
            switch (opcode) {
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case RETURN:
                    super.visitLdcInsn(SERVICE_ADDRESS_PROPERTY);
                    super.visitLdcInsn(myUnifiedPipeline ? 1 : 0);
                    super.visitMethodInsn(
                            INVOKESTATIC,
                            PROFILER_APPLICATION_CLASSNAME,
                            "initialize",
                            "(Ljava/lang/String;Z)V",
                            false);
            }
            super.visitInsn(opcode);
        }
    }
}
