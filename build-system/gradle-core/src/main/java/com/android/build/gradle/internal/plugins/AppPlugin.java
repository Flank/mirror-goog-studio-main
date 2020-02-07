/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.variant.impl.ApplicationVariantImpl;
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.AppModelBuilder;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.ApplicationExtensionImpl;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.VariantApiServices;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.internal.tasks.ApplicationTaskManager;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.builder.profile.Recorder;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on the base application module */
public class AppPlugin
        extends AbstractAppPlugin<ApplicationVariantImpl, ApplicationVariantPropertiesImpl> {
    @Inject
    public AppPlugin(
            ToolingModelBuilderRegistry registry, SoftwareComponentFactory componentFactory) {
        super(registry, componentFactory);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
    }

    @Override
    protected void registerModelBuilder(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantModel variantModel,
            @NonNull BaseExtension extension,
            @NonNull ExtraModelInfo extraModelInfo) {
        registry.register(
                new AppModelBuilder(
                        globalScope,
                        variantModel,
                        (BaseAppModuleExtension) extension,
                        extraModelInfo,
                        syncIssueHandler,
                        getProjectType()));
    }


    @Override
    @NonNull
    protected Class<? extends AppExtension> getExtensionClass() {
        return BaseAppModuleExtension.class;
    }

    @NonNull
    @Override
    protected AppExtension createExtension(
            @NonNull DslServices dslServices,
            @NonNull GlobalScope globalScope,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull DefaultConfig defaultConfig,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo) {
        return project.getExtensions()
                .create(
                        "android",
                        getExtensionClass(),
                        dslServices,
                        projectOptions,
                        globalScope,
                        buildOutputs,
                        sourceSetManager,
                        extraModelInfo,
                        new ApplicationExtensionImpl(
                                globalScope.getDslServices(),
                                buildTypeContainer,
                                defaultConfig,
                                productFlavorContainer,
                                signingConfigContainer));
    }

    @NonNull
    @Override
    protected ApplicationTaskManager createTaskManager(
            @NonNull
                    List<ComponentInfo<ApplicationVariantImpl, ApplicationVariantPropertiesImpl>>
                            variants,
            @NonNull
                    List<
                                    ComponentInfo<
                                            TestComponentImpl<
                                                    ? extends TestComponentPropertiesImpl>,
                                            TestComponentPropertiesImpl>>
                            testComponents,
            boolean hasFlavors,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull Recorder threadRecorder) {
        return new ApplicationTaskManager(
                variants, testComponents, hasFlavors, globalScope, extension, threadRecorder);
    }

    @NonNull
    @Override
    protected ApplicationVariantFactory createVariantFactory(
            @NonNull VariantApiServices variantApiServices,
            @NonNull VariantPropertiesApiServices variantPropertiesApiServices,
            @NonNull GlobalScope globalScope) {
        return new ApplicationVariantFactory(
                variantApiServices, variantPropertiesApiServices, globalScope);
    }
}
