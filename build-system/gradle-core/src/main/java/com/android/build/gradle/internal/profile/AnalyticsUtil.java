/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.Transform;
import com.google.common.base.CaseFormat;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

/**
 * Utilities to map internal representations of types to analytics.
 */
public class AnalyticsUtil {

    public static AndroidStudioStats.GradleTransformExecution.Type getTransformType(
            @NonNull Class<? extends Transform> taskClass) {
        try {
            return AndroidStudioStats.GradleTransformExecution.Type.valueOf(
                    getPotentialTransformTypeName(taskClass));
        } catch (IllegalArgumentException ignored) {
            return AndroidStudioStats.GradleTransformExecution.Type.UNKNOWN_TRANSFORM_TYPE;
        }
    }

    @VisibleForTesting
    @NonNull
    static String getPotentialTransformTypeName(Class<?> taskClass) {
        String taskImpl = taskClass.getSimpleName();
        if (taskImpl.endsWith("Transform")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "Transform".length());
        }
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, taskImpl);
    }


    @NonNull
    public static AndroidStudioStats.GradleTaskExecution.Type getTaskExecutionType(
            @NonNull Class<?> taskClass) {
        try {
            return AndroidStudioStats.GradleTaskExecution.Type.valueOf(
                    getPotentialTaskExecutionTypeName(taskClass));
        } catch (IllegalArgumentException ignored) {
            return AndroidStudioStats.GradleTaskExecution.Type.UNKNOWN_TASK_TYPE;
        }
    }

    @VisibleForTesting
    @NonNull
    static String getPotentialTaskExecutionTypeName(Class<?> taskClass) {
        String taskImpl = taskClass.getSimpleName();
        if (taskImpl.endsWith("_Decorated")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "_Decorated".length());
        }
        if (taskImpl.endsWith("Task")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "Task".length());
        }
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, taskImpl);
    }
}
