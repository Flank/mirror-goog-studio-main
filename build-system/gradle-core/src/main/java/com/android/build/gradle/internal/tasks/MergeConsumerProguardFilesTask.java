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

package com.android.build.gradle.internal.tasks;

import static com.android.build.gradle.internal.scope.InternalArtifactType.GENERATED_PROGUARD_FILE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.FeaturePlugin;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.errors.EvalIssueException;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

/** Configuration action for a merge-Proguard-files task. */
public abstract class MergeConsumerProguardFilesTask extends MergeFileTask {

    private boolean isDynamicFeature;
    private boolean isBaseFeature;
    private boolean hasFeaturePlugin;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getConsumerProguardFiles();

    @Override
    public void doTaskAction() throws IOException {
        final Project project = getProject();

        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseFeature) {
            ExportConsumerProguardFilesTask.checkProguardFiles(
                    project,
                    isDynamicFeature,
                    hasFeaturePlugin,
                    getConsumerProguardFiles().getFiles(),
                    errorMessage -> {
                        throw new EvalIssueException(errorMessage);
                    });
        }
        super.doTaskAction();
    }

    public static class CreationAction extends TaskCreationAction<MergeConsumerProguardFilesTask> {

        @NonNull private final VariantScope variantScope;

        public CreationAction(@NonNull VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("merge", "ConsumerProguardFiles");
        }

        @NonNull
        @Override
        public Class<MergeConsumerProguardFilesTask> getType() {
            return MergeConsumerProguardFilesTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends MergeConsumerProguardFilesTask> taskProvider) {
            super.handleProvider(taskProvider);

            variantScope
                    .getArtifacts()
                    .producesFile(
                            InternalArtifactType.MERGED_CONSUMER_PROGUARD_FILE.INSTANCE,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            MergeConsumerProguardFilesTask::getOutputFile,
                            SdkConstants.FN_PROGUARD_TXT);
        }

        @Override
        public void configure(@NonNull MergeConsumerProguardFilesTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            GlobalScope globalScope = variantScope.getGlobalScope();
            Project project = globalScope.getProject();

            task.hasFeaturePlugin = project.getPlugins().hasPlugin(FeaturePlugin.class);
            task.isBaseFeature =
                    task.hasFeaturePlugin && globalScope.getExtension().getBaseFeature();
            if (task.isBaseFeature) {
                task.isDynamicFeature = variantScope.getType().isDynamicFeature();
            }

            task.getConsumerProguardFiles()
                    .from(variantScope.getConsumerProguardFilesForFeatures());

            ConfigurableFileCollection inputFiles =
                    project.files(
                            task.getConsumerProguardFiles(),
                            variantScope
                                    .getArtifacts()
                                    .getFinalProduct(GENERATED_PROGUARD_FILE.INSTANCE));
            task.setInputFiles(inputFiles);
        }
    }
}
