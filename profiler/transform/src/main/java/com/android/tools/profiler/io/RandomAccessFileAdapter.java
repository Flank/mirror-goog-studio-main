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

package com.android.tools.profiler.io;

import com.android.tools.profiler.io.util.MethodAdapterBase;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * This class intercepts calling the constructor of a {@link java.io.RandomAccessFile}, and instead
 * calls another method which returns an object of RandomAccessFile$, which override some methods to
 * report file write calls.
 */
public class RandomAccessFileAdapter extends ClassVisitor implements Opcodes {

    private static final String RANDOM_ACCESS_FILE_CLASS = "java/io/RandomAccessFile";
    private static final String WRAP_RANDOM_ACCESS_FILE_CONSTRUCTOR = "wrapRandomAccessFileConstructor";

    private static final String[] RANDOM_ACCESS_FILE_DESC_LIST = {
        "(Ljava/io/File;Ljava/lang/String;)V", "(Ljava/lang/String;Ljava/lang/String;)V"};

    private static final String WRAPPER_CLASS =
        "com/android/tools/profiler/support/io/randomaccessfile/RandomAccessFileWrapper";

    public RandomAccessFileAdapter(ClassVisitor classVisitor) {
        super(ASM5, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {

        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return (mv != null) ? new MethodAdapter(mv) : null;
    }

    private static final class MethodAdapter extends MethodAdapterBase implements Opcodes {

        public MethodAdapter(MethodVisitor mv) {
            super(mv);
        }

        @Override
        protected String getWrapperClass() {
            return WRAPPER_CLASS;
        }

        @Override
        public void visitMethodInsn(
            int opcode, String owner, String name, String desc, boolean itf) {
            if (isConstructor(opcode, name) && findAndInvokeMatchingConstructorWrapper(owner, desc,
                itf, RANDOM_ACCESS_FILE_CLASS, WRAP_RANDOM_ACCESS_FILE_CONSTRUCTOR,
                RANDOM_ACCESS_FILE_DESC_LIST)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
