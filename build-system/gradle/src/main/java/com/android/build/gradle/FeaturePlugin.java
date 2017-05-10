/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.MultiTypeTaskManager;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.variant.MultiTypeVariantFactory;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'feature' projects. */
public class FeaturePlugin extends LibraryPlugin {

    @Inject
    public FeaturePlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry);
    }

    @Override
    public void apply(@NonNull Project project) {
        // FIXME: Remove this once aapt2 is re-enabled by default.
        project.getExtensions()
                .getByType(ExtraPropertiesExtension.class)
                .set(BooleanOption.ENABLE_AAPT2.getPropertyName(), true);

        super.apply(project);

        // create the configuration used to declare the feature split in the base split.
        Configuration featureSplit =
                project.getConfigurations().maybeCreate(VariantDependencies.CONFIG_NAME_FEATURE);
        featureSplit.setCanBeConsumed(false);
        featureSplit.setCanBeResolved(false);

        // create the configuration used to declare the application id to the base feature.
        Configuration application =
                project.getConfigurations()
                        .maybeCreate(VariantDependencies.CONFIG_NAME_APPLICATION);
        application.setCanBeConsumed(false);
        application.setCanBeResolved(false);
    }

    @NonNull
    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return FeatureExtension.class;
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.FEATURE;
    }

    @NonNull
    @Override
    protected VariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig androidConfig) {
        return new MultiTypeVariantFactory(
                globalScope, androidBuilder, instantiator, androidConfig);
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_FEATURE;
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
            @NonNull Recorder recorder) {
        return new MultiTypeTaskManager(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                androidConfig,
                sdkHandler,
                dependencyManager,
                toolingRegistry,
                recorder);
    }
}
