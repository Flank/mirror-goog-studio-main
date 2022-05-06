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
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

class Transformer implements ClassFileTransformer {

    private static final Logger LOGGER = Logger.getLogger(Transformer.class.getName());

    // Skip the default JRE classes.
    // Also skip com.intellij.workspaceModel.* classes for some of which
    // ClassReader#readVerificationTypeInfo throws IllegalArgumentException.
    private static final String[] skipPackagePrefixes = {"java.", "com.intellij.workspaceModel."};

    @NonNull private final AnnotationMappings annotationMappings;

    public Transformer(@NonNull AnnotationMappings annotationMappings) {
        this.annotationMappings = annotationMappings;
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String classJvmName,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classFileBuffer) {
        if (classJvmName == null) {
            LOGGER.fine("classJvmName is null");
            return null;
        }

        String className = classJvmName.replace('/', '.');
        for (String skipPackagePrefix : skipPackagePrefixes) {
            if (className.startsWith(skipPackagePrefix)) {
                LOGGER.fine("Threading agent: skip instrumenting class " + className);
                return null;
            }
        }

        try {
            ClassReader reader = new ClassReader(classFileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            InstrumentClassVisitor classVisitor =
                    new InstrumentClassVisitor(annotationMappings, className, writer);
            reader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable e) {
            LOGGER.warning(
                    (String.format("Threading agent: failed to instrument %s\n%s", className, e)));
            return null;
        }
    }
}
