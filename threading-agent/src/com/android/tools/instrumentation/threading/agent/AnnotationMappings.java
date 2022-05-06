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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Class that contains mappings between the threading annotations and the instrumented static
 * methods that they are mapped to.
 */
final class AnnotationMappings {

    @NonNull private final Map<String, Optional<CheckerMethodRef>> annotationToCheckerMethodsMap;

    private AnnotationMappings(
            @NonNull Map<String, Optional<CheckerMethodRef>> annotationToCheckerMethodsMap) {
        this.annotationToCheckerMethodsMap = annotationToCheckerMethodsMap;
    }

    /**
     * Checks if an annotation is one of the threading annotations
     *
     * @param annotation Annotation descriptor as in org.objectweb.asm.MethodVisitor#visitAnnotation
     */
    public boolean isThreadingAnnotation(@NonNull String annotation) {
        return annotationToCheckerMethodsMap.containsKey(annotation);
    }

    /**
     * Returns an optional mapping from a threading annotation to a static method that should be
     * called for this annotation.
     *
     * @param annotation Annotation descriptor as in org.objectweb.asm.MethodVisitor#visitAnnotation
     */
    @NonNull
    public Optional<CheckerMethodRef> getCheckerMethodForThreadingAnnotation(
            @NonNull String annotation) {
        if (!annotationToCheckerMethodsMap.containsKey(annotation)) {
            throw new IllegalArgumentException(
                    "Annotation '" + annotation + "' is not a threading annotation.");
        }
        return annotationToCheckerMethodsMap.get(annotation);
    }

    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    static class Builder {

        private final Map<String, Optional<CheckerMethodRef>> mappings = new HashMap<>();

        private Builder() {}

        /**
         * Add a mapping from a threading annotation to a checker method
         *
         * @param annotation Annotation descriptor as in
         *     org.objectweb.asm.MethodVisitor#visitAnnotation
         * @param className name of the class containing a method to call
         * @param methodName method name to call
         */
        @NonNull
        public Builder addThreadingAnnotationWithCheckerMethod(
                @NonNull String annotation, @NonNull String className, @NonNull String methodName) {
            if (mappings.containsKey(annotation)) {
                throw new IllegalArgumentException(annotation + " has been already added.");
            }
            mappings.put(annotation, Optional.of(new CheckerMethodRef(className, methodName)));
            return this;
        }

        /**
         * Add a threading annotation for which there is no checker method.
         *
         * <p>Note that we need to specify these annotations to correctly handle conflicts between
         * class and method level threading annotations.
         *
         * @param annotation Annotation descriptor as in
         *     org.objectweb.asm.MethodVisitor#visitAnnotation
         */
        @NonNull
        public Builder addNoopThreadingAnnotation(@NonNull String annotation) {
            mappings.put(annotation, Optional.empty());
            return this;
        }

        @NonNull
        public AnnotationMappings build() {
            return new AnnotationMappings(mappings);
        }
    }
}
