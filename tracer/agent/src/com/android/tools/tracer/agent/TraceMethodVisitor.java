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

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

class TraceMethodVisitor extends AdviceAdapter {

    private final String name;
    private final String className;
    private final String tag;
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
        this.tag = buildTag(className, name, desc);
        this.profile = profile;
        this.enabled = profile.shouldInstrument(className, name);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        enabled = enabled || profile.shouldInstrument(desc);
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        if (!enabled) {
            return;
        }
        if (profile.start(className, name)) {
            invoke("start", "()V");
        }
        invoke("begin", "(Ljava/lang/String;)V", tag);
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

    private static String buildTag(String className, String method, String desc) {
        // className comes as "Ljava/lang/String"
        String name = simplifyClassName(className, '/');

        // getClassName from Type, comes as "java.lang.String".
        String returnClass = simplifyClassName(Type.getReturnType(desc).getClassName(), '.');
        String args =
                Stream.of(Type.getArgumentTypes(desc))
                        .map(t -> simplifyClassName(t.getClassName(), '.'))
                        .collect(Collectors.joining(", "));

        return returnClass + " " + name + "." + method + "(" + args + ")";
    }

    private static String simplifyClassName(String className, int charSeparator) {
        // If the charSeparator is not found, it will return -1. When substring is called, it will
        // return substring(0), which is optimized to return the same String object, so there is no
        // waste of time.
        return className.substring(className.lastIndexOf(charSeparator) + 1);
    }

    private void invoke(String method, String desc, String... args) {
        for (String arg : args) {
            visitLdcInsn(arg);
        }
        visitMethodInsn(INVOKESTATIC, "com/android/tools/tracer/agent/Tracer", method, desc, false);
    }
}
