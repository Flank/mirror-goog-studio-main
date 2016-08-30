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

package com.android.build.gradle.internal.variant;

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import com.android.annotations.NonNull;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.InstantAppVariant;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.InstantAppVariantImpl;
import com.android.build.gradle.internal.api.InstantAppVariantOutputImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.google.common.collect.Lists;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;

import java.util.Collections;
import java.util.List;

/**
 * An implementation of VariantFactory for a project that generates IAPKs.
 */

public class InstantAppVariantFactory implements VariantFactory {

    @NonNull
    private Instantiator instantiator;
    @NonNull
    private final AndroidConfig extension;
    @NonNull
    private final AndroidBuilder androidBuilder;

    public InstantAppVariantFactory(
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension) {
        this.instantiator = instantiator;
        this.androidBuilder = androidBuilder;
        this.extension = extension;
    }


    @NonNull
    @Override
    public BaseVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull TaskManager taskManager) {
        InstantAppVariantData variant =
                new InstantAppVariantData(extension, taskManager, variantConfiguration,
                        androidBuilder.getErrorReporter());
        variant.createOutput(OutputFile.OutputType.MAIN,
                Collections.<FilterData>emptyList());
        return variant;
    }

    @NonNull
    @Override
    public InstantAppVariant createVariantApi(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        InstantAppVariantImpl variant = instantiator.newInstance(
                InstantAppVariantImpl.class, variantData, androidBuilder, readOnlyObjectProvider);

        // now create the output objects
        List<? extends BaseVariantOutputData> outputList = variantData.getOutputs();
        List<BaseVariantOutput> apiOutputList = Lists.newArrayListWithCapacity(outputList.size());

        for (BaseVariantOutputData variantOutputData : outputList) {
            InstantAppVariantOutputData instantAppOutput = (InstantAppVariantOutputData) variantOutputData;

            InstantAppVariantOutputImpl output = instantiator.newInstance(
                    InstantAppVariantOutputImpl.class, instantAppOutput);

            apiOutputList.add(output);
        }

        variant.addOutputs(apiOutputList);

        return variant;
    }

    @NonNull
    @Override
    public VariantType getVariantConfigurationType() {
        return VariantType.INSTANTAPP;
    }

    @Override
    public boolean hasTestScope() {
        return false;
    }

    @Override
    public void validateModel(@NonNull VariantModel model) {
        // No additional checks for InstantAppVariantFactory, so just return.
    }

    @Override
    public void preVariantWork(Project project) {
        // nothing to be done here.
    }

    @Override
    public void createDefaultComponents(@NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(DEBUG);
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    }
}
