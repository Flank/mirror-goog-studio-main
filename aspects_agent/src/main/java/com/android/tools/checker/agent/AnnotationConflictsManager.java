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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;

/**
 * Handles conflicts of method annotations belonging to the same group as some of the containing
 * class's annotations.
 */
public class AnnotationConflictsManager {

    @NonNull private final Map<String, String> annotationsGroups;

    public AnnotationConflictsManager(@NonNull Map<String, String> annotationGroups) {
        this.annotationsGroups = annotationGroups;
    }
    /**
     * Get all the class annotations that do not conflict (i.e. does not belong to the same group)
     * with any method annotations.
     */
    public Set<Type> getNonConflictingClassAnnotations(
            @NonNull Set<Type> methodAnnotations, Set<Type> classAnnotations) {
        return classAnnotations
                .stream()
                .filter(annotation -> !conflictsWithMethodAnnotation(annotation, methodAnnotations))
                .collect(Collectors.toSet());
    }

    private boolean conflictsWithMethodAnnotation(
            @NonNull Type classAnnotation, Set<Type> methodAnnotations) {
        String className = classAnnotation.getClassName();
        for (Type methodAnnotation : methodAnnotations) {
            if (annotationsGroups.containsKey(className)
                    && annotationsGroups
                            .get(className)
                            .equals(annotationsGroups.get(methodAnnotation.getClassName()))) {
                return true;
            }
        }
        return false;
    }
}
