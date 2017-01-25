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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_APK;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_METADATA;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.manifmerger.ManifestMerger2;
import java.io.File;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.file.FileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager extends ApplicationTaskManager {

    private FileCollection mTestTargetMapping = null;
    private FileCollection mTargetManifestConfiguration = null;

    public TestApplicationTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {

        super.createTasksForVariantData(tasks, variantData);

        Configuration compileConfiguration =
                variantData.getVariantDependency().getCompileConfiguration();
        final ResolvableDependencies incoming = compileConfiguration.getIncoming();

        // TODO: replace hack below with a FileCollection that will contain the testing APK,
        // obtained from the scope anchor types.
        BaseVariantOutputData baseVariantOutputData = variantData.getOutputs().get(0);
        File testingApk = baseVariantOutputData.getOutputFile();

        // create a FileCollection that will contain the APKs to be tested.
        FileCollection testedApks = incoming.artifactView()
                .attributes(container -> container.attribute(ARTIFACT_TYPE, TYPE_APK)).getFiles();

        // same for the metadata
        FileCollection testTargetMetadata = incoming.artifactView()
                .attributes(container -> container.attribute(ARTIFACT_TYPE, TYPE_METADATA)).getFiles();

        TestApplicationTestData testData = new TestApplicationTestData(
                variantData.getVariantConfiguration(),
                variantData.getApplicationId(),
                testingApk,
                testedApks);

        configureTestData(testData);

        // create the test connected check task.
        AndroidTask<DeviceProviderInstrumentTestTask> instrumentTestTask =
                getAndroidTasks().create(
                        tasks,
                        new DeviceProviderInstrumentTestTask.ConfigAction(
                                variantData.getScope(),
                                new ConnectedDeviceProvider(
                                        sdkHandler.getSdkInfo().getAdb(),
                                        getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(getLogger())),
                                testData,
                                testTargetMetadata) {
                            @NonNull
                            @Override
                            public String getName() {
                                return super.getName() + VariantType.ANDROID_TEST.getSuffix();
                            }
                        });

        Task connectedAndroidTest = tasks.named(BuilderConstants.CONNECTED
                + VariantType.ANDROID_TEST.getSuffix());
        if (connectedAndroidTest != null) {
            connectedAndroidTest.dependsOn(instrumentTestTask.getName());
        }
    }

    @Override
    protected boolean isTestedAppMinified(@NonNull VariantScope variantScope) {
        return getTestTargetMapping(variantScope) != null;
    }

    @Override
    protected void createMinifyTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope variantScope,
            boolean createJarFile) {
        if (getTestTargetMapping(variantScope) != null) {
            doCreateMinifyTransform(taskFactory, variantScope, getTestTargetMapping(variantScope));
        }
    }

    /** Returns the mapping configuration of the tested app, if it is used */
    @Nullable
    private FileCollection getTestTargetMapping(@NonNull VariantScope variantScope){
        if (mTestTargetMapping == null){
            mTestTargetMapping = variantScope.getVariantData().getVariantDependency()
                    .getCompileConfiguration().getIncoming().artifactView()
                    .attributes(container -> container.attribute(ARTIFACT_TYPE, TYPE_METADATA)).getFiles();
        }

        if (mTestTargetMapping.getFiles().isEmpty()) {
            return null;
        }
        else {
            return mTestTargetMapping;
        }
    }

    /** Returns the manifest configuration of the tested application */
    @NonNull
    private FileCollection getTestTargetMetadata(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        if (mTargetManifestConfiguration == null){
            mTargetManifestConfiguration = variantData.getVariantDependency()
                    .getCompileConfiguration().getIncoming().artifactView()
                    .attributes(container -> container.attribute(ARTIFACT_TYPE, TYPE_METADATA)).getFiles();
        }

        return mTargetManifestConfiguration;
    }

    @Override
    @NonNull
    protected TaskConfigAction<? extends ManifestProcessorTask> getMergeManifestConfig(
            @NonNull VariantOutputScope scope,
            @NonNull List<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        return new ProcessTestManifest.ConfigAction(
                scope.getVariantScope(),
                getTestTargetMetadata(scope.getVariantScope().getVariantData()));
    }
}
