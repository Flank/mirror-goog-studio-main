/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import android.util.Log;
import android.util.Pair;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Support class to invoke Compose API functions. */
public class ComposeSupport {
    public static final String KEY_META_CLASS_NAME =
            "androidx.compose.runtime.internal.FunctionKeyMetaClass";
    public static final String KEY_META_NAME = "androidx.compose.runtime.internal.FunctionKeyMeta";

    // Return empty string if success. Otherwise, an error message is returned.
    public static String recomposeFunction(
            Object reloader, String className, int offsetStart, int offSetEnd) {
        ClassLoader classLoader = reloader.getClass().getClassLoader();

        Class<?> keyMeta = null;
        String metaClassName = className.replaceAll("/", ".") + "-KeyMeta";
        try {
            keyMeta = Class.forName(metaClassName, true, classLoader);
        } catch (ClassNotFoundException e) {
            return metaClassName + " class not found.";
        }

        Annotation[] annotations = keyMeta.getAnnotations();
        boolean isMetaClass = false;
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getName().equals(KEY_META_CLASS_NAME)) {
                isMetaClass = true;
                break;
            }
        }

        if (!isMetaClass) {
            return metaClassName + " is not annotated with " + KEY_META_CLASS_NAME;
        }

        boolean found = false;
        int key = Integer.MIN_VALUE;
        for (Annotation annotation : annotations) {
            if (!annotation.annotationType().getName().equals(KEY_META_NAME)) {
                continue;
            }
            try {
                Method startOffsetMethod = annotation.annotationType().getMethod("startOffset");
                Method endOffsetMethod = annotation.annotationType().getMethod("endOffset");
                Method groupMethod = annotation.annotationType().getMethod("key");
                int curStartOffset = (Integer) startOffsetMethod.invoke(annotation);
                int curEndOffset = (Integer) endOffsetMethod.invoke(annotation);

                if (curStartOffset == offsetStart && curEndOffset == offSetEnd) {
                    found = true;
                    key = (Integer) groupMethod.invoke(annotation);
                    break;
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                // Very unlikely scenario that only happens if Compose changed their API.
                e.printStackTrace();
                return e.getMessage();
            }
        }

        if (!found) {
            // Very unlikely scenario that only happens if Compose Group ID key look up is out of
            // sync with Android Studio.
            String errorMessage =
                    String.format(
                            "Compose Group for found for class %s with"
                                    + " offsetStart = %d and offSetEnd = %d.",
                            metaClassName, offsetStart, offSetEnd);

            // Print everything in logcat since this is a rare occurrence and all the offset
            // information is going to be useful in the bug report.
            Log.e("studio.deploy", errorMessage);

            List<Pair<Integer, Integer>> ranges = new ArrayList<>(annotations.length);

            for (Annotation annotation : annotations) {
                if (!annotation.annotationType().getName().equals(KEY_META_NAME)) {
                    continue;
                }
                try {
                    Method startOffsetMethod = annotation.annotationType().getMethod("startOffset");
                    Method endOffsetMethod = annotation.annotationType().getMethod("endOffset");
                    int curStartOffset = (Integer) startOffsetMethod.invoke(annotation);
                    int curEndOffset = (Integer) endOffsetMethod.invoke(annotation);
                    ranges.add(new Pair(curStartOffset, curEndOffset));
                } catch (NoSuchMethodException
                        | InvocationTargetException
                        | IllegalAccessException e) {
                    // Would have been caught in the previous step.
                }
            }
            Collections.sort(ranges, Comparator.comparing(a -> a.first));
            ranges.stream()
                    .forEach(
                            pair -> {
                                Log.e("studio.deploy", "(" + pair.first + "," + pair.second + ") ");
                            });
        }

        Method invalidateGroupsWithKey = null;

        try {
            invalidateGroupsWithKey =
                    reloader.getClass().getMethod("invalidateGroupsWithKey", int.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return e.getMessage(); // Very unlikely.
        }

        try {
            invalidateGroupsWithKey.invoke(reloader, key);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return e.getMessage(); // Very unlikely.
        }
        return "";
    }
}
