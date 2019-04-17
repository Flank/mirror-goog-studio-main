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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROCESSED_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.workers.WorkerExecutor;

/** Tasks to perform necessary action before a JavaCompile. */
@CacheableTask
public class JavaPreCompileTask extends AndroidVariantTask {

    @NonNull private RegularFileProperty processorListFile;

    private String annotationProcessorConfigurationName;

    private ArtifactCollection annotationProcessorConfiguration;

    private ArtifactCollection compileClasspaths;

    private AnnotationProcessorOptions annotationProcessorOptions;

    private boolean isTestComponent;

    private final WorkerExecutorFacade workers;

    @Inject
    public JavaPreCompileTask(WorkerExecutor workerExecutor, ObjectFactory objectFactory) {
        workers = Workers.INSTANCE.preferWorkers(getProject().getName(), getPath(), workerExecutor);
        processorListFile = objectFactory.fileProperty();
    }

    @VisibleForTesting
    void init(
            @NonNull String annotationProcessorConfigurationName,
            @NonNull ArtifactCollection annotationProcessorConfiguration,
            @NonNull ArtifactCollection compileClasspaths,
            @NonNull AnnotationProcessorOptions annotationProcessorOptions,
            boolean isTestComponent) {
        this.annotationProcessorConfigurationName = annotationProcessorConfigurationName;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.compileClasspaths = compileClasspaths;
        this.annotationProcessorOptions = annotationProcessorOptions;
        this.isTestComponent = isTestComponent;
    }

    @NonNull
    @OutputFile
    public RegularFileProperty getProcessorListFile() {
        return processorListFile;
    }

    @Classpath
    public FileCollection getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration.getArtifactFiles();
    }

    @Classpath
    public FileCollection getCompileClasspaths() {
        return compileClasspaths.getArtifactFiles();
    }

    @TaskAction
    public void preCompile() {
        try (WorkerExecutorFacade workerExecutor = this.workers) {
            workerExecutor.submit(
                    PreCompileRunnable.class,
                    new PreCompileParams(
                            processorListFile.get().getAsFile(),
                            annotationProcessorConfigurationName,
                            toSerializable(annotationProcessorConfiguration),
                            toSerializable(compileClasspaths),
                            isTestComponent,
                            annotationProcessorOptions.getClassNames(),
                            annotationProcessorOptions.getIncludeCompileClasspath()));
        }
    }

    @NonNull
    private static Collection<SerializableArtifact> toSerializable(
            @NonNull ArtifactCollection artifactCollection) {
        return artifactCollection
                .getArtifacts()
                .stream()
                .map(SerializableArtifact::new)
                .collect(ImmutableList.toImmutableList());
    }

    static class PreCompileParams implements Serializable {
        @NonNull private final File processorListFile;
        @NonNull private final String annotationProcessorConfigurationName;
        @NonNull private final Collection<SerializableArtifact> annotationProcessorConfiguration;
        @NonNull private final Collection<SerializableArtifact> compileClasspaths;
        private final boolean isTestComponent;
        @NonNull private final List<String> apOptionClassNames;
        @Nullable final Boolean apOptionIncludeCompileClasspath;

        public PreCompileParams(
                @NonNull File processorListFile,
                @NonNull String annotationProcessorConfigurationName,
                @NonNull Collection<SerializableArtifact> annotationProcessorConfiguration,
                @NonNull Collection<SerializableArtifact> compileClasspaths,
                boolean isTestComponent,
                @NonNull List<String> apOptionClassNames,
                @Nullable Boolean apOptionIncludeCompileClasspath) {
            this.processorListFile = processorListFile;
            this.annotationProcessorConfigurationName = annotationProcessorConfigurationName;
            this.annotationProcessorConfiguration = annotationProcessorConfiguration;
            this.compileClasspaths = compileClasspaths;
            this.isTestComponent = isTestComponent;
            this.apOptionClassNames = apOptionClassNames;
            this.apOptionIncludeCompileClasspath = apOptionIncludeCompileClasspath;
        }
    }

    public static class PreCompileRunnable implements Runnable {
        @NonNull private final PreCompileParams params;

        @Inject
        public PreCompileRunnable(@NonNull PreCompileParams params) {
            this.params = params;
        }

        @Override
        public void run() {
            if (params.apOptionIncludeCompileClasspath == null) {
                Set<File> processorClasspath =
                        params.annotationProcessorConfiguration
                                .stream()
                                .map(SerializableArtifact::getFile)
                                .collect(Collectors.toSet());

                // Detect processors that are on the compile classpath but not on the annotation
                // processor classpath
                Collection<SerializableArtifact> violatingProcessors =
                        JavaCompileUtils.detectAnnotationProcessors(params.compileClasspaths)
                                .keySet();
                violatingProcessors =
                        violatingProcessors
                                .stream()
                                .filter(
                                        artifact ->
                                                !processorClasspath.contains(artifact.getFile()))
                                .collect(Collectors.toList());

                if (!violatingProcessors.isEmpty()) {
                    Collection<String> violatingProcessorNames =
                            violatingProcessors
                                    .stream()
                                    .map(SerializableArtifact::getDisplayName)
                                    .collect(Collectors.toList());
                    String message =
                            "Annotation processors must be explicitly declared now.  The following "
                                    + "dependencies on the compile classpath are found to contain "
                                    + "annotation processor.  Please add them to the "
                                    + params.annotationProcessorConfigurationName
                                    + " configuration.\n  - "
                                    + Joiner.on("\n  - ").join(violatingProcessorNames)
                                    + "\nAlternatively, set "
                                    + "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath = true "
                                    + "to continue with previous behavior.  Note that this option "
                                    + "is deprecated and will be removed in the future.\n"
                                    + "See "
                                    + "https://developer.android.com/r/tools/annotation-processor-error-message.html "
                                    + "for more details.";
                    if (params.isTestComponent) {
                        Logging.getLogger(JavaPreCompileTask.class).warn(message);
                    } else {
                        throw new RuntimeException(message);
                    }
                }
            }

            Map<String, Boolean> annotationProcessors =
                    JavaCompileUtils.detectAnnotationProcessors(
                            params.apOptionIncludeCompileClasspath,
                            params.apOptionClassNames,
                            params.annotationProcessorConfiguration,
                            params.compileClasspaths);
            JavaCompileUtils.writeAnnotationProcessorsToJsonFile(
                    annotationProcessors, params.processorListFile);
        }
    }

    public static class CreationAction extends VariantTaskCreationAction<JavaPreCompileTask> {

        public CreationAction(VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("javaPreCompile");
        }

        @NonNull
        @Override
        public Class<JavaPreCompileTask> getType() {
            return JavaPreCompileTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends JavaPreCompileTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope()
                    .getArtifacts()
                    .producesFile(
                            InternalArtifactType.ANNOTATION_PROCESSOR_LIST,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            taskProvider.map(JavaPreCompileTask::getProcessorListFile),
                            "annotationProcessors.json");
        }

        @Override
        public void configure(@NonNull JavaPreCompileTask task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            task.init(
                    scope.getVariantData().getType().isTestComponent()
                            ? scope.getVariantData().getType().getPrefix() + "AnnotationProcessor"
                            : "annotationProcessor",
                    scope.getArtifactCollection(ANNOTATION_PROCESSOR, ALL, PROCESSED_JAR),
                    scope.getJavaClasspathArtifacts(COMPILE_CLASSPATH, CLASSES, null),
                    scope.getVariantConfiguration()
                            .getJavaCompileOptions()
                            .getAnnotationProcessorOptions(),
                    scope.getVariantData().getType().isTestComponent());
        }
    }
}
