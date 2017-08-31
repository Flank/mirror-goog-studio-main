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
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * This class intercepts calling the constructor of a {@link FileOutputStream} or a {@link
 * PrintStream} object, and instead calls another method which returns an object of
 * FileOutputStream$ or PrintStream$, which override some methods to report file write calls.
 */
public class OutputStreamAdapter extends ClassVisitor implements Opcodes {

    private static final String FILE_OUTPUT_STREAM_CLASS = "java/io/FileOutputStream";
    private static final String PRINT_STREAM_CLASS = "java/io/PrintStream";

    private static final String WRAP_FILE_OUTPUT_STREAM_CONSTRUCTOR = "wrapFileOutputStreamConstructor";
    private static final String WRAP_PRINT_STREAM_CONSTRUCTOR = "wrapPrintStreamConstructor";

    private static final String[] FILE_OUTPUT_STREAM_DESC_LIST = {"(Ljava/io/File;)V",
        "(Ljava/io/File;Z)V", "(Ljava/io/FileDescriptor;)V", "(Ljava/lang/String;)V",
        "(Ljava/lang/String;Z)V"};
    private static final String[] PRINT_STREAM_DESC_LIST = {"(Ljava/io/File;)V",
        "(Ljava/io/File;Ljava/lang/String;)V", "(Ljava/lang/String;)V",
        "(Ljava/lang/String;Ljava/lang/String;)V"};

    private static final String WRAPPER_CLASS =
        "com/android/tools/profiler/support/io/outputstream/OutputStreamWrapper";

    public OutputStreamAdapter(ClassVisitor classVisitor) {
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
            if (isConstructor(opcode, name) && (
                findAndInvokeMatchingConstructorWrapper(owner, desc, itf, FILE_OUTPUT_STREAM_CLASS,
                    WRAP_FILE_OUTPUT_STREAM_CONSTRUCTOR, FILE_OUTPUT_STREAM_DESC_LIST)
                    || findAndInvokeMatchingConstructorWrapper(owner, desc, itf, PRINT_STREAM_CLASS,
                    WRAP_PRINT_STREAM_CONSTRUCTOR, PRINT_STREAM_DESC_LIST))) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
