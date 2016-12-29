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
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.LibraryVariantImpl;
import com.android.build.gradle.internal.api.LibraryVariantOutputImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.google.common.collect.Lists;
import java.util.List;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;

public class LibraryVariantFactory implements VariantFactory {

    @NonNull
    private Instantiator instantiator;
    @NonNull
    private final AndroidConfig extension;
    @NonNull
    private final AndroidBuilder androidBuilder;

    public LibraryVariantFactory(
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
        return new LibraryVariantData(
                extension,
                taskManager,
                variantConfiguration,
                androidBuilder.getErrorReporter(),
                recorder);
    }

    @Override
    @NonNull
    public LibraryVariant createVariantApi(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        LibraryVariantImpl variant = instantiator.newInstance(
                LibraryVariantImpl.class, variantData, androidBuilder, readOnlyObjectProvider);

        // now create the output objects
        List<? extends BaseVariantOutputData> outputList = variantData.getOutputs();
        List<BaseVariantOutput> apiOutputList = Lists.newArrayListWithCapacity(outputList.size());

        for (BaseVariantOutputData variantOutputData : outputList) {
            LibVariantOutputData libOutput = (LibVariantOutputData) variantOutputData;

            LibraryVariantOutputImpl output = instantiator.newInstance(
                    LibraryVariantOutputImpl.class, libOutput);

            apiOutputList.add(output);
        }

        variant.addOutputs(apiOutputList);

        return variant;
    }

    @NonNull
    @Override
    public VariantType getVariantConfigurationType() {
        return VariantType.LIBRARY;
    }

    @Override
    public boolean hasTestScope() {
        return true;
    }

    /***
     * Prevent customization of applicationId or applicationIdSuffix.
     */
    @Override
    public void validateModel(@NonNull VariantModel model) {
        ErrorReporter errorReporter = androidBuilder.getErrorReporter();

        if (model.getDefaultConfig().getProductFlavor().getApplicationId() != null) {
            String applicationId = model.getDefaultConfig().getProductFlavor().getApplicationId();
            errorReporter.handleSyncError(
                    applicationId,
                    SyncIssue.TYPE_GENERIC,
                    "Library projects cannot set applicationId. " +
                    "applicationId is set to '" + applicationId + "' in default config.");
        }

        if (model.getDefaultConfig().getProductFlavor().getApplicationIdSuffix() != null) {
            String applicationIdSuffix =
                    model.getDefaultConfig().getProductFlavor().getApplicationIdSuffix();
            errorReporter.handleSyncError(
                    applicationIdSuffix,
                    SyncIssue.TYPE_GENERIC,
                    "Library projects cannot set applicationIdSuffix. " +
                    "applicationIdSuffix is set to '" + applicationIdSuffix + "' in default config.");
        }

        for (BuildTypeData buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().getApplicationIdSuffix() != null) {
                String applicationIdSuffix = buildType.getBuildType().getApplicationIdSuffix();
                errorReporter.handleSyncError(
                        applicationIdSuffix,
                        SyncIssue.TYPE_GENERIC,
                        "Library projects cannot set applicationIdSuffix. " +
                        "applicationIdSuffix is set to '" + applicationIdSuffix +
                        "' in build type '" + buildType.getBuildType().getName() + "'.");
            }
        }
        for (ProductFlavorData productFlavor : model.getProductFlavors().values()) {
            if (productFlavor.getProductFlavor().getApplicationId() != null) {
                String applicationId = productFlavor.getProductFlavor().getApplicationId();
                errorReporter.handleSyncError(
                        applicationId,
                        SyncIssue.TYPE_GENERIC,
                        "Library projects cannot set applicationId. " +
                        "applicationId is set to '" + applicationId + "' in flavor '" +
                        productFlavor.getProductFlavor().getName() + "'.");
            }

            if (productFlavor.getProductFlavor().getApplicationIdSuffix() != null) {
                String applicationIdSuffix =
                        productFlavor.getProductFlavor().getApplicationIdSuffix();
                errorReporter.handleSyncError(
                        applicationIdSuffix,
                        SyncIssue.TYPE_GENERIC,
                        "Library projects cannot set applicationIdSuffix. " +
                        "applicationIdSuffix is set to '" + applicationIdSuffix +
                        "' in flavor '" + productFlavor.getProductFlavor().getName() + "'.");
            }
        }

        // Jack is not supported in library project.
        for (BuildTypeData buildType: model.getBuildTypes().values()) {
            if (Boolean.TRUE.equals(buildType.getBuildType().getJackOptions().isEnabled())) {
                errorReporter.handleSyncError(
                        buildType.getBuildType().getName(),
                        SyncIssue.TYPE_GENERIC,
                        "Library projects cannot enable Jack. " +
                        "Jack is enabled in buildType '" + buildType.getBuildType().getName() +
                        "'.");
            }
        }

        for (ProductFlavorData productFlavor : model.getProductFlavors().values()) {
            if (Boolean.TRUE.equals(
                    productFlavor.getProductFlavor().getJackOptions().isEnabled())) {
                errorReporter.handleSyncError(
                        productFlavor.getProductFlavor().getName(),
                        SyncIssue.TYPE_GENERIC,
                        "Library projects cannot enable Jack. " +
                        "Jack is enabled in productFlavor '" +
                        productFlavor.getProductFlavor().getName() + "'.");
            }
        }

        if (Boolean.TRUE.equals(
                model.getDefaultConfig().getProductFlavor().getJackOptions().isEnabled())) {
            errorReporter.handleSyncError(
                    model.getDefaultConfig().getProductFlavor().getName(),
                    SyncIssue.TYPE_GENERIC,
                    "Library projects cannot enable Jack. " +
                    "Jack is enabled in default config.");
        }
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
