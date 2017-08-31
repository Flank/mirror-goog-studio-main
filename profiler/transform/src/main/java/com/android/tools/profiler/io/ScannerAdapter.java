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
import java.util.Scanner;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * This class intercepts read methods calls from the {@link Scanner} class and redirects it to call
 * the wrapper method in ScannerWrapper class.
 */
public class ScannerAdapter extends ClassVisitor implements Opcodes {

    private static final String SCANNER_CLASS = "java/util/Scanner";

    private static final String WRAP_SCANNER_CONSTRUCTOR = "wrapConstructor";

    private static final String[] SCANNER_DESC_LIST = {"(Ljava/io/File;)V",
        "(Ljava/io/File;Ljava/lang/String;)V", "(Ljava/nio/file/Path;)V",
        "(Ljava/nio/file/Path;Ljava/lang/String;)V"};

    private static final String WRAPPER_CLASS =
        "com/android/tools/profiler/support/io/scanner/ScannerWrapper";

    public ScannerAdapter(ClassVisitor classVisitor) {
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
            if (isConstructor(opcode, name)) {
                if (findAndInvokeMatchingConstructorWrapper(owner, desc, itf, SCANNER_CLASS,
                    WRAP_SCANNER_CONSTRUCTOR, SCANNER_DESC_LIST)) {
                    return;
                }
            }
            if (opcode != INVOKEVIRTUAL || !owner.equals(SCANNER_CLASS)) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
            if (name.equals("next")) {
                if (desc.equals("()Ljava/lang/String;")) {
                    invoke("wrapNext", "(Ljava/util/Scanner;)Ljava/lang/String;", itf);
                } else if (desc.equals("(Ljava/util/regex/Pattern;)Ljava/lang/String;")) {
                    invoke(
                        "wrapNext",
                        "(Ljava/util/Scanner;Ljava/util/regex/Pattern;)Ljava/lang/String;",
                        itf);
                } else if (desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                    invoke(
                        "wrapNext",
                        "(Ljava/util/Scanner;Ljava/lang/String;)Ljava/lang/String;",
                        itf);
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            } else if (name.equals("nextBigDecimal")) {
                invoke("wrapNextBigDecimal", "(Ljava/util/Scanner;)Ljava/math/BigDecimal;", itf);
            } else if (name.equals("nextBigInteger")) {
                if (desc.equals("()Ljava/math/BigInteger;")) {
                    invoke(
                        "wrapNextBigInteger",
                        "(Ljava/util/Scanner;)Ljava/math/BigInteger;",
                        itf);
                } else if (desc.equals("(I)Ljava/math/BigInteger;")) {
                    invoke(
                        "wrapNextBigInteger",
                        "(Ljava/util/Scanner;I)Ljava/math/BigInteger;",
                        itf);
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            } else if (name.equals("nextBoolean")) {
                invoke("wrapNextBoolean", "(Ljava/util/Scanner;)Z", itf);
            } else if (name.equals("nextByte")) {
                if (desc.equals("()B")) {
                    invoke("wrapNextByte", "(Ljava/util/Scanner;)B", itf);
                } else if (desc.equals("(I)B")) {
                    invoke("wrapNextByte", "(Ljava/util/Scanner;I)B", itf);
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            } else if (name.equals("nextDouble")) {
                invoke("wrapNextDouble", "(Ljava/util/Scanner;)D", itf);
            } else if (name.equals("nextFloat")) {
                invoke("wrapNextFloat", "(Ljava/util/Scanner;)F", itf);
            } else if (name.equals("nextInt")) {
                if (desc.equals("()I")) {
                    invoke("wrapNextInt", "(Ljava/util/Scanner;)I", itf);
                } else if (desc.equals("(I)I")) {
                    invoke("wrapNextInt", "(Ljava/util/Scanner;I)I", itf);
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            } else if (name.equals("nextLine")) {
                invoke("wrapNextLine", "(Ljava/util/Scanner;)Ljava/lang/String;", itf);
            } else if (name.equals("nextLong")) {
                if (desc.equals("()J")) {
                    invoke("wrapNextLong", "(Ljava/util/Scanner;)J", itf);
                } else if (desc.equals("(I)J")) {
                    invoke("wrapNextLong", "(Ljava/util/Scanner;I)J", itf);
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            } else if (name.equals("nextShort")) {
                if (desc.equals("()S")) {
                    invoke("wrapNextShort", "(Ljava/util/Scanner;)S", itf);
                } else if (desc.equals("(I)S")) {
                    invoke("wrapNextShort", "(Ljava/util/Scanner;I)S", itf);
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            } else if (name.equals("close")) {
                invoke("wrapClose", "(Ljava/util/Scanner;)V", itf);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }
}
