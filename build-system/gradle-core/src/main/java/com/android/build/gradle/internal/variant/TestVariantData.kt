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
import com.android.build.api.component.ComponentIdentity;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.builder.core.VariantType;
import com.android.utils.StringHelper;

/**
 * Data about a test component in a normal plugin
 *
 * <p>For the test plugin, ApplicationVariantData is used.
 */
public class TestVariantData extends ApkVariantData {

    @NonNull
    private final TestedVariantData testedVariantData;

    public TestVariantData(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull BuildArtifactsHolder artifacts,
            @NonNull TestedVariantData testedVariantData,
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
        this.testedVariantData = testedVariantData;
    }

    @NonNull
    public TestedVariantData getTestedVariantData() {
        return testedVariantData;
    }

    @Override
    @NonNull
    public String getDescription() {
        String prefix;
        VariantType variantType = variantDslInfo.getVariantType();
        if (variantType.isApk()) {
            prefix = "android (on device) tests";
        } else {
            prefix = "unit tests";
        }

        if (variantDslInfo.hasFlavors()) {
            StringBuilder sb = new StringBuilder(50);
            sb.append(prefix);
            sb.append(" for the ");
            StringHelper.appendCapitalized(
                    sb, variantDslInfo.getComponentIdentity().getFlavorName());
            StringHelper.appendCapitalized(
                    sb, variantDslInfo.getComponentIdentity().getBuildType());
            sb.append(" build");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(50);
            sb.append(prefix);
            sb.append(" for the ");
            StringHelper.appendCapitalized(
                    sb, variantDslInfo.getComponentIdentity().getBuildType());
            sb.append(" build");
            return sb.toString();
        }
    }
}
