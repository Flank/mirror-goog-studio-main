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
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.variant.impl.LibraryVariantPropertiesImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.api.LibraryVariantImpl;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import org.gradle.api.NamedDomainObjectContainer;

public class LibraryVariantFactory extends BaseVariantFactory {

    public LibraryVariantFactory(@NonNull GlobalScope globalScope) {
        super(globalScope);
    }

    @NonNull
    @Override
    public VariantImpl createVariantObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantDslInfo variantDslInfo) {
        return globalScope
                .getDslScope()
                .getObjectFactory()
                .newInstance(
                        com.android.build.api.variant.impl.LibraryVariantImpl.class,
                        variantDslInfo,
                        componentIdentity);
    }

    @NonNull
    @Override
    public VariantPropertiesImpl createVariantPropertiesObject(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull BuildArtifactsHolder artifacts,
            @NonNull VariantScope variantScope,
            @NonNull BaseVariantData variantData,
            @NonNull TransformManager transformManager) {
        LibraryVariantPropertiesImpl variantProperties =
                globalScope
                        .getDslScope()
                        .getObjectFactory()
                        .newInstance(
                                LibraryVariantPropertiesImpl.class,
                                componentIdentity,
                                variantDslInfo,
                                variantDependencies,
                                variantSources,
                                paths,
                                artifacts,
                                variantScope,
                                variantData,
                                transformManager,
                                globalScope.getDslScope(),
                                globalScope);

        // create default output
        String name =
                globalScope.getProjectBaseName()
                        + "-"
                        + variantDslInfo.getBaseName()
                        + "."
                        + BuilderConstants.EXT_LIB_ARCHIVE;
        variantProperties.addVariantOutput(variantData.getOutputFactory().addMainOutput(name));

        return variantProperties;
    }

    @NonNull
    @Override
    public BaseVariantData createVariantData(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull BuildArtifactsHolder artifacts,
            @NonNull GlobalScope globalScope,
            @NonNull TaskManager taskManager,
            @NonNull MutableTaskContainer taskContainer) {
        return new LibraryVariantData(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                globalScope,
                taskManager,
                taskContainer);
    }


    @Override
    @NonNull
    public Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData) {
        return LibraryVariantImpl.class;
    }

    @NonNull
    @Override
    public VariantType getVariantType() {
        return VariantTypeImpl.LIBRARY;
    }

    @Override
    public boolean hasTestScope() {
        return true;
    }

    /** * Prevent customization of applicationId or applicationIdSuffix. */
    @Override
    public void validateModel(@NonNull VariantInputModel model) {
        super.validateModel(model);

        IssueReporter issueReporter = globalScope.getDslScope().getIssueReporter();

        if (model.getDefaultConfig().getProductFlavor().getApplicationId() != null) {
            String applicationId = model.getDefaultConfig().getProductFlavor().getApplicationId();
            issueReporter.reportError(
                    Type.GENERIC,
                    "Library projects cannot set applicationId. "
                            + "applicationId is set to '"
                            + applicationId
                            + "' in default config.",
                    applicationId);
        }

        if (model.getDefaultConfig().getProductFlavor().getApplicationIdSuffix() != null) {
            String applicationIdSuffix =
                    model.getDefaultConfig().getProductFlavor().getApplicationIdSuffix();
            issueReporter.reportError(
                    Type.GENERIC,
                    "Library projects cannot set applicationIdSuffix. "
                            + "applicationIdSuffix is set to '"
                            + applicationIdSuffix
                            + "' in default config.",
                    applicationIdSuffix);
        }

        for (BuildTypeData buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().getApplicationIdSuffix() != null) {
                String applicationIdSuffix = buildType.getBuildType().getApplicationIdSuffix();
                issueReporter.reportError(
                        Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. "
                                + "applicationIdSuffix is set to '"
                                + applicationIdSuffix
                                + "' in build type '"
                                + buildType.getBuildType().getName()
                                + "'.",
                        applicationIdSuffix);
            }
        }
        for (ProductFlavorData productFlavor : model.getProductFlavors().values()) {
            if (productFlavor.getProductFlavor().getApplicationId() != null) {
                String applicationId = productFlavor.getProductFlavor().getApplicationId();
                issueReporter.reportError(
                        Type.GENERIC,
                        "Library projects cannot set applicationId. "
                                + "applicationId is set to '"
                                + applicationId
                                + "' in flavor '"
                                + productFlavor.getProductFlavor().getName()
                                + "'.",
                        applicationId);
            }

            if (productFlavor.getProductFlavor().getApplicationIdSuffix() != null) {
                String applicationIdSuffix =
                        productFlavor.getProductFlavor().getApplicationIdSuffix();
                issueReporter.reportError(
                        Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. "
                                + "applicationIdSuffix is set to '"
                                + applicationIdSuffix
                                + "' in flavor '"
                                + productFlavor.getProductFlavor().getName()
                                + "'.",
                        applicationIdSuffix);
            }
        }
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
