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
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.incremental.InstantRunWrapperTask;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformStream;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.InstantRunSplitApkBuilder;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.tasks.AndroidJarTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * TaskManager for creating tasks in an Android application project.
 */
public class ApplicationTaskManager extends TaskManager {

    public ApplicationTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry) {
        super(
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
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        assert variantData instanceof ApplicationVariantData;

        final VariantScope variantScope = variantData.getScope();

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        handleMicroApp(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest(s)
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeAppManifestsTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to create the res values
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createGenerateResValuesTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to compile renderscript files.
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createRenderscriptTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to merge the resource folders
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeResourcesTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to merge the asset folders
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeAssetsTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to create the BuildConfig class
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createBuildConfigTask(tasks, variantScope);
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        // Add a task to process the Android Resources and generate source files
                        createApkProcessResTask(tasks, variantScope);

                        // Add a task to process the java resources
                        createProcessJavaResTasks(tasks, variantScope);
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_AIDL_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createAidlTask(tasks, variantScope);
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_SHADER_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createShaderTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add NDK tasks
        if (!isComponentModelPlugin) {
            ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_NDK_TASK,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() {
                            createNdkTasks(variantScope);
                            return null;
                        }
                    });
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(tasks, getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add external native build tasks
        ThreadRecorder.get().record(
                ExecutionType.APP_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createExternalNativeBuildJsonGenerators(tasks, variantScope);
                        createExternalNativeBuildTasks(tasks, variantScope);
                        return null;
                    }
                }
        );

        // Add a task to merge the jni libs folders
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeJniLibFoldersTasks(tasks, variantScope);
                        return null;
                    }
                });

        // Add a compile task
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_COMPILE_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        CoreJackOptions jackOptions =
                                variantData.getVariantConfiguration().getJackOptions();
                        AndroidTask<? extends JavaCompile> javacTask =
                                createJavacTask(tasks, variantScope);

                        if (jackOptions.isEnabled()) {
                            AndroidTask<TransformTask> jackTask =
                                    createJackTask(tasks, variantScope, true /*compileJavaSource*/);
                            setJavaCompilerTask(jackTask, tasks, variantScope);
                        } else {
                            addJavacClassesStream(variantScope);
                            setJavaCompilerTask(javacTask, tasks, variantScope);
                            getAndroidTasks().create(tasks,
                                    new AndroidJarTask.JarClassesConfigAction(variantScope));
                            createPostCompilationTasks(tasks, variantScope);
                        }
                        return null;
                    }
                });

        // Add data binding tasks if enabled
        if (extension.getDataBinding().isEnabled()) {
            createDataBindingTasks(tasks, variantScope);
        }

        if (variantData.getSplitHandlingPolicy().equals(
                SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY)) {
            if (getExtension().getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException("Pure splits can only be used with buildtools 21 and later");
            }

            ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_SPLIT_TASK,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() {
                            createSplitTasks(tasks, variantScope);
                            return null;
                        }
                    });
        }

        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        @Nullable
                        AndroidTask<InstantRunWrapperTask> fullBuildInfoGeneratorTask
                                = createInstantRunPackagingTasks(tasks, variantScope);
                        createPackagingTask(tasks, variantScope, true /*publishApk*/,
                                fullBuildInfoGeneratorTask);
                        return null;
                    }
                });

        // create the lint tasks.
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_LINT_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createLintTasks(tasks, variantScope);
                        return null;
                    }
                });
    }

    /**
     * Create tasks related to creating pure split APKs containing sharded dex files.
     */
    @Nullable
    protected AndroidTask<InstantRunWrapperTask> createInstantRunPackagingTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        if (getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE) {
            return null;
        }

        // create a buildInfoGeneratorTask that will only be invoked if a assembleVARIANT is called.
        AndroidTask<InstantRunWrapperTask> fullBuildInfoGeneratorTask = getAndroidTasks()
                .create(tasks, new InstantRunWrapperTask.ConfigAction(
                    variantScope, InstantRunWrapperTask.TaskType.FULL, getLogger()));

        InstantRunPatchingPolicy patchingPolicy =
                variantScope.getInstantRunBuildContext().getPatchingPolicy();

        if (patchingPolicy == InstantRunPatchingPolicy.MULTI_APK) {
            BaseVariantOutputData outputData =
                    variantScope.getVariantData().getOutputs().get(0);
            PackagingScope packagingScope = new DefaultGradlePackagingScope(outputData.getScope());

            AndroidTask<InstantRunSplitApkBuilder> splitApk =
                    getAndroidTasks().create(tasks,
                            new InstantRunSplitApkBuilder.ConfigAction(packagingScope));

            TransformManager transformManager = variantScope.getTransformManager();
            for (TransformStream stream : transformManager.getStreams(StreamFilter.DEX)) {
                // TODO Optimize to avoid creating too many actions
                splitApk.dependsOn(tasks, stream.getDependencies());
            }
            variantScope.getAssembleTask().dependsOn(tasks, splitApk.get(tasks));

            // if the assembleVariant task run, make sure it also runs the task to generate
            // the build-info.xml.
            variantScope.getAssembleTask().dependsOn(
                    tasks,
                    fullBuildInfoGeneratorTask.get(tasks));

            // make sure the split APK task is run before we generate the build-info.xml
            variantScope.getInstantRunAnchorTask().dependsOn(tasks, splitApk);
            variantScope.getInstantRunIncrementalTask().dependsOn(tasks, splitApk);
        }
        return fullBuildInfoGeneratorTask;
    }

    @NonNull
    @Override
    protected Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /**
     * Configure variantData to generate embedded wear application.
     */
    private void handleMicroApp(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        if (variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
            // get all possible configurations for the variant. We'll take the highest priority
            // of them that have a file.
            List<String> wearConfigNames = variantData.getWearConfigNames();

            for (String configName : wearConfigNames) {
                Configuration config = project.getConfigurations().findByName(
                        configName);
                // this shouldn't happen, but better safe.
                if (config == null) {
                    continue;
                }

                Set<File> file = config.getFiles();

                int count = file.size();
                if (count == 1) {
                    createGenerateMicroApkDataTask(tasks, scope, config);
                    // found one, bail out.
                    return;
                } else if (count > 1) {
                    throw new RuntimeException(String.format(
                            "Configuration '%s' resolves to more than one apk.", configName));
                }
            }
        }
    }
}
