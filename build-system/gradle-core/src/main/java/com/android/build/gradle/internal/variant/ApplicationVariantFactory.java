/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.OutputFile;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.ApkVariantImpl;
import com.android.build.gradle.internal.api.ApkVariantOutputImpl;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.SplitFactory;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;

/**
 * An implementation of VariantFactory for a project that generates APKs.
 *
 * This can be an app project, or a test-only project, though the default
 * behavior is app.
 */
public class ApplicationVariantFactory implements VariantFactory {

    Instantiator instantiator;
    @NonNull
    protected final AndroidConfig extension;
    @NonNull
    private final AndroidBuilder androidBuilder;

    public ApplicationVariantFactory(
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension) {
        this.instantiator = instantiator;
        this.androidBuilder = androidBuilder;
        this.extension = extension;
    }

    @Override
    @NonNull
    public BaseVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull TaskManager taskManager,
            @NonNull Recorder recorder) {
        ApplicationVariantData variant =
                new ApplicationVariantData(
                        extension,
                        variantConfiguration,
                        taskManager,
                        androidBuilder.getErrorReporter(),
                        recorder);

        variant.calculateFilters(extension.getSplits());

        Set<String> densities = variant.getFilters(OutputFile.FilterType.DENSITY);
        Set<String> abis = variant.getFilters(OutputFile.FilterType.ABI);

        if (!densities.isEmpty()) {
            variant.setCompatibleScreens(extension.getSplits().getDensity()
                    .getCompatibleScreens());
        }

        SplitScope splitScope = variant.getSplitScope();
        SplitFactory splitFactory = variant.getSplitFactory();

        // create its output
        if (splitScope.getSplitHandlingPolicy() == SplitHandlingPolicy.PRE_21_POLICY) {

            // if the abi list is not empty and we must generate a universal apk, add it.
            if (abis.isEmpty()) {
                // create the main APK or universal APK depending on whether or not we are going
                // to produce full splits.
                if (densities.isEmpty()) {
                    splitFactory.addMainApk();
                } else {
                    splitFactory.addUniversalApk();
                }
            } else {
                if (extension.getSplits().getAbi().isEnable()
                        && extension.getSplits().getAbi().isUniversalApk()) {
                    splitFactory.addUniversalApk();
                }
                // for each ABI, create a specific split that will contain all densities.
                abis.forEach(
                        abi ->
                                splitFactory.addFullSplit(
                                        ImmutableList.of(Pair.of(OutputFile.FilterType.ABI, abi))));
            }

            // create its outputs
            for (String density : densities) {
                if (!abis.isEmpty()) {
                    for (String abi : abis) {
                        splitFactory.addFullSplit(
                                ImmutableList.of(
                                        Pair.of(OutputFile.FilterType.ABI, abi),
                                        Pair.of(OutputFile.FilterType.DENSITY, density)));
                    }
                } else {
                    splitFactory.addFullSplit(
                            ImmutableList.of(Pair.of(OutputFile.FilterType.DENSITY, density)));
                }
            }
        } else {
            splitFactory.addMainApk();
        }

        return variant;
    }

    @Override
    public Class<ApplicationVariantImpl> getVariantImplementationClass() {
        return ApplicationVariantImpl.class;
    }

    public static void createApkOutputApiObjects(
            @NonNull Instantiator instantiator,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull ApkVariantImpl variant) {
        List<? extends BaseVariantOutputData> outputList = variantData.getOutputs();
        List<BaseVariantOutput> apiOutputList = Lists.newArrayListWithCapacity(outputList.size());

        for (BaseVariantOutputData variantOutputData : outputList) {
            ApkVariantOutputData apkOutput = (ApkVariantOutputData) variantOutputData;

            ApkVariantOutputImpl output = instantiator.newInstance(
                    ApkVariantOutputImpl.class, apkOutput);

            apiOutputList.add(output);
        }

        variant.addOutputs(apiOutputList);
    }

    @NonNull
    @Override
    public VariantType getVariantConfigurationType() {
        return VariantType.DEFAULT;
    }

    @Override
    public boolean hasTestScope() {
        return true;
    }

    @Override
    public void validateModel(@NonNull VariantModel model){
        // No additional checks for ApplicationVariantFactory, so just return.
    }

    @Override
    public void preVariantWork(Project project) {
        // nothing to be done here.
    }

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(DEBUG);
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    }
}
