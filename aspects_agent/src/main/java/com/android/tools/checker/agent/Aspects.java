package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A list of pairs of methods names. Calls to the first method will be redirected to the second one.
 */
class Aspects {
    private final Map<String, String> aspects;

    /** Index that allows to quickly ignore classes that have no aspects defined */
    private final Set<String> classIndex = new HashSet<>();
    /** Index that allows to quickly ignore methods with annotations that have no aspects defined */
    private final Set<String> annotationIndex = new HashSet<>();

    Aspects(Map<String, String> aspects) {
        this.aspects = aspects;

        this.aspects
                .keySet()
                .forEach(
                        key -> {
                            // Put the aspect key in on of the indexes depending on whether is
                            // an annotation or a method name.
                            boolean isAnnotation = key.startsWith("@");
                            if (isAnnotation) {
                                annotationIndex.add(key.substring(1));
                            } else {
                                // The key is a method name. Index the class name to avoid checking
                                // classes that do not have any instrumented methods.
                                classIndex.add(
                                        key.substring(0, key.lastIndexOf('.')).replace('.', '/'));
                            }
                        });
    }

    boolean hasClass(@NonNull String className) {
        return classIndex.contains(className);
    }

    boolean hasAnnotation(@NonNull String annotation) {
        return annotationIndex.contains(annotation);
    }

    @NonNull
    String getAspect(@NonNull String source) {
        return aspects.get(source);
    }
}
