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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildElementsCopyParams;
import com.android.build.gradle.internal.scope.BuildElementsCopyRunnable;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.workers.WorkerExecutor;

/**
 * Copy the location our various tasks outputs into a single location.
 *
 * <p>This is useful when having configuration or feature splits which are located in different
 * folders since they are produced by different tasks.
 */
public abstract class CopyOutputs extends NonIncrementalTask {

    private final WorkerExecutorFacade workers;

    @Inject
    public CopyOutputs(WorkerExecutor workerExecutor) {
        this.workers =
                Workers.INSTANCE.preferWorkers(getProject().getName(), getPath(), workerExecutor);
    }

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    @InputFiles
    public abstract DirectoryProperty getFullApks();

    @InputFiles
    @Optional
    public abstract DirectoryProperty getAbiSplits();

    @InputFiles
    @Optional
    public abstract DirectoryProperty getResourcesSplits();

    // FIX ME : add incrementality
    @Override
    protected void doTaskAction() throws IOException, ExecutionException {
        FileUtils.cleanOutputDir(getDestinationDir().get().getAsFile());

        List<Callable<BuildElements>> buildElementsCallables = new ArrayList<>();

        buildElementsCallables.add(copy(InternalArtifactType.FULL_APK, getFullApks()));
        buildElementsCallables.add(copy(InternalArtifactType.ABI_PACKAGED_SPLIT, getAbiSplits()));
        buildElementsCallables.add(
                copy(
                        InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                        getResourcesSplits()));

        ImmutableList.Builder<BuildOutput> buildOutputs = ImmutableList.builder();

        for (Callable<BuildElements> buildElementsCallable : buildElementsCallables) {
            try {
                buildOutputs.addAll(buildElementsCallable.call());
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }

        new BuildElements(buildOutputs.build()).save(getDestinationDir().get().getAsFile());
    }

    private Callable<BuildElements> copy(InternalArtifactType inputType, DirectoryProperty inputs) {
        return ExistingBuildElements.from(inputType, inputs)
                .transform(
                        workers,
                        BuildElementsCopyRunnable.class,
                        (apkInfo, inputFile) ->
                                new BuildElementsCopyParams(
                                        inputFile,
                                        new File(
                                                getDestinationDir().get().getAsFile(),
                                                inputFile.getName())))
                .intoCallable(InternalArtifactType.APK);
    }

    public static class CreationAction extends VariantTaskCreationAction<CopyOutputs> {

        private final File destinationDir;

        public CreationAction(VariantScope variantScope, File destinationDir) {
            super(variantScope);
            this.destinationDir = destinationDir;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("copyOutputs");
        }

        @NonNull
        @Override
        public Class<CopyOutputs> getType() {
            return CopyOutputs.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends CopyOutputs> taskProvider) {
            super.handleProvider(taskProvider);

            getVariantScope()
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.APK,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            CopyOutputs::getDestinationDir,
                            destinationDir.getAbsolutePath(),
                            "");
        }

        @Override
        public void configure(@NonNull CopyOutputs task) {
            super.configure(task);

            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            artifacts.setTaskInputToFinalProduct(InternalArtifactType.FULL_APK, task.getFullApks());
            artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.ABI_PACKAGED_SPLIT, task.getAbiSplits());
            artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                    task.getResourcesSplits());
        }
    }
}
