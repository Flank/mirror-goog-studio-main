/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_NAME;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.NAVIGATION_JSON;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.REVERSE_METADATA_BASE_MODULE_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.REVERSE_METADATA_FEATURE_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact.ExtraComponentIdentifier;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.build.gradle.internal.tasks.manifest.ManifestHelperKt;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexingType;
import com.android.builder.model.ApiVersion;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestMerger2.Invoker.Feature;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
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
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;

/** A task that processes the manifest */
@CacheableTask
public abstract class ProcessApplicationManifest extends ManifestProcessorTask {

    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<Integer> maxSdkVersion;
    private ArtifactCollection manifests;
    private ArtifactCollection featureManifests;
    private FileCollection dependencyFeatureNameArtifacts;
    private FileCollection microApkManifest;
    private FileCollection packageManifest;
    private Supplier<EnumSet<Feature>> optionalFeatures;

    private final Property<String> packageOverride;
    private final ListProperty<File> manifestOverlays;
    private final MapProperty<String, Object> manifestPlaceholders;
    private boolean isHybridVariantType;
    private boolean isFeatureSplitVariantType;
    private String buildTypeName;

    private FileCollection navigationJsons;

    // supplier to read the file above to get the feature name for the current project.
    @Nullable private Supplier<String> featureNameSupplier = null;

