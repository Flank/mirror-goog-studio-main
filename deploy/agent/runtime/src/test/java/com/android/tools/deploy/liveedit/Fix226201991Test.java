/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import com.android.deploy.asm.ClassWriter;
import com.android.deploy.asm.MethodVisitor;
import com.android.deploy.asm.Opcodes;
import org.junit.Assert;
import org.junit.Test;

public class Fix226201991Test {

    private byte[] makeBadGetter(
            String className,
            String methodName,
            String returnType,
            String constantPoolClass,
            String constantpoolField) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                        methodName,
                        "()" + returnType,
                        null,
                        null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, constantPoolClass, constantpoolField, returnType);
        if (returnType.equals("I")
                || returnType.equals("Z")
                || returnType.equals("C")
                || returnType.equals("B")) {
            mv.visitInsn(Opcodes.IRETURN);
        } else if (returnType.equals("J")) {
            mv.visitInsn(Opcodes.LRETURN);
        } else if (returnType.equals("F")) {
            mv.visitInsn(Opcodes.FRETURN);
        } else if (returnType.equals("D")) {
            mv.visitInsn(Opcodes.DRETURN);
        } else {
            mv.visitInsn(Opcodes.ARETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();

        // Write the bytes as a class file
        return cw.toByteArray();
    }

    private static final String METHOD_NAME = "justAMethodContainerforASM";
    private static final String CLASS_NAME = "JustAClassContainerForASM";

    @Test
    public void testDoubleRename() {
        String returnType = "D";
        byte[] clazz = makeBadGetter(CLASS_NAME, METHOD_NAME, returnType, "D", "MAX_VALUE");
        MethodBodyEvaluator ev = new MethodBodyEvaluator(clazz, METHOD_NAME, "()" + returnType);
        Double d = (Double) ev.evalStatic(new Object[] {});
        Assert.assertTrue("Bad D.MAX_VALUE retrieval", d == Double.MAX_VALUE);
    }

    @Test
    public void testFloatRename() {
        String returnType = "F";
        byte[] clazz = makeBadGetter(CLASS_NAME, METHOD_NAME, returnType, "F", "MAX_VALUE");
        MethodBodyEvaluator ev = new MethodBodyEvaluator(clazz, METHOD_NAME, "()" + returnType);
        Float f = (Float) ev.evalStatic(new Object[] {});
        Assert.assertTrue("Bad F.MAX_VALUE retrieval", f == Float.MAX_VALUE);
    }

    @Test
    public void testIntRename() {
        String returnType = "I";
        byte[] clazz = makeBadGetter(CLASS_NAME, METHOD_NAME, returnType, "I", "MAX_VALUE");
        MethodBodyEvaluator ev = new MethodBodyEvaluator(clazz, METHOD_NAME, "()" + returnType);
        Integer i = (Integer) ev.evalStatic(new Object[] {});
        Assert.assertTrue("Bad I.MAX_VALUE retrieval", i == Integer.MAX_VALUE);
    }

    @Test
    public void testLongRename() {
        String returnType = "J";
        byte[] clazz = makeBadGetter(CLASS_NAME, METHOD_NAME, returnType, "J", "MAX_VALUE");
        MethodBodyEvaluator ev = new MethodBodyEvaluator(clazz, METHOD_NAME, "()" + returnType);
        Long l = (Long) ev.evalStatic(new Object[] {});
        Assert.assertTrue("Bad J.MAX_VALUE retrieval", l == Long.MAX_VALUE);
    }

    @Test
    public void testShortRename() {
        String returnType = "S";
        byte[] clazz = makeBadGetter(CLASS_NAME, METHOD_NAME, returnType, "S", "MAX_VALUE");
        MethodBodyEvaluator ev = new MethodBodyEvaluator(clazz, METHOD_NAME, "()" + returnType);
        Short s = (Short) ev.evalStatic(new Object[] {});
        Assert.assertTrue("Bad S.MAX_VALUE retrieval", s == Short.MAX_VALUE);
    }

    @Test
    public void testByteRename() {
        String returnType = "B";
        byte[] clazz = makeBadGetter(CLASS_NAME, METHOD_NAME, returnType, "B", "MAX_VALUE");
        MethodBodyEvaluator ev = new MethodBodyEvaluator(clazz, METHOD_NAME, "()" + returnType);
        Byte b = (Byte) ev.evalStatic(new Object[] {});
        Assert.assertTrue("Bad B.MAX_VALUE retrieval", b == Byte.MAX_VALUE);
    }

    @Test
    public void testCharRename() {
        String returnType = "C";
        byte[] clazz = makeBadGetter(CLASS_NAME, METHOD_NAME, returnType, "C", "MAX_VALUE");
        MethodBodyEvaluator ev = new MethodBodyEvaluator(clazz, METHOD_NAME, "()" + returnType);
        Character c = (Character) ev.evalStatic(new Object[] {});
        Assert.assertTrue("Bad C.MAX_VALUE retrieval", c == Character.MAX_VALUE);
    }
}
