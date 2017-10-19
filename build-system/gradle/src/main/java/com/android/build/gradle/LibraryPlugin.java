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

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LibraryTaskManager;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.dsl.extensions.BuildPropertiesImpl;
import com.android.build.gradle.internal.api.dsl.extensions.EmbeddedTestPropertiesImpl;
import com.android.build.gradle.internal.api.dsl.extensions.LibraryExtensionImpl;
import com.android.build.gradle.internal.api.dsl.extensions.OnDeviceTestPropertiesImpl;
import com.android.build.gradle.internal.api.dsl.extensions.VariantAwarePropertiesImpl;
import com.android.build.gradle.internal.api.dsl.extensions.VariantOrExtensionPropertiesImpl;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.variant.LibraryVariantFactory;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant2.LibAndroidTestVariantFactory;
import com.android.build.gradle.internal.variant2.VariantFactory2;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.AndroidProject;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'library' projects. */
public class LibraryPlugin extends BasePlugin<LibraryExtensionImpl> implements Plugin<Project> {

    @Inject
    public LibraryPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
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
                        getExtensionClass(),
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
    protected Class<? extends BaseExtension> getExtensionClass() {
        return LibraryExtension.class;
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.LIBRARY;
    }

    @NonNull
    @Override
    protected VariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig androidConfig) {
        return new LibraryVariantFactory(globalScope, androidBuilder, instantiator, androidConfig);
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_LIBRARY;
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
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        return new LibraryTaskManager(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                androidConfig,
                sdkHandler,
                toolingRegistry,
                recorder);
    }

    @Override
    public void apply(@NonNull Project project) {
        super.apply(project);
        // Default assemble task for the default-published artifact.
        // This is needed for the prepare task on the consuming project.
        project.getTasks().create("assembleDefault");
    }

    @NonNull
    @Override
    protected LibraryExtensionImpl createNewExtension(
            @NonNull BuildPropertiesImpl buildProperties,
            @NonNull VariantOrExtensionPropertiesImpl variantExtensionProperties,
            @NonNull VariantAwarePropertiesImpl variantAwareProperties) {
        EvalIssueReporter issueReporter = extraModelInfo;

        return project.getExtensions()
                .create(
                        "android",
                        LibraryExtensionImpl.class,
                        buildProperties,
                        variantExtensionProperties,
                        variantAwareProperties,
                        new EmbeddedTestPropertiesImpl(issueReporter),
                        new OnDeviceTestPropertiesImpl(issueReporter),
                        issueReporter);
    }

    @NonNull
    @Override
    protected List<VariantFactory2<LibraryExtensionImpl>> getVariantFactories() {
        return ImmutableList.of(
                new com.android.build.gradle.internal.variant2.LibraryVariantFactory(
                        extraModelInfo),
                new LibAndroidTestVariantFactory(extraModelInfo));
    }
}
