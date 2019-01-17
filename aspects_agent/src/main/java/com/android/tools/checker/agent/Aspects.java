package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A list of pairs of methods names. Calls to the first method will be redirected to the second one.
 */
class Aspects {
    private final Map<String, String> aspects;

    /** Index that allows to quickly ignore classes that have no aspects defined */
    private final Set<String> classIndex;

    Aspects(Map<String, String> aspects) {
        this.aspects = new HashMap<>(aspects);

        classIndex =
                this.aspects
                        .keySet()
                        .stream()
                        .map(
                                methodName ->
                                        methodName
                                                .substring(0, methodName.lastIndexOf('.'))
                                                .replace('.', '/'))
                        .collect(Collectors.toSet());
    }

    boolean hasClass(@NonNull String className) {
        return classIndex.contains(className);
    }

    @NonNull
    String getAspect(@NonNull String source) {
        return aspects.get(source);
    }
}
