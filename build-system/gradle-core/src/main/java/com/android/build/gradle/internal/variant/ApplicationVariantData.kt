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
import com.android.build.api.component.ComponentIdentity;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.builder.core.VariantType;
import com.android.utils.StringHelper;
import com.google.common.collect.Maps;
import java.util.Map;

/**
 * Data about a variant that produce an application APK.
 *
 * <p>This includes application, dynamic-feature and standalone Test plugins.
 */
public class ApplicationVariantData extends ApkVariantData implements TestedVariantData {
    private final Map<VariantType, TestVariantData> testVariants;

    public ApplicationVariantData(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull BuildArtifactsHolder artifacts,
            @NonNull GlobalScope globalScope,
            @NonNull MutableTaskContainer taskContainer) {
        super(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                globalScope,
                taskContainer);
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

    @Override
    @NonNull
    public String getDescription() {
        if (variantDslInfo.hasFlavors()) {
            StringBuilder sb = new StringBuilder(50);
            StringHelper.appendCapitalized(
                    sb, variantDslInfo.getComponentIdentity().getBuildType());
            StringHelper.appendCapitalized(sb, variantDslInfo.getComponentIdentity().getName());
            sb.append(" build");
            return sb.toString();
        } else {
            return StringHelper.capitalizeAndAppend(
                    variantDslInfo.getComponentIdentity().getBuildType(), " build");
        }
    }
}
