/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.google.common.collect.Maps;
import java.util.Map;

/**
 * Data about a variant that produce an application APK
 */
public class ApplicationVariantData extends ApkVariantData implements TestedVariantData {
    private final Map<VariantType, TestVariantData> testVariants;

    public ApplicationVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull TaskManager taskManager,
            @NonNull GradleVariantConfiguration config,
            @NonNull Recorder recorder) {
        super(globalScope, taskManager, config, recorder);
        testVariants = Maps.newHashMap();
    }

    @Override
    public void setTestVariantData(
            @NonNull TestVariantData testVariantData,
            @NonNull VariantType type) {
        testVariants.put(type, testVariantData);
    }

    @Nullable
    @Override
    public TestVariantData getTestVariantData(@NonNull VariantType type) {
        return testVariants.get(type);
    }
}
