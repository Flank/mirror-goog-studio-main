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

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.CheckTestedAppObfuscation;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Objects;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager extends ApplicationTaskManager {

    private FileCollection mTargetManifestConfiguration = null;

    public TestApplicationTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(@NonNull VariantScope variantScope) {

        super.createTasksForVariantScope(variantScope);

        Configuration testedApksConfig =
                project.getConfigurations()
                        .getByName(
                                getTestedApksConfigurationName(variantScope.getFullVariantName()));

        BuildableArtifact testingApk =
                variantScope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.APK);

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
        FileCollection testedManifestMetadata =
                getTestedManifestMetadata(variantScope.getVariantData());

        TestApplicationTestData testData =
                new TestApplicationTestData(
                        variantScope.getVariantConfiguration(),
                        variantScope.getVariantData()::getApplicationId,
                        testingApk,
                        new BuildableArtifactImpl(testedApks, globalScope.getDslScope()));

        configureTestData(testData);

        // create the test connected check task.
        DeviceProviderInstrumentTestTask instrumentTestTask =
                taskFactory.create(
                        new DeviceProviderInstrumentTestTask.ConfigAction(
                                variantScope,
                                new ConnectedDeviceProvider(
                                        sdkHandler.getSdkInfo().getAdb(),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(getLogger())),
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
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // do nothing.
    }

    @Override
    public void createLintTasks(VariantScope scope) {
        // do nothing
    }

    @Override
    public void createGlobalLintTask() {
        // do nothing
    }

    @Override
    public void configureGlobalLintTask(@NonNull Collection<VariantScope> variants) {
        // do nothing
    }

    @Override
    protected boolean isTestedAppObfuscated(@NonNull VariantScope variantScope) {
        return variantScope.getCodeShrinker() == CodeShrinker.PROGUARD
                || variantScope.getCodeShrinker() == CodeShrinker.R8;
    }

    @Nullable
    @Override
    protected CodeShrinker maybeCreateJavaCodeShrinkerTransform(
            @NonNull VariantScope variantScope) {
        if (isTestedAppObfuscated(variantScope)) {
            return doCreateJavaCodeShrinkerTransform(
                    variantScope,
                    Objects.requireNonNull(variantScope.getCodeShrinker()),
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.APK_MAPPING));
        } else {
            CheckTestedAppObfuscation checkObfuscation =
                    taskFactory.create(new CheckTestedAppObfuscation.ConfigAction(variantScope));
            Preconditions.checkNotNull(variantScope.getJavacTask());
            variantScope.getJavacTask().dependsOn(checkObfuscation);
            return null;
        }
    }

    /** Returns the manifest configuration of the tested application */
    @NonNull
    private FileCollection getTestedManifestMetadata(@NonNull BaseVariantData variantData) {
        if (mTargetManifestConfiguration == null){
            mTargetManifestConfiguration =
                    variantData
                            .getVariantDependency()
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

        return mTargetManifestConfiguration;
    }

    /** Creates the merge manifests task. */
    @Override
    @NonNull
    protected ManifestProcessorTask createMergeManifestTask(@NonNull VariantScope variantScope) {
        return taskFactory.create(
                new ProcessTestManifest.ConfigAction(
                        variantScope,
                        new BuildableArtifactImpl(
                                getTestedManifestMetadata(variantScope.getVariantData()),
                                variantScope.getGlobalScope().getDslScope())));
    }

    @Override
    protected DefaultTask createVariantPreBuildTask(@NonNull VariantScope scope) {
        return createDefaultPreBuildTask(scope);
    }
}
