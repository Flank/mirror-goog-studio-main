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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.AtomTaskManager;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.variant.AtomVariantFactory;
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
 * Gradle plugin class for 'atom' projects.
 */
public class AtomPlugin extends BasePlugin implements Plugin<Project> {

    @Inject
    public AtomPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry);
    }

    @Override
    public Class<? extends BaseExtension> getExtensionClass() {
        return AtomExtension.class;
    }

    @NonNull
    @Override
    protected AndroidStudioStats.GradleBuildProject.PluginType getAnalyticsPluginType() {
        return AndroidStudioStats.GradleBuildProject.PluginType.ATOM;
    }

    @Override
    protected VariantFactory createVariantFactory() {
        return new AtomVariantFactory(instantiator, androidBuilder, extension);
    }

    @Override
    protected boolean isLibrary() {
        return true;
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_ATOM;
    }

    @Override
    protected TaskManager createTaskManager(@NonNull Project project,
            @NonNull AndroidBuilder androidBuilder, @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension, @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler, @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry) {
        return new AtomTaskManager(project, androidBuilder, dataBindingBuilder, extension,
                sdkHandler, ndkHandler, dependencyManager, toolingRegistry);
    }

    @Override
    public void apply(@NonNull Project project) {
        super.apply(project);
        // Default assemble task for the default-published artifact.
        // This is needed for the prepare task on the consuming project.
        project.getTasks().create("assembleDefaultAtom");
    }
}
