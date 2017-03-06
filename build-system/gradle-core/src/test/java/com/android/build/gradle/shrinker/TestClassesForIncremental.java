/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.shrinker;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Test classes used by {@link IncrementalShrinkerTest}. */
public class TestClassesForIncremental implements Opcodes {

    public static class Simple {
        public static byte[] main1() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>", "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }

            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main2() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>", "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m2", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_extraMethod() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>", "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }

            {
                mv = cw.visitMethod(ACC_PUBLIC, "extraMain", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>", "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }

            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_extraField() {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>", "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                fv =
                        cw.visitField(
                                ACC_PUBLIC + ACC_STATIC,
                                "sString",
                                "Ljava/lang/String;",
                                null,
                                null);
                fv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main_extraField_private() {

            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/Bbb");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Bbb", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitTypeInsn(NEW, "test/Aaa");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/Aaa", "<init>", "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "test/Aaa", "m1", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            {
                fv =
                        cw.visitField(
                                ACC_PRIVATE + ACC_STATIC,
                                "sString",
                                "Ljava/lang/String;",
                                null,
                                null);
                fv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] aaa() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Aaa", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "m1", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(0, "m2", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] bbb() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] bbb_packagePrivateConstructor() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(0, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] bbb_packagePrivate() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_SUPER, "test/Bbb", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] bbb_serializable() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(
                    V1_6,
                    ACC_PUBLIC + ACC_SUPER,
                    "test/Bbb",
                    null,
                    "java/lang/Object",
                    new String[] {"java/io/Serializable"});

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] bbb_extendsAaa() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Bbb", null, "test/Aaa", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static class Cycle {
        public static byte[] main1() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/CycleOne");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/CycleOne", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] main2() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC, "main", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] cycleOne() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/CycleOne", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitTypeInsn(NEW, "test/CycleTwo");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/CycleTwo", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }

        public static byte[] cycleTwo() {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/CycleTwo", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitTypeInsn(NEW, "test/CycleOne");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/CycleOne", "<init>", "()V", false);
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

    public static class Interfaces {
        static byte[] myInterface() throws Exception {
            return TestClasses.Interfaces.myInterface();
        }

        static byte[] myImpl() throws Exception {
            return TestClasses.Interfaces.myImpl();
        }

        static byte[] main(boolean useInvokeinterface) throws Exception {
            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;

            cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/Main", null, "java/lang/Object", null);

            {
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv =
                        cw.visitMethod(
                                ACC_PUBLIC + ACC_STATIC,
                                "buildMyImpl",
                                "()Ltest/MyImpl;",
                                null,
                                null);
                mv.visitCode();
                mv.visitTypeInsn(NEW, "test/MyImpl");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "test/MyImpl", "<init>", "()V", false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 0);
                mv.visitEnd();
            }
            {
                mv =
                        cw.visitMethod(
                                ACC_PUBLIC + ACC_STATIC, "main", "(Ltest/MyImpl;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(ACONST_NULL);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "test/MyImpl",
                        "doSomething",
                        "(Ljava/lang/Object;)V",
                        false);
                if (useInvokeinterface) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitInsn(ACONST_NULL);
                    mv.visitMethodInsn(
                            INVOKEINTERFACE,
                            "test/MyInterface",
                            "doSomething",
                            "(Ljava/lang/Object;)V",
                            true);
                }
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }

            return cw.toByteArray();
        }
    }

    @SuppressWarnings("SameParameterValue")
    static byte[] classWhichReturnsInt(String className, int value) throws Exception {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/" + className, null, "java/lang/Object", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "getInt", "()I", null, null);
            mv.visitCode();
            mv.visitLdcInsn(value);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }

    static byte[] classWithCasts(String className, String... targets) throws Exception {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "test/" + className, null, "java/lang/Object", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "cast", "(Ljava/lang/Object;)V", null, null);
            mv.visitCode();
            for (String klass : targets) {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitTypeInsn(INSTANCEOF, klass);
                mv.visitInsn(POP);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }
}
