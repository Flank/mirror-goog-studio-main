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

package com.android.tools.instrumentation.threading.agent;

import com.android.annotations.NonNull;
import java.util.logging.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class InstrumentClassVisitor extends ClassVisitor {

    private static final Logger LOGGER = Logger.getLogger(InstrumentClassVisitor.class.getName());

    @NonNull private final AnnotationMappings annotationMappings;

    @NonNull private final String className;

    private String threadingAnnotation;

    InstrumentClassVisitor(
            @NonNull AnnotationMappings annotationMappings,
            @NonNull String className,
            @NonNull ClassVisitor classVisitor) {
        super(Opcodes.ASM7, classVisitor);
        this.annotationMappings = annotationMappings;
        this.className = className;
    }

    @Override
    public AnnotationVisitor visitAnnotation(@NonNull String desc, boolean visible) {
        if (annotationMappings.isThreadingAnnotation(desc)) {
            if (threadingAnnotation != null) {
                LOGGER.warning(
                        className
                                + " already has a threading annotation "
                                + threadingAnnotation
                                + ". Only one class-level threading annotation can be specified.");
            }
            threadingAnnotation = desc;
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        return new InstrumentMethodVisitor(
                methodVisitor,
                annotationMappings,
                threadingAnnotation,
                className,
                access,
                name,
                desc);
    }
}
