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

package com.android.build.gradle;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.InstantAppTaskManager;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.variant.InstantAppVariantFactory;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * Gradle plugin class for 'instantApp' projects.
 */
public class InstantAppPlugin extends BasePlugin implements Plugin<Project> {

    @Inject
    public InstantAppPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry);
    }

    @NonNull
    @Override
    protected BaseExtension createExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo) {
        return project.getExtensions()
                .create(
                        "android",
                        InstantAppExtension.class,
                        project,
                        projectOptions,
                        instantiator,
                        androidBuilder,
                        sdkHandler,
                        buildTypeContainer,
                        productFlavorContainer,
                        signingConfigContainer,
                        buildOutputs,
                        extraModelInfo);
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.INSTANTAPP;
    }

    @NonNull
    @Override
    protected VariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig androidConfig) {
        return new InstantAppVariantFactory(
                globalScope, instantiator, androidBuilder, androidConfig);
    }

    @NonNull
    @Override
    protected TaskManager createTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig androidConfig,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder) {
        return new InstantAppTaskManager(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                androidConfig,
                sdkHandler,
                dependencyManager,
                toolingRegistry,
                threadRecorder);
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_INSTANTAPP;
    }

    @Override
    public void apply(@NonNull Project project) {
        super.apply(project);
    }
}
