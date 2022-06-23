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
import com.android.annotations.Nullable;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class that contains mappings between the threading annotations and the instrumented static
 * methods that they are mapped to.
 */
final class AnnotationMappings {

    private enum AnnotationMapping {
        UI_THREAD(
                "Lcom/android/annotations/concurrency/UiThread;",
                "com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline",
                "verifyOnUiThread"),
        WORKER_THREAD(
                "Lcom/android/annotations/concurrency/WorkerThread;",
                "com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline",
                "verifyOnWorkerThread"),
        SLOW_THREAD("Lcom/android/annotations/concurrency/Slow;"),
        ANY_THREAD("Lcom/android/annotations/concurrency/AnyThread;");

        @NonNull private final String annotation;

        @Nullable private final CheckerMethodRef checkerMethod;

        /** Create AnnotationMapping with no mapped checker method */
        AnnotationMapping(@NonNull String annotation) {
            this.annotation = annotation;
            this.checkerMethod = null;
        }

        /** Create Annotation with a mapped checker method */
        AnnotationMapping(
                @NonNull String annotation,
                @NonNull String checkMethodClassName,
                @NonNull String checkerMethodName) {
            this.annotation = annotation;
            this.checkerMethod = new CheckerMethodRef(checkMethodClassName, checkerMethodName);
        }

        @NonNull
        String getAnnotation() {
            return annotation;
        }

        @NonNull
        Optional<CheckerMethodRef> getCheckerMethod() {
            return Optional.ofNullable(checkerMethod);
        }
    }

    @NonNull private final Map<String, Optional<CheckerMethodRef>> annotationToCheckerMethodsMap;

    private AnnotationMappings(EnumSet<AnnotationMapping> annotationMappings) {
        this.annotationToCheckerMethodsMap =
                annotationMappings.stream()
                        .collect(
                                Collectors.toMap(
                                        AnnotationMapping::getAnnotation,
                                        AnnotationMapping::getCheckerMethod));
    }

    /** Creates {@link AnnotationMappings} for all the threading annotations. */
    @NonNull
    public static AnnotationMappings create() {
        return new AnnotationMappings(EnumSet.allOf(AnnotationMapping.class));
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
}
