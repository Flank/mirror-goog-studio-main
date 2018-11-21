/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.tracer.agent;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

class TraceMethodVisitor extends AdviceAdapter {

    private final String name;
    private final String className;
    private final TraceProfile profile;
    private Label beginLabel;
    private boolean enabled;

    public TraceMethodVisitor(
            MethodVisitor mv,
            String className,
            int access,
            String name,
            String desc,
            TraceProfile profile) {
        super(Opcodes.ASM5, mv, access, name, desc);
        this.className = className;
        this.name = name;
        this.profile = profile;
        this.enabled = profile.shouldInstrument(className, name);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        enabled = enabled || desc.equals("Lcom/android/annotations/Trace;");
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        if (!enabled) {
            return;
        }
        invoke("begin", "(Ljava/lang/String;)V", buildTag(className, name));
    }

    @Override
    protected void onMethodEnter() {
        super.onMethodEnter();
        if (!enabled) {
            return;
        }
        beginLabel = newLabel();
        visitLabel(beginLabel);
    }

    @Override
    public void visitInsn(int opcode) {
        if (enabled) {
            switch (opcode) {
                case RETURN:
                case IRETURN:
                case FRETURN:
                case ARETURN:
                case LRETURN:
                case DRETURN:
                    traceEnd();
                    break;
            }
        }
        super.visitInsn(opcode);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        if (enabled) {
            Label endLabel = newLabel();
            visitTryCatchBlock(beginLabel, endLabel, endLabel, null);
            visitLabel(endLabel);
            visitFrame(F_NEW, 0, null, 1, new Object[] {"java/lang/Throwable"});
            traceEnd();
            super.throwException();
        }
        super.visitMaxs(maxStack, maxLocals);
    }

    private void traceEnd() {
        invoke("end", "()V");
        if (profile.shouldFlush(className, name)) {
            invoke("flush", "()V");
        }
    }

    private String buildTag(String className, String method) {
        int i = className.lastIndexOf('/');
        String name = i == -1 ? className : className.substring(i + 1);
        return name + "." + method;
    }

    private void invoke(String method, String desc, String... args) {
        for (String arg : args) {
            visitLdcInsn(arg);
        }
        visitMethodInsn(INVOKESTATIC, "com/android/tools/tracer/agent/Tracer", method, desc, false);
    }
}
