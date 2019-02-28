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

package com.android.tools.checker.agent;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.checker.util.ClassAnnotationFinder;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;


/**
 * A {@link ClassFileTransformer} that overrides methods so the call is redirected to a different
 * method. This redirections are specified via the {@link Aspects} passed in the constructor.
 */
class Transform implements ClassFileTransformer {
    private static final Logger LOGGER = Logger.getLogger(Transform.class.getName());
    private static final Consumer<String> NOT_FOUND_CALLBACK =
            key -> LOGGER.fine(key + " not found");

    private final Aspects aspects;

    Transform(@NonNull Aspects aspects) {
        this.aspects = aspects;
    }

    static byte[] transformClass(
            byte[] input,
            @NonNull String className,
            @NonNull Function<String, String> aspects,
            @NonNull Consumer<String> notMatchedCallback) {
        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor =
                    new InstrumentClassVisitor(writer, className, aspects, notMatchedCallback);
            ClassReader reader = new ClassReader(input);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    @NonNull
    static Collection<String> annotationFromByteBuffer(byte[] classFileBuffer) {
        HashSet<String> classAnnotation = new HashSet<>();
        // This is a new class, look if it has any annotations
        ClassReader classReader = new ClassReader(classFileBuffer);
        ClassAnnotationFinder annotationFinder =
                new ClassAnnotationFinder(
                        null,
                        (type) -> {
                            classAnnotation.add(type.getClassName());
                        });
        classReader.accept(
                annotationFinder,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return classAnnotation;
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            @Nullable String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classFileBuffer) {
        if (className == null) {
            return classFileBuffer;
        }

        if (!aspects.hasClass(className)) {
            boolean hasAnnotations;
            if (classBeingRedefined != null) {
                // The class has no method aspects defined in the configuration, however, we can still
                // define aspects via annotations. Check if the class has any annotations and if the
                // annotations has an aspect defined in the configuration file.
                hasAnnotations =
                        Arrays.stream(classBeingRedefined.getDeclaredMethods())
                                .flatMap(method -> Arrays.stream(method.getDeclaredAnnotations()))
                                .anyMatch(
                                        annotation ->
                                                aspects.hasAnnotation(
                                                        annotation
                                                                .annotationType()
                                                                .getCanonicalName()));
            } else {
                hasAnnotations =
                        annotationFromByteBuffer(classFileBuffer)
                                .stream()
                                .anyMatch(aspects::hasAnnotation);
            }

            if (!hasAnnotations) {
                return classFileBuffer;
            }
        }

        return transformClass(classFileBuffer, className, aspects::getAspect, NOT_FOUND_CALLBACK);
    }
}
