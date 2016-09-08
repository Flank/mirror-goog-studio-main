/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ApplicationTaskManager;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.builder.core.AndroidBuilder;

import com.android.builder.model.AndroidProject;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import javax.inject.Inject;

/**
 * Gradle plugin class for 'application' projects.
 */
public class AppPlugin extends BasePlugin implements Plugin<Project> {
    @Inject
    public AppPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry);
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_APP;
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return AppExtension.class;
    }

    @NonNull
    @Override
    protected AndroidStudioStats.GradleBuildProject.PluginType getAnalyticsPluginType() {
        return AndroidStudioStats.GradleBuildProject.PluginType.APPLICATION;
    }

    @Override
    protected TaskManager createTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry) {
        return new ApplicationTaskManager(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                toolingRegistry);
    }

    @Override
    public void apply(@NonNull Project project) {
        super.apply(project);
    }

    @Override
    protected VariantFactory createVariantFactory() {
        return new ApplicationVariantFactory(instantiator, androidBuilder, extension);
    }
}
