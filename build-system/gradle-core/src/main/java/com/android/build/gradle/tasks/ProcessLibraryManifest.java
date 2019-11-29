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
import com.android.build.api.variant.VariantProperties;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.tasks.manifest.ManifestHelperKt;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.tools.ant.BuildException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
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
public abstract class ProcessLibraryManifest extends ManifestProcessorTask {

    private final RegularFileProperty manifestOutputFile;
    private final Property<String> packageOverride;
    private final Property<Integer> versionCode;
    private final Property<String> versionName;
    private final ListProperty<File> manifestOverlays;
    private final MapProperty<String, Object> manifestPlaceholders;
    @VisibleForTesting final Property<ApkData> mainSplit;

    private boolean isNamespaced;

    @Inject
    public ProcessLibraryManifest(ObjectFactory objectFactory) {
        super(objectFactory);
        manifestOutputFile = objectFactory.fileProperty();
        packageOverride = objectFactory.property(String.class);
        versionCode = objectFactory.property(Integer.class);
        versionName = objectFactory.property(String.class);
        manifestOverlays = objectFactory.listProperty(File.class);
        manifestPlaceholders = objectFactory.mapProperty(String.class, Object.class);
        mainSplit = objectFactory.property(ApkData.class);
    }

    @OutputFile
    @NonNull
    public RegularFileProperty getManifestOutputFile() {
        return manifestOutputFile;
    }

    @Override
    protected void doFullTaskAction() {
        try (WorkerExecutorFacade workers = getWorkerFacadeWithWorkers()) {
            DirectoryProperty manifestOutputDirectory = getManifestOutputDirectory();
            DirectoryProperty aaptFriendlyManifestOutputDirectory =
                    getAaptFriendlyManifestOutputDirectory();

            workers.submit(
                    ProcessLibRunnable.class,
                    new ProcessLibParams(
                            getAaptFriendlyManifestOutputFile(),
                            isNamespaced,
                            getMainManifest().get(),
                            manifestOverlays.get(),
                            packageOverride.getOrNull(),
                            versionCode.get(),
                            versionName.getOrNull(),
                            getMinSdkVersion().getOrNull(),
                            getTargetSdkVersion().getOrNull(),
                            getMaxSdkVersion().getOrNull(),
                            manifestOutputFile.get().getAsFile(),
                            manifestPlaceholders.get(),
                            getReportFile().get().getAsFile(),
                            getMergeBlameFile().get().getAsFile(),
                            manifestOutputDirectory.isPresent()
                                    ? manifestOutputDirectory.get().getAsFile()
                                    : null,
                            aaptFriendlyManifestOutputDirectory.isPresent()
                                    ? aaptFriendlyManifestOutputDirectory.get().getAsFile()
                                    : null,
                            mainSplit.get()));
        }
    }

    private static class ProcessLibParams implements Serializable {
        @Nullable private final File aaptFriendlyManifestOutputFile;
        private final boolean isNamespaced;
        @NonNull private final File mainManifest;
        @NonNull private final List<File> manifestOverlays;
        @Nullable private final String packageOverride;
        private final int versionCode;
        @Nullable private final String versionName;
        @Nullable private final String minSdkVersion;
        @Nullable private final String targetSdkVersion;
        @Nullable private final Integer maxSdkVersion;
        @NonNull private final File manifestOutputFile;
        @NonNull private final Map<String, Object> manifestPlaceholders;
        @NonNull private final File reportFile;
        @NonNull private final File mergeBlameFile;
        @Nullable private final File manifestOutputDirectory;
        @Nullable private final File aaptFriendlyManifestOutputDirectory;
        @NonNull private final ApkData mainSplit;

        private ProcessLibParams(
                @Nullable File aaptFriendlyManifestOutputFile,
                boolean isNamespaced,
                @NonNull File mainManifest,
                @NonNull List<File> manifestOverlays,
                @Nullable String packageOverride,
                int versionCode,
                @Nullable String versionName,
                @Nullable String minSdkVersion,
                @Nullable String targetSdkVersion,
                @Nullable Integer maxSdkVersion,
                @NonNull File manifestOutputFile,
                @NonNull Map<String, Object> manifestPlaceholders,
                @NonNull File reportFile,
                @NonNull File mergeBlameFile,
                @Nullable File manifestOutputDirectory,
                @Nullable File aaptFriendlyManifestOutputDirectory,
                @NonNull ApkData mainSplit) {
            this.aaptFriendlyManifestOutputFile = aaptFriendlyManifestOutputFile;
            this.isNamespaced = isNamespaced;
            this.mainManifest = mainManifest;
            this.manifestOverlays = manifestOverlays;
            this.packageOverride = packageOverride;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.minSdkVersion = minSdkVersion;
            this.targetSdkVersion = targetSdkVersion;
            this.maxSdkVersion = maxSdkVersion;
            this.manifestOutputFile = manifestOutputFile;
            this.manifestPlaceholders = manifestPlaceholders;
            this.reportFile = reportFile;
            this.mergeBlameFile = mergeBlameFile;
            this.manifestOutputDirectory = manifestOutputDirectory;
            this.aaptFriendlyManifestOutputDirectory = aaptFriendlyManifestOutputDirectory;
            this.mainSplit = mainSplit;
        }
    }

