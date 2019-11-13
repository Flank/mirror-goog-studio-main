/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment;
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.errors.EvalIssueReporter;
import com.android.ide.common.process.ProcessException;
import java.io.IOException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Task wrapper around ExternalNativeJsonGenerator. */
public abstract class ExternalNativeBuildJsonTask extends NonIncrementalTask {

    private EvalIssueReporter evalIssueReporter;
    private Provider<ExternalNativeJsonGenerator> generator;

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRenderscriptSources();

    @Override
    protected void doTaskAction() throws ProcessException, IOException {
        try (ThreadLoggingEnvironment ignore =
                new IssueReporterLoggingEnvironment(evalIssueReporter)) {
            generator.get().build();
        }
    }

    @Nested
    public Provider<ExternalNativeJsonGenerator> getExternalNativeJsonGenerator() {
        return generator;
    }

    @NonNull
    public static VariantTaskCreationAction<ExternalNativeBuildJsonTask> createTaskConfigAction(
            @NonNull final Provider<ExternalNativeJsonGenerator> generator,
            @NonNull final VariantScope scope) {
        return new CreationAction(scope, generator);
    }

    private static class CreationAction
            extends VariantTaskCreationAction<ExternalNativeBuildJsonTask> {

        private final Provider<ExternalNativeJsonGenerator> generator;

        private CreationAction(
                VariantScope scope, Provider<ExternalNativeJsonGenerator> generator) {
            super(scope);
            this.generator = generator;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("generateJsonModel");
        }

        @NonNull
        @Override
        public Class<ExternalNativeBuildJsonTask> getType() {
            return ExternalNativeBuildJsonTask.class;
        }

        @Override
        public void configure(@NonNull ExternalNativeBuildJsonTask task) {
            super.configure(task);

            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            task.generator = generator;
            task.evalIssueReporter = getVariantScope().getGlobalScope().getErrorHandler();
            GradleVariantConfiguration config = getVariantScope().getVariantConfiguration();

            // Task should always run.
            task.getOutputs().upToDateWhen(task1 -> false);

            if (artifacts.hasFinalProduct(
                            InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR.INSTANCE)
                    && config.getRenderscriptNdkModeEnabled()) {
                artifacts.setTaskInputToFinalProduct(
                        InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR.INSTANCE,
                        task.getRenderscriptSources());
            }
        }
    }
}
