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
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.google.common.base.CaseFormat;
import com.google.wireless.android.sdk.stats.ApiVersion;

/**
 * Utilities to map internal representations of types to analytics.
 */
public class AnalyticsUtil {

    public static GradleTransformExecutionType getTransformType(
            @NonNull Class<? extends Transform> taskClass) {
        try {
            return GradleTransformExecutionType.valueOf(getPotentialTransformTypeName(taskClass));
        } catch (IllegalArgumentException ignored) {
            return GradleTransformExecutionType.UNKNOWN_TRANSFORM_TYPE;
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
    public static GradleTaskExecutionType getTaskExecutionType(@NonNull Class<?> taskClass) {
        try {
            return GradleTaskExecutionType.valueOf(getPotentialTaskExecutionTypeName(taskClass));
        } catch (IllegalArgumentException ignored) {
            return GradleTaskExecutionType.UNKNOWN_TASK_TYPE;
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

    @NonNull
    public static ApiVersion convert(@NonNull AndroidVersion apiVersion) {
        ApiVersion.Builder builder = ApiVersion.newBuilder().setApiLevel(apiVersion.getApiLevel());
        if (apiVersion.getCodename() != null) {
            builder.setCodename(apiVersion.getCodename());
        }
        return builder.build();
    }

    @NonNull
    public static ApiVersion convert(@NonNull com.android.builder.model.ApiVersion apiVersion) {
        ApiVersion.Builder builder = ApiVersion.newBuilder().setApiLevel(apiVersion.getApiLevel());
        if (apiVersion.getCodename() != null) {
            builder.setCodename(apiVersion.getCodename());
        }
        return builder.build();
    }

}
