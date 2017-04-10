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

package com.android.testutils;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.annotations.NonNull;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

/** A class generator used to create classes that can be used in tests. */
public final class TestClassesGenerator {

    /** Generates an empty class. */
    public static byte[] emptyClass(String name) throws Exception {
        return emptyClass("test", name);
    }

    /** Generates an empty class in the specified package. */
    public static byte[] emptyClass(@NonNull String pkg, @NonNull String name) throws Exception {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, pkg + "/" + name, null, "java/lang/Object", null);

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

    /** Generates a class containing specified methods that contain empty bodies. */
    public static byte[] classWithEmptyMethods(String className, String... namesAndDescriptors)
            throws Exception {

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
        for (String namesAndDescriptor : namesAndDescriptors) {
            int colon = namesAndDescriptor.indexOf(':');
            String methodName = namesAndDescriptor.substring(0, colon);
            String descriptor =
                    namesAndDescriptor.substring(colon + 1, namesAndDescriptor.length());
            {
                mv = cw.visitMethod(ACC_PUBLIC, methodName, descriptor, null, null);
                mv.visitCode();
                // This bytecode is only valid for some signatures (void methods). This class is used
                // for testing the parser, we don't ever load these classes to a running VM anyway.
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
        }
        cw.visitEnd();

        return cw.toByteArray();
    }

    /** Generates a class containing specified fields and methods. */
    public static byte[] classWithFieldsAndMethods(
            @NonNull String className, @NonNull List<String> fields, @NonNull List<String> methods)
            throws Exception {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;
        FieldVisitor fv;

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        for (String fieldName : fields) {
            fv = cw.visitField(ACC_PRIVATE, fieldName, "Ljava/lang/String;", null, null);
            fv.visitEnd();
        }
        for (String namesAndDescriptor : methods) {
            int colon = namesAndDescriptor.indexOf(':');
            String methodName = namesAndDescriptor.substring(0, colon);
            String descriptor =
                    namesAndDescriptor.substring(colon + 1, namesAndDescriptor.length());
            {
                mv = cw.visitMethod(ACC_PUBLIC, methodName, descriptor, null, null);
                mv.visitCode();
                // This bytecode is only valid for some signatures (void methods). This class is used
                // for testing the parser, we don't ever load these classes to a running VM anyway.
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
            }
        }
        cw.visitEnd();

        return cw.toByteArray();
    }

    /** Generates a class containing specified fields and methods. */
    public static byte[] classWithStrings(@NonNull String className, int cntStringsToGenerate)
            throws Exception {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null);

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
            mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            for (int i = 0; i < cntStringsToGenerate; i++) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("generated_string_" + className + "_" + i);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "java/io/PrintStream",
                        "println",
                        "(Ljava/lang/String;)V",
                        false);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 0);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }

    /** Rewrites the version of the class file. */
    @NonNull
    public static byte[] rewriteToVersion(int newVersion, @NonNull InputStream current)
            throws IOException {
        byte[] bytes = ByteStreams.toByteArray(current);
        // magic-minor-major:  0x CA FE BA BE 00 00 <new_version>
        ByteBuffer.wrap(bytes).putShort(6, (short) newVersion);
        return bytes;
    }
}
