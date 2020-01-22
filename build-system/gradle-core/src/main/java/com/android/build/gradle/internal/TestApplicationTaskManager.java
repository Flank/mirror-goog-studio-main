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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST_METADATA;
import static com.android.build.gradle.internal.variant.TestVariantFactory.getTestedApksConfigurationName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.variant.impl.TestVariantPropertiesImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.tasks.CheckTestedAppObfuscation;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.model.CodeShrinker;
import com.android.builder.profile.Recorder;
import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager extends AbstractAppTaskManager<TestVariantPropertiesImpl> {

    public TestApplicationTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                extension,
                toolingRegistry,
                recorder);
    }

    @Override
    protected void doCreateTasksForVariant(
            @NonNull TestVariantPropertiesImpl testVariantProperties,
            @NonNull List<TestVariantPropertiesImpl> allComponentsWithLint) {

        createCommonTasks(testVariantProperties, allComponentsWithLint);

        Configuration testedApksConfig =
                project.getConfigurations()
                        .getByName(getTestedApksConfigurationName(testVariantProperties.getName()));

        Provider<Directory> testingApk =
                testVariantProperties
                        .getArtifacts()
                        .getFinalProduct(InternalArtifactType.APK.INSTANCE);

        // create a FileCollection that will contain the APKs to be tested.
        // FULL_APK is published only to the runtime configuration
        FileCollection testedApks =
                testedApksConfig
                        .getIncoming()
                        .artifactView(
                                config ->
                                        config.attributes(
                                                container ->
                                                        container.attribute(
                                                                ARTIFACT_TYPE, APK.getType())))
                        .getFiles();

        // same for the manifests.
        FileCollection testedManifestMetadata = getTestedManifestMetadata(testVariantProperties);

        TestApplicationTestData testData =
                new TestApplicationTestData(
                        testVariantProperties.getVariantDslInfo(),
                        testVariantProperties.getVariantSources(),
                        testVariantProperties.getVariantDslInfo()::getApplicationId,
                        testingApk,
                        testedApks);

        configureTestData(testData);

        // create the test connected check task.
        TaskProvider<DeviceProviderInstrumentTestTask> instrumentTestTask =
                taskFactory.register(
                        new DeviceProviderInstrumentTestTask.CreationAction(
                                testVariantProperties,
                                new ConnectedDeviceProvider(
                                        () ->
                                                globalScope
                                                        .getSdkComponents()
                                                        .getAdbExecutableProvider()
                                                        .get(),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(getLogger())),
                                DeviceProviderInstrumentTestTask.CreationAction.Type
                                        .INTERNAL_CONNECTED_DEVICE_PROVIDER,
                                testData,
                                testedManifestMetadata) {
                            @NonNull
                            @Override
                            public String getName() {
                                return super.getName() + VariantType.ANDROID_TEST_SUFFIX;
                            }
                        });

        Task connectedAndroidTest =
                taskFactory.findByName(
                        BuilderConstants.CONNECTED + VariantType.ANDROID_TEST_SUFFIX);
        if (connectedAndroidTest != null) {
            connectedAndroidTest.dependsOn(instrumentTestTask.getName());
        }
    }

    @Override
    protected void postJavacCreation(@NonNull ComponentPropertiesImpl componentProperties) {
        // do nothing.
    }

    @Override
    public void createLintTasks(
            @NonNull TestVariantPropertiesImpl componentProperties,
            @NonNull List<TestVariantPropertiesImpl> allComponentsWithLint) {
        // do nothing
    }

    @Override
    public void maybeCreateLintVitalTask(
            @NonNull TestVariantPropertiesImpl variant,
            @NonNull List<TestVariantPropertiesImpl> allComponentsWithLint) {
        // do nothing
    }

    @Override
    public void createGlobalLintTask() {
        // do nothing
    }

    @Override
    public void configureGlobalLintTask(
            @NonNull Collection<? extends VariantPropertiesImpl> components) {
        // do nothing
    }

    @Nullable
    @Override
    protected CodeShrinker maybeCreateJavaCodeShrinkerTask(
            @NonNull ComponentPropertiesImpl componentProperties) {
        final CodeShrinker codeShrinker = componentProperties.getVariantScope().getCodeShrinker();
        if (codeShrinker != null) {
            return doCreateJavaCodeShrinkerTask(
                    componentProperties, Objects.requireNonNull(codeShrinker), true);
        } else {
            TaskProvider<CheckTestedAppObfuscation> checkObfuscation =
                    taskFactory.register(
                            new CheckTestedAppObfuscation.CreationAction(componentProperties));
            Preconditions.checkNotNull(componentProperties.getTaskContainer().getJavacTask());
            TaskFactoryUtils.dependsOn(
                    componentProperties.getTaskContainer().getJavacTask(), checkObfuscation);
            return null;
        }
    }

    /** Returns the manifest configuration of the tested application */
    @NonNull
    private FileCollection getTestedManifestMetadata(
            @NonNull ComponentPropertiesImpl componentProperties) {
        return componentProperties
                .getVariantDependencies()
                .getCompileClasspath()
                .getIncoming()
                .artifactView(
                        config ->
                                config.attributes(
                                        container ->
                                                container.attribute(
                                                        ARTIFACT_TYPE,
                                                        MANIFEST_METADATA.getType())))
                .getFiles();
    }

    /** Creates the merge manifests task. */
    @Override
    @NonNull
    protected TaskProvider<? extends ManifestProcessorTask> createMergeManifestTask(
            @NonNull ComponentPropertiesImpl componentProperties) {

        return taskFactory.register(
                new ProcessTestManifest.CreationAction(
                        componentProperties, getTestedManifestMetadata(componentProperties)));
    }

    @Override
    protected void createVariantPreBuildTask(@NonNull ComponentPropertiesImpl componentProperties) {
        createDefaultPreBuildTask(componentProperties);
    }
}
