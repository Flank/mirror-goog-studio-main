/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.AndroidSourceSetFactory;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.managed.AndroidConfig;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.LibraryRequest;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.google.common.collect.Lists;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.internal.reflect.Instantiator;

/**
 * Utility functions for initializing an AndroidConfig.
 */
public class AndroidConfigHelper {
    public static void configure(
            @NonNull AndroidConfig model,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull Instantiator instantiator) {
        model.setDefaultPublishConfig(BuilderConstants.RELEASE);
        model.setGeneratePureSplits(false);
        model.setDeviceProviders(Lists.<DeviceProvider>newArrayList());
        model.setTestServers(Lists.<TestServer>newArrayList());
        model.setAaptOptions(instantiator.newInstance(AaptOptions.class));
        model.setDexOptions(instantiator.newInstance(DexOptions.class, extraModelInfo));
        model.setLintOptions(instantiator.newInstance(LintOptions.class));
        model.setTestOptions(instantiator.newInstance(TestOptions.class));
        model.setCompileOptions(instantiator.newInstance(CompileOptions.class));
        model.setPackagingOptions(instantiator.newInstance(PackagingOptions.class));
        model.setJacoco(instantiator.newInstance(JacocoOptions.class));
        model.setAdbOptions(instantiator.newInstance(AdbOptions.class));
        model.setSplits(instantiator.newInstance(Splits.class, instantiator));
        model.setLibraryRequests(Lists.<LibraryRequest>newArrayList());
    }


    public static NamedDomainObjectContainer<AndroidSourceSet> createSourceSetsContainer(
            @NonNull final Project project,
            @NonNull Instantiator instantiator,
            final boolean publishPackage) {
        NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer =
                project.container(
                        AndroidSourceSet.class,
                        new AndroidSourceSetFactory(instantiator, project, publishPackage));

        sourceSetsContainer.whenObjectAdded(
                sourceSet -> {
                    ConfigurationContainer configurations = project.getConfigurations();

                    createConfiguration(
                            configurations,
                            sourceSet.getCompileConfigurationName(),
                            "Classpath for compiling the " + sourceSet.getName() + " sources.");

                    String packageConfigDescription;
                    if (publishPackage) {
                        packageConfigDescription =
                                "Classpath only used when publishing '"
                                        + sourceSet.getName()
                                        + "'.";
                    } else {
                        packageConfigDescription =
                                "Classpath packaged with the compiled '"
                                        + sourceSet.getName()
                                        + "' classes.";
                    }
                    createConfiguration(
                            configurations,
                            sourceSet.getPackageConfigurationName(),
                            packageConfigDescription);

                    createConfiguration(
                            configurations,
                            sourceSet.getProvidedConfigurationName(),
                            "Classpath for only compiling the "
                                    + sourceSet.getName()
                                    + " sources.");

                    createConfiguration(
                            configurations,
                            sourceSet.getWearAppConfigurationName(),
                            "Link to a wear app to embed for object '"
                                    + sourceSet.getName()
                                    + "}'.");

                    createConfiguration(
                            configurations,
                            sourceSet.getAnnotationProcessorConfigurationName(),
                            "Classpath for the annotation processor for '"
                                    + sourceSet.getName()
                                    + "'.");

                    createConfiguration(
                            configurations,
                            sourceSet.getWearAppConfigurationName(),
                            "Link to a wear app to embed for object '${sourceSet.name}'.");

                    createConfiguration(
                            configurations,
                            sourceSet.getJackPluginConfigurationName(),
                            String.format(
                                    "Classpath for the '%s' Jack plugins.", sourceSet.getName()));

                    sourceSet.setRoot(String.format("src/%s", sourceSet.getName()));
                });
        return sourceSetsContainer;
    }

    private static void createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String configurationName,
            @NonNull String configurationDescription) {
        Configuration configuration = configurations.findByName(configurationName);
        if (configuration == null) {
            configuration = configurations.create(configurationName);
        }

        // Disable modification to configurations as this causes issues when accessed through the
        // tooling-api.  Check that it works with Studio's ImportProjectAction before re-enabling
        // them.
        //configuration.setVisible(false);
        //configuration.setDescription(configurationDescription);
    }
}
