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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

public class InstrumentMethodVisitor extends AdviceAdapter {

    private static final Logger LOGGER = Logger.getLogger(InstrumentMethodVisitor.class.getName());

    @NonNull private final AnnotationMappings annotationMappings;

    @NonNull private final String className;

    @NonNull private final String methodName;

    private final String classThreadingAnnotation;

    // Methods may be decorated with more than one threading annotation at the same time.
    // For example, @Slow and @WorkerThread are sometimes present on a same method.
    private List<String> threadingAnnotations;

    public InstrumentMethodVisitor(
            @NonNull MethodVisitor methodVisitor,
            @NonNull AnnotationMappings annotationMappings,
            String classThreadingAnnotation,
            @NonNull String className,
            int access,
            @NonNull String name,
            @NonNull String desc) {
        super(Opcodes.ASM7, methodVisitor, access, name, desc);
        this.annotationMappings = annotationMappings;
        this.classThreadingAnnotation = classThreadingAnnotation;
        this.className = className;
        this.methodName = name;
    }

    @Override
    public AnnotationVisitor visitAnnotation(@NonNull String desc, boolean visible) {
        if (annotationMappings.isThreadingAnnotation(desc)) {
            if (threadingAnnotations == null) {
                threadingAnnotations = new ArrayList<>(1);
            }
            threadingAnnotations.add(desc);
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override
    protected void onMethodEnter() {
        super.onMethodEnter();
        if (threadingAnnotations == null && classThreadingAnnotation == null) {
            return;
        }
        if ((methodAccess & ACC_SYNTHETIC) != 0) {
            // Do not process synthetic methods such as synthetic accessors, lambdas, and bridge
            // methods.
            if (threadingAnnotations != null && (methodAccess & ACC_BRIDGE) == 0) {
                LOGGER.warning(
                        "Threading annotation found on a generated method which is not a bridge method. "
                                + className
                                + "#"
                                + methodName);
            }
            return;
        }
        // When annotations are present on a method then the class level annotations should be
        // ignored.
        // TODO: Figure out how to handle inheritance and interface implementations.
        List<String> effectiveThreadingAnnotations =
                threadingAnnotations != null
                        ? threadingAnnotations
                        : Collections.singletonList(classThreadingAnnotation);
        for (String effectiveThreadingAnnotation : effectiveThreadingAnnotations) {
            LOGGER.fine(
                    String.format(
                            "Processed a threading annotation '%s' on %s#%s",
                            effectiveThreadingAnnotation, className, methodName));
            Optional<CheckerMethodRef> checkerMethodRef =
                    annotationMappings.getCheckerMethodForThreadingAnnotation(
                            effectiveThreadingAnnotation);
            checkerMethodRef.ifPresent(
                    cmr -> generateStaticCall(cmr.getClassName(), cmr.getMethodName()));
        }
    }

    private void generateStaticCall(@NonNull String className, @NonNull String methodName) {
        try {
            Type staticClassType = Type.getType(Class.forName(className));
            Method staticClassMethod = Method.getMethod(String.format("void %s()", methodName));

            invokeStatic(staticClassType, staticClassMethod);
        } catch (ClassNotFoundException e) {
            LOGGER.warning(
                    "Threading agent: unable to instrument an annotated method. Class "
                            + className
                            + " not found");
        }
    }
}
