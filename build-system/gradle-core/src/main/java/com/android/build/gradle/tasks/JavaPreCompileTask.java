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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.tasks.factory.AndroidJavaCompile;
import com.android.build.gradle.tasks.factory.JavaCompileConfigAction;
import com.android.builder.model.SyncIssue;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

/**
 * Tasks to perform necessary action before a JavaCompile.
 */
public class JavaPreCompileTask extends BaseTask {

    @Input
    private File annotationProcessorOutputFolder;

    @InputFiles
    private Configuration annotationProcessorConfiguration;

    private CoreAnnotationProcessorOptions annotationProcessorOptions;

    private String javacTaskName;

    @TaskAction
    public void preCompile() {
        // Create directory for output of annotation processor.
        FileUtils.mkdirs(annotationProcessorOutputFolder);

        Preconditions.checkNotNull(annotationProcessorOptions.getIncludeCompileClasspath());

        // Resolve configuration.
        ResolvedConfiguration resolvedConfiguration =
                annotationProcessorConfiguration.getResolvedConfiguration();
        if (resolvedConfiguration.hasError()) {
            resolvedConfiguration.rethrowFailure();
        }
        Collection<File> processorPath = annotationProcessorConfiguration.getFiles();

        AndroidJavaCompile javacTask =
                (AndroidJavaCompile) getProject().getTasks().getByName(javacTaskName);

        if (!processorPath.isEmpty()) {
            if (Boolean.TRUE.equals(annotationProcessorOptions.getIncludeCompileClasspath())) {
                processorPath.addAll(javacTask.getClasspath().getFiles());
            }
            javacTask.getOptions().getCompilerArgs().add("-processorpath");
            javacTask.getOptions().getCompilerArgs().add(FileUtils.joinFilePaths(processorPath));
        }
        if (!annotationProcessorOptions.getClassNames().isEmpty()) {
            javacTask.getOptions().getCompilerArgs().add("-processor");
            javacTask.getOptions().getCompilerArgs().add(
                    Joiner.on(',').join(annotationProcessorOptions.getClassNames()));
        }
        if ((!processorPath.isEmpty() || !annotationProcessorOptions.getClassNames().isEmpty())
                && getProject().getPlugins().hasPlugin("com.neenbedankt.android-apt")) {
            throw new RuntimeException(
                    // Error if using android-apt plugin, as it overwrites the annotation processor
                    // opts
                    "Using incompatible plugins for the annotation processing: "
                            + "android-apt. This may result in an unexpected behavior.");
        }
        if (!annotationProcessorOptions.getArguments().isEmpty()) {
            for (Map.Entry<String, String> arg :
                    annotationProcessorOptions.getArguments().entrySet()) {
                javacTask.getOptions().getCompilerArgs().add(
                        "-A" + arg.getKey() + "=" + arg.getValue());
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<JavaPreCompileTask> {

        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("javaPreCompile");
        }

        @NonNull
        @Override
        public Class<JavaPreCompileTask> getType() {
            return JavaPreCompileTask.class;
        }

        @Override
        public void execute(@NonNull JavaPreCompileTask task) {
            task.setVariantName(scope.getFullVariantName());
            task.annotationProcessorOutputFolder = scope.getAnnotationProcessorOutputDir();
            task.javacTaskName = new JavaCompileConfigAction(scope).getName();
            task.annotationProcessorOptions =
                    scope.getVariantConfiguration().getJavaCompileOptions()
                            .getAnnotationProcessorOptions();
            task.annotationProcessorConfiguration =
                    scope.getVariantData().getVariantDependency()
                            .getAnnotationProcessorConfiguration();

            Project project = scope.getGlobalScope().getProject();
            if ((!task.annotationProcessorConfiguration.getAllDependencies().isEmpty()
                    || !task.annotationProcessorOptions.getClassNames().isEmpty())
                    && project.getPlugins().hasPlugin("com.neenbedankt.android-apt")) {
                // warn user if using android-apt plugin, as it overwrites the annotation processor
                // opts
                scope.getGlobalScope().getAndroidBuilder().getErrorReporter().handleSyncWarning(
                        null,
                        SyncIssue.TYPE_GENERIC,
                        "Using incompatible plugins for the annotation processing: "
                                + "android-apt. This may result in an unexpected behavior.");

            }
        }
    }
}