    @Inject
    public ProcessApplicationManifest(ObjectFactory objectFactory) {
        super(objectFactory);
        packageOverride = objectFactory.property(String.class);
        manifestOverlays = objectFactory.listProperty(File.class);
        manifestPlaceholders = objectFactory.mapProperty(String.class, Object.class);
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        // read the output of the compatible screen manifest.
        BuildElements compatibleScreenManifests =
                ExistingBuildElements.from(
                        InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST.INSTANCE,
                        getCompatibleScreensManifest().get().getAsFile());

        ModuleMetadata moduleMetadata = null;
        if (packageManifest != null && !packageManifest.isEmpty()) {
            moduleMetadata = ModuleMetadata.load(packageManifest.getSingleFile());
            boolean isDebuggable = optionalFeatures.get().contains(Feature.DEBUGGABLE);
            if (moduleMetadata.getDebuggable() != isDebuggable) {
                String moduleType = isHybridVariantType ? "Instant App Feature" : "Dynamic Feature";
                String errorMessage =
                        String.format(
                                "%1$s '%2$s' (build type '%3$s') %4$s debuggable,\n"
                                        + "and the corresponding build type in the base "
                                        + "application %5$s debuggable.\n"
                                        + "Recommendation: \n"
                                        + "   in  %6$s\n"
                                        + "   set android.buildTypes.%3$s.debuggable = %7$s",
                                moduleType,
                                getProject().getPath(),
                                buildTypeName,
                                isDebuggable ? "is" : "is not",
                                moduleMetadata.getDebuggable() ? "is" : "is not",
                                getProject().getBuildFile(),
                                moduleMetadata.getDebuggable() ? "true" : "false");
                throw new InvalidUserDataException(errorMessage);
            }
        }


        @Nullable BuildOutput compatibleScreenManifestForSplit;

        ImmutableList.Builder<BuildOutput> mergedManifestOutputs = ImmutableList.builder();
        ImmutableList.Builder<BuildOutput> metadataFeatureMergedManifestOutputs =
                ImmutableList.builder();
        ImmutableList.Builder<BuildOutput> bundleManifestOutputs = ImmutableList.builder();
        ImmutableList.Builder<BuildOutput> instantAppManifestOutputs = ImmutableList.builder();

        List<File> navJsons =
                navigationJsons == null
                        ? Collections.emptyList()
                        : Lists.newArrayList(navigationJsons);
        // FIX ME : multi threading.
        for (ApkData apkData : ExistingBuildElements.loadApkList(getApkList().get().getAsFile())) {

            compatibleScreenManifestForSplit = compatibleScreenManifests.element(apkData);
            File manifestOutputFile =
                    new File(
                            getManifestOutputDirectory().get().getAsFile(),
                            FileUtils.join(apkData.getDirName(), ANDROID_MANIFEST_XML));

            File metadataFeatureManifestOutputFile =
                    FileUtils.join(
                            getMetadataFeatureManifestOutputDirectory().get().getAsFile(),
                            apkData.getDirName(),
                            ANDROID_MANIFEST_XML);

            File bundleManifestOutputFile =
                    FileUtils.join(
                            getBundleManifestOutputDirectory().get().getAsFile(),
                            apkData.getDirName(),
                            ANDROID_MANIFEST_XML);

            File instantAppManifestOutputFile =
                    getInstantAppManifestOutputDirectory().isPresent()
                            ? FileUtils.join(
                                    getInstantAppManifestOutputDirectory().get().getAsFile(),
                                    apkData.getDirName(),
                                    ANDROID_MANIFEST_XML)
                            : null;

            MergingReport mergingReport =
                    ManifestHelperKt.mergeManifestsForApplication(
                            getMainManifest().get(),
                            manifestOverlays.get(),
                            computeFullProviderList(compatibleScreenManifestForSplit),
                            navJsons,
                            getFeatureName(),
                            moduleMetadata == null
                                    ? packageOverride.getOrNull()
                                    : moduleMetadata.getApplicationId(),
                            moduleMetadata == null
                                    ? apkData.getVersionCode()
                                    : Integer.parseInt(moduleMetadata.getVersionCode()),
                            moduleMetadata == null
                                    ? apkData.getVersionName()
                                    : moduleMetadata.getVersionName(),
                            getMinSdkVersion(),
                            getTargetSdkVersion(),
                            getMaxSdkVersion(),
                            manifestOutputFile.getAbsolutePath(),
                            // no aapt friendly merged manifest file necessary for applications.
                            null /* aaptFriendlyManifestOutputFile */,
                            metadataFeatureManifestOutputFile.getAbsolutePath(),
                            bundleManifestOutputFile.getAbsolutePath(),
                            instantAppManifestOutputFile != null
                                    ? instantAppManifestOutputFile.getAbsolutePath()
                                    : null,
                            ManifestMerger2.MergeType.APPLICATION,
                            manifestPlaceholders.get(),
                            getOptionalFeatures(),
                            getDependencyFeatureNames(),
                            getReportFile().get().getAsFile(),
                            LoggerWrapper.getLogger(ProcessApplicationManifest.class));

            XmlDocument mergedXmlDocument =
                    mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

            outputMergeBlameContents(mergingReport, getMergeBlameFile().get().getAsFile());

            ImmutableMap<String, String> properties =
                    mergedXmlDocument != null
                            ? ImmutableMap.of(
                                    "packageId",
                                    mergedXmlDocument.getPackageName(),
                                    "split",
                                    mergedXmlDocument.getSplitName(),
                                    SdkConstants.ATTR_MIN_SDK_VERSION,
                                    mergedXmlDocument.getMinSdkVersion())
                            : ImmutableMap.of();

            mergedManifestOutputs.add(
                    new BuildOutput(
                            InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                            apkData,
                            manifestOutputFile,
                            properties));

            metadataFeatureMergedManifestOutputs.add(
                    new BuildOutput(
                            InternalArtifactType.METADATA_FEATURE_MANIFEST.INSTANCE,
                            apkData,
                            metadataFeatureManifestOutputFile));
            bundleManifestOutputs.add(
                    new BuildOutput(
                            InternalArtifactType.BUNDLE_MANIFEST.INSTANCE,
                            apkData,
                            bundleManifestOutputFile,
                            properties));
            if (instantAppManifestOutputFile != null) {
                instantAppManifestOutputs.add(
                        new BuildOutput(
                                InternalArtifactType.INSTANT_APP_MANIFEST.INSTANCE,
                                apkData,
                                instantAppManifestOutputFile,
                                properties));
            }
        }
        new BuildElements(mergedManifestOutputs.build())
                .save(getManifestOutputDirectory());
        new BuildElements(metadataFeatureMergedManifestOutputs.build())
                .save(getMetadataFeatureManifestOutputDirectory());
        new BuildElements(bundleManifestOutputs.build()).save(
                getBundleManifestOutputDirectory());

        if (getInstantAppManifestOutputDirectory().isPresent()) {
            new BuildElements(instantAppManifestOutputs.build())
                    .save(getInstantAppManifestOutputDirectory());
        }
    }

