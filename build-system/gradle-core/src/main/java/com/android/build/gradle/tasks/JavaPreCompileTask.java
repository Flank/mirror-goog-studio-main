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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;


/**
 * Tasks to perform necessary action before a JavaCompile.
 */
public class JavaPreCompileTask extends BaseTask {

    private static final String PROCESSOR_SERVICES =
            "META-INF/services/javax.annotation.processing.Processor";

    private File annotationProcessorOutputFolder;

    private Configuration annotationProcessorConfiguration;

    private Supplier<Collection<File>> compileClasspaths;

    private CoreAnnotationProcessorOptions annotationProcessorOptions;

    @Input
    public File getAnnotationProcessorOutputFolder() {
        return annotationProcessorOutputFolder;
    }

    @InputFiles
    public Configuration getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration;
    }

    @TaskAction
    public void preCompile() {
        // Create directory for output of annotation processor.
        FileUtils.mkdirs(annotationProcessorOutputFolder);

        // Resolve configuration.
        ResolvedConfiguration resolvedConfiguration =
                annotationProcessorConfiguration.getResolvedConfiguration();
        if (resolvedConfiguration.hasError()) {
            resolvedConfiguration.rethrowFailure();
        }
        Collection<File> processorPath =
                annotationProcessorConfiguration
                        .getFiles()
                        .stream()
                        .filter(file -> !file.getPath().endsWith(SdkConstants.DOT_AAR))
                        .collect(Collectors.toSet());

        if (annotationProcessorOptions.getIncludeCompileClasspath() == null
                && !getProject().getPlugins().hasPlugin("com.neenbedankt.android-apt")) {
            List<String> processors = Lists.newArrayList();
            for (File file : compileClasspaths.get()) {
                try (FileSystem fs = FileSystems.newFileSystem(file.toPath(), null)) {
                    if (Files.exists(fs.getPath(PROCESSOR_SERVICES))
                            && !processorPath.contains(file)) {
                        processors.add(file.getName());
                    }
                } catch (IOException ignore) {
                }
                if (!processors.isEmpty()) {
                    throw new RuntimeException(
                            "Annotation processors must be explicitly declared now.  The following "
                                    + "dependencies on the compile classpath are found to contain "
                                    + "annotation processor.  Please add them to the "
                                    + "annotationProcessor configuration.\n  - "
                                    + Joiner.on("\n  - ").join(processors)
                                    + "\nAlternatively, set "
                                    + "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath = true "
                                    + "to continue with previous behavior.  Note that this option "
                                    + "is deprecated and will be removed in the future.\n"
                                    + "See "
                                    + "https://developer.android.com/r/tools/annotation-processor-error-message.html "
                                    + "for more details.");
                }
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
            task.annotationProcessorOptions =
                    scope.getVariantConfiguration()
                            .getJavaCompileOptions()
                            .getAnnotationProcessorOptions();
            task.annotationProcessorConfiguration =
                    scope.getVariantData()
                            .getVariantDependency()
                            .getAnnotationProcessorConfiguration();
            task.compileClasspaths =
                    InputFilesSupplier.from(() -> scope.getJavaClasspath().getFiles());
        }
    }
}
