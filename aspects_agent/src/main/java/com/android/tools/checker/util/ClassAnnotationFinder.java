/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.checker.util;

import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Reads the annotations from a given {@link ClassVisitor}. */
public class ClassAnnotationFinder extends ClassVisitor {
    private final Consumer<Type> annotationFound;

    private class MethodAnnotationFinder extends MethodVisitor {
        MethodAnnotationFinder(MethodVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            annotationFound.accept(Type.getType(desc));
            return super.visitAnnotation(desc, visible);
        }
    }

    /**
     * @param delegate the delegate {@link ClassVisitor} or null if none.
     * @param annotationFound {@link Consumer} that will received the annotations.
     */
    public ClassAnnotationFinder(ClassVisitor delegate, Consumer<Type> annotationFound) {
        super(Opcodes.ASM5, delegate);
        this.annotationFound = annotationFound;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        return new MethodAnnotationFinder(
                super.visitMethod(access, name, desc, signature, exceptions));
    }
}