    public static class ProcessLibRunnable implements Runnable {
        @NonNull private final ProcessLibParams params;

        @Inject
        public ProcessLibRunnable(@NonNull ProcessLibParams params) {
            this.params = params;
        }

        @Override
        public void run() {
            Collection<ManifestMerger2.Invoker.Feature> optionalFeatures =
                    params.isNamespaced
                            ? Collections.singletonList(
                                    ManifestMerger2.Invoker.Feature.FULLY_NAMESPACE_LOCAL_RESOURCES)
                            : Collections.emptyList();
            MergingReport mergingReport =
                    ManifestHelperKt.mergeManifestsForApplication(
                            params.mainManifest,
                            params.manifestOverlays,
                            Collections.emptyList(),
                            Collections.emptyList(),
                            null,
                            params.packageOverride,
                            params.versionCode,
                            params.versionName,
                            params.minSdkVersion,
                            params.targetSdkVersion,
                            params.maxSdkVersion,
                            params.manifestOutputFile.getAbsolutePath(),
                            params.aaptFriendlyManifestOutputFile != null
                                    ? params.aaptFriendlyManifestOutputFile.getAbsolutePath()
                                    : null,
                            null /* outInstantRunManifestLocation */,
                            null, /*outMetadataFeatureManifestLocation */
                            null /* outInstantAppManifestLocation */,
                            ManifestMerger2.MergeType.LIBRARY,
                            params.manifestPlaceholders,
                            optionalFeatures,
                            /* dependencyFeatureNames= */ Collections.emptyList(),
                            params.reportFile,
                            LoggerWrapper.getLogger(ProcessLibraryManifest.class));

            XmlDocument mergedXmlDocument =
                    mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

            try {
                outputMergeBlameContents(mergingReport, params.mergeBlameFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            ImmutableMap<String, String> properties =
                    mergedXmlDocument != null
                            ? ImmutableMap.of(
                                    "packageId", mergedXmlDocument.getPackageName(),
                                    "split", mergedXmlDocument.getSplitName())
                            : ImmutableMap.of();

            try {
                if (params.manifestOutputDirectory != null) {
                    new BuildOutput(
                                    InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                                    params.mainSplit,
                                    params.manifestOutputFile,
                                    properties)
                            .save(params.manifestOutputDirectory);
                }

                if (params.aaptFriendlyManifestOutputDirectory != null) {
                    new BuildOutput(
                                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS.INSTANCE,
                                    params.mainSplit,
                                    params.aaptFriendlyManifestOutputFile,
                                    properties)
                            .save(params.aaptFriendlyManifestOutputDirectory);
                }
            } catch (IOException e) {
                throw new BuildException("Exception while saving build metadata : ", e);
            }

        }
    }

    @Nullable
    @Override
    @Internal
    public File getAaptFriendlyManifestOutputFile() {
        return getAaptFriendlyManifestOutputDirectory().isPresent()
                ? FileUtils.join(
                        getAaptFriendlyManifestOutputDirectory().get().getAsFile(),
                        mainSplit.get().getDirName(),
                        SdkConstants.ANDROID_MANIFEST_XML)
                : null;
    }

    @Input
    @Optional
    public abstract Property<String> getMinSdkVersion();

    @Input
    @Optional
    public abstract Property<String> getTargetSdkVersion();

    @Input
    @Optional
    public abstract Property<Integer> getMaxSdkVersion();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Property<File> getMainManifest();

    @Input
    @Optional
    public Property<String> getPackageOverride() {
        return packageOverride;
    }

    @Input
    public Property<Integer> getVersionCode() {
        return versionCode;
    }

    @Input
    @Optional
    public Property<String> getVersionName() {
        return versionName;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ListProperty<File> getManifestOverlays() {
        return manifestOverlays;
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     *
     * <p>This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input
    @Optional
    public MapProperty<String, Object> getManifestPlaceholders() {
        return manifestPlaceholders;
    }

    @Input
    public String getMainSplitFullName() {
        // This information is written to the build output's metadata file, so it needs to be
        // annotated as @Input
        return mainSplit.get().getFullName();
    }

    public static class CreationAction extends VariantTaskCreationAction<ProcessLibraryManifest> {

        private final VariantProperties variantProperties;
        /**
         * {@code EagerTaskCreationAction} for the library process manifest task.
         *
         * @param scope The library variant scope.
         */
        public CreationAction(
                @NonNull VariantProperties variantProperties, @NonNull VariantScope scope) {
            super(scope);
            this.variantProperties = variantProperties;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<ProcessLibraryManifest> getType() {
            return ProcessLibraryManifest.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ProcessLibraryManifest> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setProcessManifestTask(taskProvider);

            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            artifacts.producesDir(
                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS.INSTANCE,
                    taskProvider,
                    ManifestProcessorTask::getAaptFriendlyManifestOutputDirectory,
                    "aapt");

            artifacts.producesDir(
                    InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                    taskProvider,
                    ManifestProcessorTask::getManifestOutputDirectory,
                    "");

            artifacts.producesFile(
                    InternalArtifactType.LIBRARY_MANIFEST.INSTANCE,
                    taskProvider,
                    ProcessLibraryManifest::getManifestOutputFile,
                    SdkConstants.ANDROID_MANIFEST_XML);

            artifacts.producesFile(
                    InternalArtifactType.MANIFEST_MERGE_BLAME_FILE.INSTANCE,
                    taskProvider,
                    ProcessLibraryManifest::getMergeBlameFile,
                    "manifest-merger-blame-"
                            + getVariantScope().getVariantConfiguration().getBaseName()
                            + "-report.txt");

            artifacts.producesFile(
                    InternalArtifactType.MANIFEST_MERGE_REPORT.INSTANCE,
                    taskProvider,
                    ProcessLibraryManifest::getReportFile,
                    FileUtils.join(getVariantScope().getGlobalScope().getOutputsDir(), "logs")
                            .getAbsolutePath(),
                    "manifest-merger-"
                            + getVariantScope().getVariantConfiguration().getBaseName()
                            + "-report.txt");
        }

        @Override
        public void configure(@NonNull ProcessLibraryManifest task) {
            super.configure(task);

            GradleVariantConfiguration config = getVariantScope().getVariantConfiguration();

            final ProductFlavor mergedFlavor = config.getMergedFlavor();

            Project project = getVariantScope().getGlobalScope().getProject();
            task.getMinSdkVersion()
                    .set(
                            project.provider(
                                    () -> {
                                        ApiVersion minSdkVersion1 = mergedFlavor.getMinSdkVersion();
                                        if (minSdkVersion1 == null) {
                                            return null;
                                        }
                                        return minSdkVersion1.getApiString();
                                    }));
            task.getMinSdkVersion().disallowChanges();

            task.getTargetSdkVersion()
                    .set(
                            project.provider(
                                    () -> {
                                        ApiVersion targetSdkVersion =
                                                mergedFlavor.getTargetSdkVersion();
                                        if (targetSdkVersion == null) {
                                            return null;
                                        }
                                        return targetSdkVersion.getApiString();
                                    }));
            task.getTargetSdkVersion().disallowChanges();

            task.getMaxSdkVersion().set(project.provider(mergedFlavor::getMaxSdkVersion));
            task.getMaxSdkVersion().disallowChanges();

            task.mainSplit.set(project.provider(getVariantScope().getOutputScope()::getMainSplit));
            task.mainSplit.disallowChanges();

            task.isNamespaced =
                    getVariantScope()
                            .getGlobalScope()
                            .getExtension()
                            .getAaptOptions()
                            .getNamespaced();
            task.versionName.set(task.getProject().provider(config::getVersionName));
            task.versionName.disallowChanges();
            task.versionCode.set(task.getProject().provider(config::getVersionCode));
            task.versionCode.disallowChanges();
            task.packageOverride.set(variantProperties.getApplicationId());
            task.packageOverride.disallowChanges();
            task.manifestPlaceholders.set(
                    task.getProject().provider(config::getManifestPlaceholders));
            task.manifestPlaceholders.disallowChanges();
            task.getMainManifest().set(project.provider(config::getMainManifestFilePath));
            task.getMainManifest().disallowChanges();
            task.manifestOverlays.set(task.getProject().provider(config::getManifestOverlays));
            task.manifestOverlays.disallowChanges();
        }
    }
}