    @Nullable
    @Override
    @Internal
    public File getAaptFriendlyManifestOutputFile() {
        return null;
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Property<File> getMainManifest();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ListProperty<File> getManifestOverlays() {
        return manifestOverlays;
    }

    @Input
    @Optional
    public Property<String> getPackageOverride() {
        return packageOverride;
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

    private List<ManifestProvider> computeProviders(
            @NonNull Set<ResolvedArtifactResult> artifacts) {
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size());
        for (ResolvedArtifactResult artifact : artifacts) {
            File directory = artifact.getFile();
            BuildElements splitOutputs =
                    ExistingBuildElements.from(
                            InternalArtifactType.METADATA_FEATURE_MANIFEST.INSTANCE, directory);
            if (splitOutputs.isEmpty()) {
                throw new GradleException("Could not load manifest from " + directory);
            }
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            splitOutputs.iterator().next().getOutputFile(),
                            getArtifactName(artifact)));
        }

        return providers;
    }

    /**
     * Compute the final list of providers based on the manifest file collection and the other
     * providers.
     *
     * @return the list of providers.
     */
    private List<ManifestProvider> computeFullProviderList(
            @Nullable BuildOutput compatibleScreenManifestForSplit) {
        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size() + 2);

        for (ResolvedArtifactResult artifact : artifacts) {
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            artifact.getFile(), getArtifactName(artifact)));
        }

        if (microApkManifest != null) {
            // this is now always present if embedding is enabled, but it doesn't mean
            // anything got embedded so the file may not run (the file path exists and is
            // returned by the FC but the file doesn't exist.
            File microManifest = microApkManifest.getSingleFile();
            if (microManifest.isFile()) {
                providers.add(
                        new CreationAction.ManifestProviderImpl(
                                microManifest, "Wear App sub-manifest"));
            }
        }

        if (compatibleScreenManifestForSplit != null) {
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            compatibleScreenManifestForSplit.getOutputFile(),
                            "Compatible-Screens sub-manifest"));

        }

        if (getAutoNamespacedManifests().isPresent()) {
            // We do not have resolved artifact results here, we need to find the artifact name
            // based on the file name.
            File directory = getAutoNamespacedManifests().get().getAsFile();
            Preconditions.checkState(
                    directory.isDirectory(),
                    "Auto namespaced manifests should be a directory.",
                    directory);
            for (File autoNamespacedManifest : Preconditions.checkNotNull(directory.listFiles())) {
                providers.add(
                        new CreationAction.ManifestProviderImpl(
                                autoNamespacedManifest,
                                getNameFromAutoNamespacedManifest(autoNamespacedManifest)));
            }
        }

        if (featureManifests != null) {
            providers.addAll(computeProviders(featureManifests.getArtifacts()));
        }

        return providers;
    }

    private List<String> getDependencyFeatureNames() {
        List<String> list = new ArrayList<>();

        if (!isFeatureSplitVariantType) {
            // Only feature splits can have feature dependencies
            return list;
        }

        try {
            for (File file : dependencyFeatureNameArtifacts.getFiles()) {
                list.add(org.apache.commons.io.FileUtils.readFileToString(file));
            }
            return list;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load feature declaration", e);
        }
    }

    @NonNull
    private static String getNameFromAutoNamespacedManifest(@NonNull File manifest) {
        final String manifestSuffix = "_AndroidManifest.xml";
        String fileName = manifest.getName();
        // Get the ID based on the file name generated by the [AutoNamespaceDependenciesTask]. It is
        // the sanitized name, but should be enough.
        if (!fileName.endsWith(manifestSuffix)) {
            throw new RuntimeException(
                    "Invalid auto-namespaced manifest file: " + manifest.getAbsolutePath());
        }
        return fileName.substring(0, fileName.length() - manifestSuffix.length());
    }

    // TODO put somewhere else?
    @NonNull
    public static String getArtifactName(@NonNull ResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (id instanceof ProjectComponentIdentifier) {
            return ((ProjectComponentIdentifier) id).getProjectPath();

        } else if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier mID = (ModuleComponentIdentifier) id;
            return mID.getGroup() + ":" + mID.getModule() + ":" + mID.getVersion();

        } else if (id instanceof OpaqueComponentArtifactIdentifier) {
            // this is the case for local jars.
            // FIXME: use a non internal class.
            return id.getDisplayName();
        } else if (id instanceof ExtraComponentIdentifier) {
            return id.getDisplayName();
        } else {
            throw new RuntimeException("Unsupported type of ComponentIdentifier");
        }
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

    /** Not an input, see {@link #getOptionalFeaturesString()}. */
    @Internal
    public EnumSet<Feature> getOptionalFeatures() {
        return optionalFeatures.get();
    }

    /** Synthetic input for {@link #getOptionalFeatures()} */
    @Input
    public List<String> getOptionalFeaturesString() {
        return getOptionalFeatures().stream().map(Enum::toString).collect(Collectors.toList());
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getManifests() {
        return manifests.getArtifactFiles();
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getNavigationJsons() {
        return navigationJsons;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getFeatureManifests() {
        if (featureManifests == null) {
            return null;
        }
        return featureManifests.getArtifactFiles();
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getDependencyFeatureNameArtifacts() {
        return dependencyFeatureNameArtifacts;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getMicroApkManifest() {
        return microApkManifest;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getCompatibleScreensManifest();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getPackageManifest() {
        return packageManifest;
    }

    @Input
    @Optional
    public String getFeatureName() {
        return featureNameSupplier != null ? featureNameSupplier.get() : null;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract DirectoryProperty getAutoNamespacedManifests();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getApkList();

    public static class CreationAction
            extends VariantTaskCreationAction<ProcessApplicationManifest> {

        protected final boolean isAdvancedProfilingOn;

        public CreationAction(
                @NonNull VariantScope scope,
                // TODO : remove this variable and find ways to access it from scope.
                boolean isAdvancedProfilingOn) {
            super(scope);
            this.isAdvancedProfilingOn = isAdvancedProfilingOn;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<ProcessApplicationManifest> getType() {
            return ProcessApplicationManifest.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            VariantType variantType = getVariantScope().getType();
            Preconditions.checkState(!variantType.isTestComponent());
            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();

            artifacts.republish(
                    InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                    InternalArtifactType.MANIFEST_METADATA.INSTANCE);
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ProcessApplicationManifest> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setProcessManifestTask(taskProvider);

            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            artifacts.producesDir(
                    InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    ManifestProcessorTask::getManifestOutputDirectory,
                    "");

            artifacts.producesDir(
                    InternalArtifactType.INSTANT_APP_MANIFEST.INSTANCE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    ManifestProcessorTask::getInstantAppManifestOutputDirectory,
                    "");

            artifacts.producesFile(
                    InternalArtifactType.MANIFEST_MERGE_BLAME_FILE.INSTANCE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    ProcessApplicationManifest::getMergeBlameFile,
                    "manifest-merger-blame-"
                            + getVariantScope().getVariantConfiguration().getBaseName()
                            + "-report.txt");

            artifacts.producesDir(
                    InternalArtifactType.METADATA_FEATURE_MANIFEST.INSTANCE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    ProcessApplicationManifest::getMetadataFeatureManifestOutputDirectory,
                    "metadata-feature");

            artifacts.producesDir(
                    InternalArtifactType.BUNDLE_MANIFEST.INSTANCE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    ProcessApplicationManifest::getBundleManifestOutputDirectory,
                    "bundle-manifest");

            getVariantScope()
                    .getArtifacts()
                    .producesFile(
                            InternalArtifactType.MANIFEST_MERGE_REPORT.INSTANCE,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskProvider,
                            ProcessApplicationManifest::getReportFile,
                            FileUtils.join(
                                            getVariantScope().getGlobalScope().getOutputsDir(),
                                            "logs")
                                    .getAbsolutePath(),
                            "manifest-merger-"
                                    + getVariantScope().getVariantConfiguration().getBaseName()
                                    + "-report.txt");
        }

        @Override
        public void configure(@NonNull ProcessApplicationManifest task) {
            super.configure(task);

            final BaseVariantData variantData = getVariantScope().getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();
            GlobalScope globalScope = getVariantScope().getGlobalScope();

            VariantType variantType = getVariantScope().getType();

            Project project = globalScope.getProject();

            // This includes the dependent libraries.
            task.manifests =
                    getVariantScope().getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);

            // Also include rewritten auto-namespaced manifests if there are any
            if (variantType
                            .isBaseModule() // TODO(b/112251836): Auto namespacing for dynamic features.
                    && getVariantScope()
                            .getGlobalScope()
                            .getExtension()
                            .getAaptOptions()
                            .getNamespaced()
                    && getVariantScope()
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
                getVariantScope()
                        .getArtifacts()
                        .setTaskInputToFinalProduct(
                                InternalArtifactType.NAMESPACED_MANIFESTS.INSTANCE,
                                task.getAutoNamespacedManifests());
            }

            // optional manifest files too.
            if (getVariantScope().getTaskContainer().getMicroApkTask() != null
                    && config.getBuildType().isEmbedMicroApp()) {
                task.microApkManifest = project.files(getVariantScope().getMicroApkManifestFile());
            }
            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST.INSTANCE,
                    task.getCompatibleScreensManifest());

            task.minSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion minSdk = config.getMergedFlavor().getMinSdkVersion();
                                return minSdk == null ? null : minSdk.getApiString();
                            });

            task.targetSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion targetSdk =
                                        config.getMergedFlavor().getTargetSdkVersion();
                                return targetSdk == null ? null : targetSdk.getApiString();
                            });

            task.maxSdkVersion =
                    TaskInputHelper.memoize(config.getMergedFlavor()::getMaxSdkVersion);

            task.optionalFeatures =
                    TaskInputHelper.memoize(
                            () -> getOptionalFeatures(getVariantScope(), isAdvancedProfilingOn));

            artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.APK_LIST.INSTANCE, task.getApkList());

            // set optional inputs per module type
            if (variantType.isBaseModule()) {
                task.packageManifest =
                        getVariantScope()
                                .getArtifactFileCollection(
                                        REVERSE_METADATA_VALUES,
                                        PROJECT,
                                        REVERSE_METADATA_BASE_MODULE_DECLARATION);

                task.featureManifests =
                        getVariantScope()
                                .getArtifactCollection(
                                        REVERSE_METADATA_VALUES,
                                        PROJECT,
                                        REVERSE_METADATA_FEATURE_MANIFEST);

            } else if (variantType.isFeatureSplit()) {
                task.featureNameSupplier =
                        FeatureSetMetadata.getInstance()
                                .getFeatureNameSupplierForTask(getVariantScope(), task);

                task.packageManifest =
                        getVariantScope()
                                .getArtifactFileCollection(
                                        COMPILE_CLASSPATH,
                                        PROJECT,
                                        FEATURE_APPLICATION_ID_DECLARATION);

                task.dependencyFeatureNameArtifacts =
                        getVariantScope()
                                .getArtifactFileCollection(
                                        RUNTIME_CLASSPATH, PROJECT, FEATURE_NAME);
            }

            if (!getVariantScope()
                    .getGlobalScope()
                    .getExtension()
                    .getAaptOptions()
                    .getNamespaced()) {
                task.navigationJsons =
                        project.files(
                                getVariantScope()
                                        .getArtifacts()
                                        .getFinalProduct(
                                                InternalArtifactType.NAVIGATION_JSON.INSTANCE),
                                getVariantScope()
                                        .getArtifactFileCollection(
                                                RUNTIME_CLASSPATH, ALL, NAVIGATION_JSON));
            }
            task.packageOverride.set(task.getProject().provider(config::getApplicationId));
            task.manifestPlaceholders.set(
                    task.getProject().provider(config::getManifestPlaceholders));
            task.getMainManifest()
                    .set(
                            TaskInputHelper.memoizeToProvider(
                                    project, config::getMainManifestFilePath));
            task.manifestOverlays.set(task.getProject().provider(config::getManifestOverlays));
            task.isHybridVariantType = config.getType().isHybrid();
            task.isFeatureSplitVariantType = config.getType().isFeatureSplit();
            task.buildTypeName = config.getBuildType().getName();
            // TODO: here in the "else" block should be the code path for the namespaced pipeline
        }

        /**
         * Implementation of AndroidBundle that only contains a manifest.
         *
         * This is used to pass to the merger manifest snippet that needs to be added during
         * merge.
         */
        public static class ManifestProviderImpl implements ManifestProvider {

            @NonNull
            private final File manifest;

            @NonNull
            private final String name;

            public ManifestProviderImpl(@NonNull File manifest, @NonNull String name) {
                this.manifest = manifest;
                this.name = name;
            }

            @NonNull
            @Override
            public File getManifest() {
                return manifest;
            }

            @NonNull
            @Override
            public String getName() {
                return name;
            }
        }
    }

    private static EnumSet<Feature> getOptionalFeatures(
            VariantScope variantScope, boolean isAdvancedProfilingOn) {
        List<Feature> features = new ArrayList<>();
        VariantType variantType = variantScope.getType();
        if (variantType.isHybrid()) {
            features.add(Feature.TARGET_SANDBOX_VERSION);
        }

        if (variantType.isFeatureSplit()) {
            features.add(Feature.ADD_FEATURE_SPLIT_ATTRIBUTE);
            features.add(Feature.CREATE_FEATURE_MANIFEST);
            features.add(Feature.ADD_USES_SPLIT_DEPENDENCIES);
        }

        if (variantType.isDynamicFeature()) {
            features.add(Feature.STRIP_MIN_SDK_FROM_FEATURE_MANIFEST);
        }

        if (variantType.isHybrid()) {
            features.add(Feature.ADD_INSTANT_APP_FEATURE_SPLIT_INFO);
        }

        if (!variantType.isHybrid()) {
            features.add(Feature.ADD_INSTANT_APP_MANIFEST);
        }

        if (variantType.isBaseModule() || variantType.isFeatureSplit()) {
            features.add(Feature.CREATE_BUNDLETOOL_MANIFEST);
        }

        if (variantType.isDynamicFeature()) {
            // create it for dynamic-features and base modules that are not hybrid base features.
            // hybrid features already contain the split name.
            features.add(Feature.ADD_SPLIT_NAME_TO_BUNDLETOOL_MANIFEST);
        }

        if (variantScope.isTestOnly()) {
            features.add(Feature.TEST_ONLY);
        }
        if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            features.add(Feature.DEBUGGABLE);
            if (isAdvancedProfilingOn) {
                features.add(Feature.ADVANCED_PROFILING);
            }
        }
        if (variantScope.getVariantConfiguration().getDexingType() == DexingType.LEGACY_MULTIDEX) {
            if (variantScope
                    .getGlobalScope()
                    .getProjectOptions()
                    .get(BooleanOption.USE_ANDROID_X)) {
                features.add(Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME);
            } else {
                features.add(Feature.ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME);
            }
        }

        if (variantScope
                .getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES)) {
            features.add(Feature.ENFORCE_UNIQUE_PACKAGE_NAME);
        }

        return features.isEmpty() ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(features);
    }
}
