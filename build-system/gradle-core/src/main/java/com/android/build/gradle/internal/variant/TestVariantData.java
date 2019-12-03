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
import com.android.build.api.variant.VariantConfiguration;
import com.android.build.api.variant.impl.TestVariantPropertiesImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.IVariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.android.utils.StringHelper;

/**
 * Data about a variant that produce a test APK
 */
public class TestVariantData extends ApkVariantData {

    @NonNull
    private final TestedVariantData testedVariantData;

    public TestVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull TaskManager taskManager,
            @NonNull VariantDslInfoImpl variantDslInfo,
            @NonNull VariantSources variantSources,
            @NonNull TestedVariantData testedVariantData,
            @NonNull Recorder recorder) {
        super(globalScope, taskManager, variantDslInfo, variantSources, recorder);
        this.testedVariantData = testedVariantData;

        // create default output
        getOutputFactory().addMainApk();
    }

    @NonNull
    public TestedVariantData getTestedVariantData() {
        return testedVariantData;
    }

    @Override
    @NonNull
    public String getDescription() {
        String prefix;
        VariantType variantType = getType();
        if (variantType.isApk()) {
            prefix = "android (on device) tests";
        } else {
            prefix = "unit tests";
        }

        final IVariantDslInfo variantDslInfo = getVariantDslInfo();

        if (variantDslInfo.hasFlavors()) {
            StringBuilder sb = new StringBuilder(50);
            sb.append(prefix);
            sb.append(" for the ");
            StringHelper.appendCapitalized(sb, variantDslInfo.getFlavorName());
            StringHelper.appendCapitalized(sb, variantDslInfo.getBuildType().getName());
            sb.append(" build");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(50);
            sb.append(prefix);
            sb.append(" for the ");
            StringHelper.appendCapitalized(sb, variantDslInfo.getBuildType().getName());
            sb.append(" build");
            return sb.toString();
        }
    }

    @Override
    VariantImpl<?> instantiatePublicVariantObject(VariantConfiguration publicVariantConfiguration) {
        return new com.android.build.api.variant.impl.TestVariantImpl(publicVariantConfiguration);
    }

    @Override
    VariantPropertiesImpl instantiatePublicVariantPropertiesObject(
            VariantConfiguration publicVariantConfiguration) {
        return scope.getGlobalScope()
                .getProject()
                .getObjects()
                .newInstance(
                        TestVariantPropertiesImpl.class,
                        scope.getGlobalScope().getDslScope(),
                        scope,
                        scope.getArtifacts().getOperations(),
                        publicVariantConfiguration);
    }
}
