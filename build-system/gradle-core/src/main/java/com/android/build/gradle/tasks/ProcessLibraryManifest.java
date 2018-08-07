/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tools.ant.BuildException;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

/** a Task that only merge a single manifest with its overlays. */
@CacheableTask
public class ProcessLibraryManifest extends ManifestProcessorTask {

    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<Integer> maxSdkVersion;

    private VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            variantConfiguration;
    private OutputScope outputScope;

    private Provider<RegularFile> manifestOutputFile;

    @OutputFile
    public Provider<RegularFile> getManifestOutputFile() {
        return manifestOutputFile;
    }

    @Override
    protected void doFullTaskAction() {
        File aaptFriendlyManifestOutputFile = getAaptFriendlyManifestOutputFile();
        MergingReport mergingReport =
                getBuilder()
                        .mergeManifestsForApplication(
                                getMainManifest(),
                                getManifestOverlays(),
                                Collections.emptyList(),
                                getNavigationFiles(),
                                null,
                                getPackageOverride(),
                                getVersionCode(),
                                getVersionName(),
                                getMinSdkVersion(),
                                getTargetSdkVersion(),
                                getMaxSdkVersion(),
                                manifestOutputFile.get().getAsFile().getAbsolutePath(),
                                aaptFriendlyManifestOutputFile.getAbsolutePath(),
                                null /* outInstantRunManifestLocation */,
                                ManifestMerger2.MergeType.LIBRARY,
                                variantConfiguration.getManifestPlaceholders(),
                                Collections.emptyList(),
                                getReportFile());

        XmlDocument mergedXmlDocument =
                mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

        ImmutableMap<String, String> properties =
                mergedXmlDocument != null
                        ? ImmutableMap.of(
                                "packageId", mergedXmlDocument.getPackageName(),
                                "split", mergedXmlDocument.getSplitName())
                        : ImmutableMap.of();

        try {
            new BuildOutput(
                            InternalArtifactType.MERGED_MANIFESTS,
                            outputScope.getMainSplit(),
                            manifestOutputFile.get().getAsFile(),
                            properties)
                    .save(getManifestOutputDirectory().get().getAsFile());

            new BuildOutput(
                            InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                            outputScope.getMainSplit(),
                            aaptFriendlyManifestOutputFile,
                            properties)
                    .save(getAaptFriendlyManifestOutputDirectory());
        } catch (IOException e) {
            throw new BuildException("Exception while saving build metadata : ", e);
        }
    }

    @Nullable
    @Override
    @Internal
    public File getAaptFriendlyManifestOutputFile() {
        Preconditions.checkNotNull(outputScope.getMainSplit());
        return FileUtils.join(
                getAaptFriendlyManifestOutputDirectory(),
                outputScope.getMainSplit().getDirName(),
                SdkConstants.ANDROID_MANIFEST_XML);
    }

    @Input
    @Optional
    public String getMinSdkVersion() {
        return minSdkVersion.get();
    }

    @Input
    @Optional
    public String getTargetSdkVersion() {
        return targetSdkVersion.get();
    }

    @Input
    @Optional
    public Integer getMaxSdkVersion() {
        return maxSdkVersion.get();
    }

    @Internal
    public VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public File getMainManifest() {
        return variantConfiguration.getMainManifest();
    }

    @Input
    @Optional
    public String getPackageOverride() {
        return variantConfiguration.getApplicationId();
    }

    @Input
    public int getVersionCode() {
        return variantConfiguration.getVersionCode();
    }

    @Input
    @Optional
    public String getVersionName() {
        return variantConfiguration.getVersionName();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getNavigationFiles() {
        return variantConfiguration.getNavigationFiles();
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     *
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input
    @Optional
    public String getManifestPlaceholders() {
        return serializeMap(variantConfiguration.getManifestPlaceholders());
    }

    @Input
    public String getMainSplitFullName() {
        // This information is written to the build output's metadata file, so it needs to be
        // annotated as @Input
        return outputScope.getMainSplit().getFullName();
    }

    public static class CreationAction
            extends AnnotationProcessingTaskCreationAction<ProcessLibraryManifest> {

        Provider<RegularFile> manifestOutputFile;
        File aaptFriendlyManifestOutputDirectory;
        VariantScope scope;
        private File reportFile;

        /**
         * {@code EagerTaskCreationAction} for the library process manifest task.
         *
         * @param scope The library variant scope.
         */
        public CreationAction(@NonNull VariantScope scope) {
            super(scope, scope.getTaskName("process", "Manifest"), ProcessLibraryManifest.class);
            this.scope = scope;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            manifestOutputFile =
                    scope.getArtifacts()
                            .appendArtifactFile(
                                    InternalArtifactType.LIBRARY_MANIFEST,
                                    taskName,
                                    SdkConstants.ANDROID_MANIFEST_XML);

            aaptFriendlyManifestOutputDirectory =
                    getScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                                    taskName,
                                    "aapt");

            reportFile =
                    FileUtils.join(
                            getScope().getGlobalScope().getOutputsDir(),
                            "logs",
                            "manifest-merger-"
                                    + getScope().getVariantConfiguration().getBaseName()
                                    + "-report.txt");

            getScope()
                    .getArtifacts()
                    .appendArtifact(
                            InternalArtifactType.MANIFEST_MERGE_REPORT,
                            ImmutableList.of(reportFile),
                            taskName);
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ProcessLibraryManifest> taskProvider) {
            super.handleProvider(taskProvider);
            getScope().getTaskContainer().setProcessManifestTask(taskProvider);
        }

        @Override
        public void configure(@NonNull ProcessLibraryManifest task) {
            super.configure(task);
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    getScope().getVariantConfiguration();
            final AndroidBuilder androidBuilder = getScope().getGlobalScope().getAndroidBuilder();

            task.setAndroidBuilder(androidBuilder);
            task.setVariantName(config.getFullName());

            task.variantConfiguration = config;

            final ProductFlavor mergedFlavor = config.getMergedFlavor();

            task.minSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion minSdkVersion1 = mergedFlavor.getMinSdkVersion();
                                if (minSdkVersion1 == null) {
                                    return null;
                                }
                                return minSdkVersion1.getApiString();
                            });

            task.targetSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion targetSdkVersion = mergedFlavor.getTargetSdkVersion();
                                if (targetSdkVersion == null) {
                                    return null;
                                }
                                return targetSdkVersion.getApiString();
                            });

            task.maxSdkVersion = TaskInputHelper.memoize(mergedFlavor::getMaxSdkVersion);

            task.setAaptFriendlyManifestOutputDirectory(aaptFriendlyManifestOutputDirectory);

            task.manifestOutputFile = manifestOutputFile;

            task.outputScope = getScope().getOutputScope();

            task.setReportFile(reportFile);

            if (scope.getTaskContainer().getCheckManifestTask() != null) {
                task.dependsOn(scope.getTaskContainer().getCheckManifestTask());
            }
        }
    }
}
