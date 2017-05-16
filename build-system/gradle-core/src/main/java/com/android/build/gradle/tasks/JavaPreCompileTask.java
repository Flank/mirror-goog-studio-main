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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.tasks.factory.AbstractCompilesUtil;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Tasks to perform necessary action before a JavaCompile. */
@CacheableTask
public class JavaPreCompileTask extends BaseTask {

    @VisibleForTesting static final String DATA_BINDING_SPEC = "android.databinding.DataBinding";

    private static final String PROCESSOR_SERVICES =
            "META-INF/services/javax.annotation.processing.Processor";

    private File processorListFile;

    private String annotationProcessorConfigurationName;

    private FileCollection annotationProcessorConfiguration;

    private FileCollection compileClasspaths;

    private AnnotationProcessorOptions annotationProcessorOptions;

    private boolean dataBindingEnabled;

    @VisibleForTesting
    void init(
            @NonNull File processorListFile,
            @NonNull String annotationProcessorConfigurationName,
            @NonNull FileCollection annotationProcessorConfiguration,
            @NonNull FileCollection compileClasspaths,
            @NonNull AnnotationProcessorOptions annotationProcessorOptions,
            boolean dataBindingEnabled) {
        this.processorListFile = processorListFile;
        this.annotationProcessorConfigurationName = annotationProcessorConfigurationName;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.compileClasspaths = compileClasspaths;
        this.annotationProcessorOptions = annotationProcessorOptions;
        this.dataBindingEnabled = dataBindingEnabled;
    }

    @OutputFile
    public File getProcessorListFile() {
        return processorListFile;
    }

    @Classpath
    public FileCollection getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration;
    }

    @Classpath
    public FileCollection getCompileClasspaths() {
        return compileClasspaths;
    }

    @TaskAction
    public void preCompile() throws IOException {
        boolean grandfathered =
                annotationProcessorOptions.getIncludeCompileClasspath() != null
                        || hasOldAptPlugin();
        Collection<File> compileProcessors = null;
        if (!grandfathered) {
            compileProcessors = collectAnnotationProcessors(compileClasspaths);
            compileProcessors.removeAll(annotationProcessorConfiguration.getFiles());
            if (!compileProcessors.isEmpty()) {
                throwException(convertFilesToNames(compileProcessors));
            }
        }

        // Get all the annotation processors for metrics collection.
        Set<String> classNames = Sets.newHashSet();

        // Add the annotation processors on classpath only when includeCompileClasspath is true.
        if (Boolean.TRUE.equals(annotationProcessorOptions.getIncludeCompileClasspath())) {
            if (compileProcessors == null) {
                compileProcessors = collectAnnotationProcessors(compileClasspaths);
            }
            classNames.addAll(convertFilesToNames(compileProcessors));
        }

        // Add all annotation processors on the annotation processor configuration.
        classNames.addAll(
                convertFilesToNames(collectAnnotationProcessors(annotationProcessorConfiguration)));

        // Add the explicitly declared processors.
        // For metrics purposes, we don't care how they include the processor in their build.
        classNames.addAll(annotationProcessorOptions.getClassNames());

        // Add a generic reference to data binding, if present.
        if (dataBindingEnabled) {
            classNames.add(DATA_BINDING_SPEC);
        }

        FileUtils.deleteIfExists(processorListFile);
        Gson gson = new GsonBuilder().create();
        try (FileWriter writer = new FileWriter(processorListFile)) {
            gson.toJson(classNames, writer);
        }
    }

    /**
     * Returns a List of packages in the configuration believed to contain an annotation processor.
     *
     * <p>We assume a package has an annotation processor if it contains the
     * META-INF/services/javax.annotation.processing.Processor file.
     */
    private static List<File> collectAnnotationProcessors(FileCollection configuration)
            throws IOException {
        List<File> processors = Lists.newArrayList();
        for (File file : configuration.getFiles()) {
            if (!file.exists()) {
                continue;
            }
            if (file.isDirectory()) {
                if (new File(file, PROCESSOR_SERVICES).exists()) {
                    processors.add(file);
                }
            } else {
                try (JarFile jarFile = new JarFile(file)) {
                    JarEntry entry = jarFile.getJarEntry(PROCESSOR_SERVICES);
                    //noinspection VariableNotUsedInsideIf
                    if (entry != null) {
                        processors.add(file);
                    }
                } catch (IOException iox) {
                    // Can happen when we encounter a folder instead of a jar; for instance, in sub-modules.
                    // We're just displaying a warning, so there's no need to stop the build here.
                }
            }
        }
        return processors;
    }

    private static List<String> convertFilesToNames(Collection<File> files) {
        return files.stream().map(File::getName).collect(Collectors.toList());
    }

    private boolean hasOldAptPlugin() {
        return getProject().getPlugins().hasPlugin(AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME);
    }

    private void throwException(List<String> processors) throws RuntimeException {
        throw new RuntimeException(
                "Annotation processors must be explicitly declared now.  The following "
                        + "dependencies on the compile classpath are found to contain "
                        + "annotation processor.  Please add them to the "
                        + annotationProcessorConfigurationName
                        + " configuration.\n  - "
                        + Joiner.on("\n  - ").join(processors)
                        + "\nAlternatively, set "
                        + "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath = true "
                        + "to continue with previous behavior.  Note that this option "
                        + "is deprecated and will be removed in the future.\n"
                        + "See "
                        + "https://developer.android.com/r/tools/annotation-processor-error-message.html "
                        + "for more details.");
    }

    public static class ConfigAction implements TaskConfigAction<JavaPreCompileTask> {

        private final VariantScope scope;
        private final File processorListFile;

        public ConfigAction(VariantScope scope, File processorListFile) {
            this.scope = scope;
            this.processorListFile = processorListFile;
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
            task.init(
                    processorListFile,
                    scope.getVariantData().getType().isForTesting()
                            ? scope.getVariantData().getType().getPrefix() + "AnnotationProcessor"
                            : "annotationProcessor",
                    scope.getArtifactFileCollection(ANNOTATION_PROCESSOR, ALL, JAR),
                    scope.getJavaClasspath(COMPILE_CLASSPATH, CLASSES),
                    scope.getVariantConfiguration()
                            .getJavaCompileOptions()
                            .getAnnotationProcessorOptions(),
                    false);
            task.setVariantName(scope.getFullVariantName());
        }
    }
}
