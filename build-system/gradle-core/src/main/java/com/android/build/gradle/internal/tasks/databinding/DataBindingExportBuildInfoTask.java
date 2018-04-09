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

package com.android.build.gradle.internal.tasks.databinding;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.processing.Scope;
import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * This task creates a class which includes the build environment information, which is needed for
 * the annotation processor.
 */
public class DataBindingExportBuildInfoTask extends DefaultTask {

    private Supplier<LayoutXmlProcessor> xmlProcessor;

    private File sdkDir;

    private File xmlOutFolder;

    private File exportClassListTo;

    private File dataBindingClassOutput;

    private boolean useAndroidX;

    private Supplier<FileCollection> compilerClasspath;
    private Supplier<Collection<ConfigurableFileTree>> compilerSources;
    private BuildableArtifact mergedResources;

    @NonNull
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getMergedResources() {
        return mergedResources;
    }

    @TaskAction
    public void exportInfo() {
        getXmlProcessor().writeEmptyInfoClass(useAndroidX);
        Scope.assertNoError();
    }

    @Input
    public boolean isUseAndroidX() {
        return useAndroidX;
    }

    public void setUseAndroidX(boolean useAndroidX) {
        this.useAndroidX = useAndroidX;
    }

    public LayoutXmlProcessor getXmlProcessor() {
        return xmlProcessor.get();
    }

    public void setXmlProcessor(Supplier<LayoutXmlProcessor> xmlProcessor) {
        this.xmlProcessor = xmlProcessor;
    }

    @Classpath
    public FileCollection getCompilerClasspath() {
        return compilerClasspath.get();
    }

    @InputFiles
    public Iterable<ConfigurableFileTree> getCompilerSources() {
        return compilerSources.get();
    }

    @Input
    public File getSdkDir() {
        return sdkDir;
    }

    public void setSdkDir(File sdkDir) {
        this.sdkDir = sdkDir;
    }

    @InputDirectory // output of the process layouts task
    public File getXmlOutFolder() {
        return xmlOutFolder;
    }

    public void setXmlOutFolder(File xmlOutFolder) {
        this.xmlOutFolder = xmlOutFolder;
    }

    @Input
    @Optional
    public File getExportClassListTo() {
        return exportClassListTo;
    }

    public void setExportClassListTo(File exportClassListTo) {
        this.exportClassListTo = exportClassListTo;
    }

    @OutputDirectory
    public File getOutput() {
        return dataBindingClassOutput;
    }

    public void setDataBindingClassOutput(File dataBindingClassOutput) {
        this.dataBindingClassOutput = dataBindingClassOutput;
    }

    public static class ConfigAction implements TaskConfigAction<DataBindingExportBuildInfoTask> {

        @NonNull private final VariantScope variantScope;
        @NonNull private final TaskManager.MergeType mergeType;

        public ConfigAction(
                @NonNull VariantScope variantScope, @NonNull TaskManager.MergeType mergeType) {
            this.variantScope = variantScope;
            this.mergeType = mergeType;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("dataBindingExportBuildInfo");
        }

        @NonNull
        @Override
        public Class<DataBindingExportBuildInfoTask> getType() {
            return DataBindingExportBuildInfoTask.class;
        }

        @Override
        public void execute(@NonNull DataBindingExportBuildInfoTask task) {
            final BaseVariantData variantData = variantScope.getVariantData();
            task.setXmlProcessor(variantData::getLayoutXmlProcessor);
            task.setSdkDir(variantScope.getGlobalScope().getSdkHandler().getSdkFolder());
            task.setXmlOutFolder(variantScope.getLayoutInfoOutputForDataBinding());
            task.setUseAndroidX(
                    variantScope
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.USE_ANDROID_X));
            // we need the external classpath, so we don't want to use scope.getClassPath as that
            // includes internal (to the module) classpath in case there's registered bytecode
            // generator (kotlin) which can trigger a cyclic dependencies.
            task.compilerClasspath =
                    () -> variantScope.getArtifactFileCollection(COMPILE_CLASSPATH, ALL, CLASSES);

            task.compilerSources =
                    () ->
                            variantData
                                    .getJavaSources()
                                    .stream()
                                    .filter(
                                            input ->
                                                    !variantScope
                                                            .getClassOutputForDataBinding()
                                                            .equals(input.getDir()))
                                    .collect(Collectors.toList());

            task.mergedResources =
                    variantScope.getArtifacts().getFinalArtifactFiles(mergeType.getOutputType());
            task.setExportClassListTo(variantData.getType().isExportDataBindingClassList() ?
                    variantScope.getGeneratedClassListOutputFileForDataBinding() : null);
            task.setDataBindingClassOutput(variantScope.getClassOutputForDataBinding());
        }
    }
}