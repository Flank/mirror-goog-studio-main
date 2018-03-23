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

import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData.InputSet;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.packaging.PackagingUtils;
import com.android.builder.utils.FileCache;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.resources.FileStatus;
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.android.tools.build.apkzlib.zip.compress.Zip64NotSupportedException;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.tooling.BuildException;

/** Abstract task to package an Android artifact. */
public abstract class PackageAndroidArtifact extends IncrementalTask {

    public static final String INSTANT_RUN_PACKAGES_PREFIX = "instant-run";

    // ----- PUBLIC TASK API -----

    // Path sensitivity here and below is absolute due to http://b/72085541
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public BuildableArtifact getManifests() {
        return manifests;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public BuildableArtifact getResourceFiles() {
        return resourceFiles;
    }

    @Input
    @NonNull
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(@Nullable Set<String> abiFilters) {
        this.abiFilters = abiFilters != null ? abiFilters : ImmutableSet.of();
    }

    // ----- PRIVATE TASK API -----

    protected InternalArtifactType manifestType;

    @Input
    public InternalArtifactType getManifestType() {
        return manifestType;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getJavaResourceFiles() {
        return javaResourceFiles;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getJniFolders() {
        return jniFolders;
    }

    protected BuildableArtifact resourceFiles;

    protected FileCollection dexFolders;

    protected BuildableArtifact assets;

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getDexFolders() {
        return dexFolders;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public BuildableArtifact getAssets() {
        return assets;
    }

    /** list of folders and/or jars that contain the merged java resources. */
    protected FileCollection javaResourceFiles;
    protected FileCollection jniFolders;

    private Set<String> abiFilters;

    private boolean debugBuild;
    private boolean jniDebugBuild;

    private CoreSigningConfig signingConfig;

    private PackagingOptions packagingOptions;

    protected Supplier<AndroidVersion> minSdkVersion;

    protected Supplier<InstantRunBuildContext> instantRunContext;

    protected BuildableArtifact manifests;

    @Nullable protected Collection<String> aaptOptionsNoCompress;

    protected FileType instantRunFileType;

    protected OutputScope outputScope;

    protected String projectBaseName;

    @Nullable protected String buildTargetAbi;

    @Nullable protected String buildTargetDensity;

    protected File outputDirectory;

    @Nullable protected OutputFileProvider outputFileProvider;

    @Input
    public String getProjectBaseName() {
        return projectBaseName;
    }

    protected File aaptIntermediateFolder;

    protected AaptGeneration aaptGeneration;

    protected FileCache fileCache;

    protected BuildableArtifact apkList;

    /**
     * Name of directory, inside the intermediate directory, where zip caches are kept.
     */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";
    private static final String ZIP_64_COPY_DIR = "zip64-copy";

    @Input
    public boolean getJniDebugBuild() {
        return jniDebugBuild;
    }

    public void setJniDebugBuild(boolean jniDebugBuild) {
        this.jniDebugBuild = jniDebugBuild;
    }

    @Input
    public boolean getDebugBuild() {
        return debugBuild;
    }

    public void setDebugBuild(boolean debugBuild) {
        this.debugBuild = debugBuild;
    }

    @Nested
    @Optional
    public CoreSigningConfig getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(CoreSigningConfig signingConfig) {
        this.signingConfig = signingConfig;
    }

    @Nested
    public PackagingOptions getPackagingOptions() {
        return packagingOptions;
    }

    public void setPackagingOptions(PackagingOptions packagingOptions) {
        this.packagingOptions = packagingOptions;
    }

    @Input
    public int getMinSdkVersion() {
        return this.minSdkVersion.get().getApiLevel();
    }

    @Input
    public Boolean isInInstantRunMode() {
        return instantRunContext.get().isInInstantRunMode();
    }

    /*
     * We don't really use this. But this forces a full build if the native packaging mode changes.
     */
    @Input
    public List<String> getNativeLibrariesPackagingModeName() {
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
        manifests
                .getFiles()
                .forEach(
                        manifest -> {
                            if (manifest.isFile()
                                    && manifest.getName()
                                            .equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                                listBuilder.add(
                                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                                        manifest,
                                                        () -> getProject().getState().getExecuted())
                                                .toString());
                            }
                        });
        return listBuilder.build();
    }

    @NonNull
    @Input
    public Collection<String> getNoCompressExtensions() {
        return aaptOptionsNoCompress != null ? aaptOptionsNoCompress : Collections.emptyList();
    }

    interface OutputFileProvider {
        @NonNull
        File getOutputFile(@NonNull ApkInfo apkData);
    }

    InternalArtifactType taskInputType;

    @Input
    public InternalArtifactType getTaskInputType() {
        return taskInputType;
    }

    @Input
    @Optional
    @Nullable
    public String getBuildTargetAbi() {
        return buildTargetAbi;
    }

    @Input
    @Optional
    @Nullable
    public String getBuildTargetDensity() {
        return buildTargetDensity;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Returns the paths to generated APKs as @Input to this task, so that when the output file name
     * is changed (e.g., by the users), the task will be re-executed in non-incremental mode.
     */
    @Input
    Collection<String> getApkNames() {
        // this task does not handle packaging of the configuration splits.
        return outputScope
                .getApkDatas()
                .stream()
                .filter(apkData -> apkData.getType() != VariantOutput.OutputType.SPLIT)
                .map(ApkInfo::getOutputFileName)
                .collect(Collectors.toList());
    }

    @InputFiles
    BuildableArtifact getApkList() {
        return apkList;
    }

    private BuildOutput computeBuildOutputFile(ApkInfo apkInfo) {
        File outputFile =
                outputFileProvider != null
                        ? outputFileProvider.getOutputFile(apkInfo)
                        : new File(outputDirectory, apkInfo.getOutputFileName());
        return new BuildOutput(getInternalArtifactType(), apkInfo, outputFile);
    }


    protected abstract InternalArtifactType getInternalArtifactType();

    @Input
    public String getAaptGeneration() {
        return aaptGeneration.name();
    }

    @NonNull
    static Set<File> getAndroidResources(@Nullable File processedResources) {

        return processedResources != null ? ImmutableSet.of(processedResources) : ImmutableSet.of();
    }

    @Override
    protected void doFullTaskAction() {

        // check that we don't have colliding output file names
        checkFileNameUniqueness();
        ExistingBuildElements.from(getTaskInputType(), resourceFiles)
                .transform(
                        (ApkInfo apkInfo, File inputFile) -> {
                            try {
                                return splitFullAction(apkInfo, inputFile);
                            } catch (IOException e) {
                                throw new BuildException(e.getMessage(), e);
                            }
                        })
                .into(getInternalArtifactType(), outputDirectory);
    }

    private void checkFileNameUniqueness() {

        checkFileNameUniqueness(
                new BuildElements(
                        outputScope
                                .getApkDatas()
                                .stream()
                                .map(this::computeBuildOutputFile)
                                .collect(Collectors.toList())));
    }

    @VisibleForTesting
    static void checkFileNameUniqueness(BuildElements buildElements) {

        Collection<File> fileOutputs =
                buildElements.stream().map(BuildOutput::getOutputFile).collect(Collectors.toList());

        java.util.Optional<String> repeatingFileNameOptional =
                fileOutputs
                        .stream()
                        .filter(fileOutput -> Collections.frequency(fileOutputs, fileOutput) > 1)
                        .map(File::getName)
                        .findFirst();
        if (repeatingFileNameOptional.isPresent()) {
            String repeatingFileName = repeatingFileNameOptional.get();
            List<String> conflictingApks =
                    buildElements
                            .stream()
                            .filter(
                                    buildOutput ->
                                            buildOutput
                                                    .getOutputFile()
                                                    .getName()
                                                    .equals(repeatingFileName))
                            .map(
                                    buildOutput -> {
                                        ApkInfo apkInfo = buildOutput.getApkInfo();
                                        if (apkInfo.getFilters().isEmpty()) {
                                            return apkInfo.getType().toString();
                                        } else {
                                            return Joiner.on("-").join(apkInfo.getFilters());
                                        }
                                    })
                            .collect(Collectors.toList());

            throw new RuntimeException(
                    String.format(
                            "Several variant outputs are configured to use "
                                    + "the same file name \"%1$s\", filters : %2$s",
                            repeatingFileName, Joiner.on(":").join(conflictingApks)));
        }
    }

    public File splitFullAction(@NonNull ApkInfo apkData, @Nullable File processedResources)
            throws IOException {

        File incrementalDirForSplit = new File(getIncrementalFolder(), apkData.getFullName());

        /*
         * Clear the intermediate build directory. We don't know if anything is in there and
         * since this is a full build, we don't want to get any interference from previous state.
         */
        if (incrementalDirForSplit.exists()) {
            FileUtils.deleteDirectoryContents(incrementalDirForSplit);
        } else {
            FileUtils.mkdirs(incrementalDirForSplit);
        }

        File cacheByPathDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
        FileUtils.mkdirs(cacheByPathDir);
        FileCacheByPath cacheByPath = new FileCacheByPath(cacheByPathDir);

        /*
         * Clear the cache to make sure we have do not do an incremental build.
         */
        cacheByPath.clear();

        Set<File> androidResources = getAndroidResources(processedResources);

        FileUtils.mkdirs(outputDirectory);

        BuildOutput buildOutput = computeBuildOutputFile(apkData);
        File outputFile = buildOutput.getOutputFile();

        /*
         * Additionally, make sure we have no previous package, if it exists.
         */
        FileUtils.deleteIfExists(outputFile);

        ImmutableMap<RelativeFile, FileStatus> updatedDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
        ImmutableMap<RelativeFile, FileStatus> updatedJavaResources = getJavaResourcesChanges();
        ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                IncrementalRelativeFileSets.fromZipsAndDirectories(assets.getFiles());
        ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> updatedJniResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        BuildElements manifestOutputs = ExistingBuildElements.from(manifestType, manifests);
        doTask(
                apkData,
                incrementalDirForSplit,
                outputFile,
                cacheByPath,
                manifestOutputs,
                updatedDex,
                updatedJavaResources,
                updatedAssets,
                updatedAndroidResources,
                updatedJniResources);

        /*
         * Update the known files.
         */
        KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalDirForSplit);
        saveData.setInputSet(updatedDex.keySet(), InputSet.DEX);
        saveData.setInputSet(updatedJavaResources.keySet(), InputSet.JAVA_RESOURCE);
        saveData.setInputSet(updatedAssets.keySet(), InputSet.ASSET);
        saveData.setInputSet(updatedAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(updatedJniResources.keySet(), InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();

        recordMetrics(outputFile, processedResources);

        return outputFile;
    }

    abstract void recordMetrics(File outputFile, File resourcesApFile);

    /**
     * Copy the input zip file (probably a Zip64) content into a new Zip in the destination folder
     * stripping out all .class files.
     *
     * @param destinationFolder the destination folder to use, the output jar will have the same
     *     name as the input zip file.
     * @param zip64File the input zip file.
     * @return the path to the stripped Zip file.
     * @throws IOException if the copying failed.
     */
    @VisibleForTesting
    static File copyJavaResourcesOnly(File destinationFolder, File zip64File) throws IOException {
        File cacheDir = new File(destinationFolder, ZIP_64_COPY_DIR);
        File copiedZip = new File(cacheDir, zip64File.getName());
        FileUtils.mkdirs(copiedZip.getParentFile());

        try (ZipFile inFile = new ZipFile(zip64File);
                ZipOutputStream outFile =
                        new ZipOutputStream(
                                new BufferedOutputStream(new FileOutputStream(copiedZip)))) {

            Enumeration<? extends ZipEntry> entries = inFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                    outFile.putNextEntry(new ZipEntry(zipEntry.getName()));
                    try {
                        ByteStreams.copy(
                                new BufferedInputStream(inFile.getInputStream(zipEntry)), outFile);
                    } finally {
                        outFile.closeEntry();
                    }
                }
            }
        }
        return copiedZip;
    }

    ImmutableMap<RelativeFile, FileStatus> getJavaResourcesChanges() throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> updatedJavaResourcesBuilder =
                ImmutableMap.builder();
        for (File javaResourceFile : getJavaResourceFiles()) {
            try {
                updatedJavaResourcesBuilder.putAll(
                        javaResourceFile.isFile()
                                ? IncrementalRelativeFileSets.fromZip(javaResourceFile)
                                : IncrementalRelativeFileSets.fromDirectory(javaResourceFile));
            } catch (Zip64NotSupportedException e) {
                updatedJavaResourcesBuilder.putAll(
                        IncrementalRelativeFileSets.fromZip(
                                copyJavaResourcesOnly(getIncrementalFolder(), javaResourceFile)));
            }
        }
        return updatedJavaResourcesBuilder.build();
    }

    /**
     * Packages the application incrementally. In case of instant run packaging, this is not a
     * perfectly incremental task as some files are always rewritten even if no change has occurred.
     *
     * @param apkData the split being built
     * @param outputFile expected output package file
     * @param changedDex incremental dex packaging data
     * @param changedJavaResources incremental java resources
     * @param changedAssets incremental assets
     * @param changedAndroidResources incremental Android resource
     * @param changedNLibs incremental native libraries changed
     * @throws IOException failed to package the APK
     */
    private void doTask(
            @NonNull ApkInfo apkData,
            @NonNull File incrementalDirForSplit,
            @NonNull File outputFile,
            @NonNull FileCacheByPath cacheByPath,
            @NonNull BuildElements manifestOutputs,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        if (isInInstantRunMode()) {
            changedDex = ImmutableMap.copyOf(
                    Maps.filterKeys(
                            changedDex,
                            Predicates.compose(
                                    Predicates.in(getDexFolders().getFiles()),
                                    RelativeFile::getBase
                            )));
        }
        final ImmutableMap<RelativeFile, FileStatus> dexFilesToPackage = changedDex;

        String filter = null;
        FilterData abiFilter = apkData.getFilter(OutputFile.FilterType.ABI);
        if (abiFilter != null) {
            filter = abiFilter.getIdentifier();
        }

        // find the manifest file for this split.
        BuildOutput manifestForSplit = manifestOutputs.element(apkData);

        if (manifestForSplit == null) {
            throw new RuntimeException(
                    "Found a .ap_ for split "
                            + apkData
                            + " but no "
                            + manifestType
                            + " associated manifest file");
        }
        FileUtils.mkdirs(outputFile.getParentFile());

        try (IncrementalPackager packager =
                new IncrementalPackagerBuilder()
                        .withOutputFile(outputFile)
                        .withSigning(signingConfig)
                        .withCreatedBy(getBuilder().getCreatedBy())
                        .withMinSdk(getMinSdkVersion())
                        // TODO: allow extra metadata to be saved in the split scope to avoid
                        // reparsing
                        // these manifest files.
                        .withNativeLibraryPackagingMode(
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                        manifestForSplit.getOutputFile(),
                                        () -> getProject().getState().getExecuted()))
                        .withNoCompressPredicate(
                                PackagingUtils.getNoCompressPredicate(
                                        aaptOptionsNoCompress,
                                        manifestForSplit.getOutputFile(),
                                        () -> getProject().getState().getExecuted()))
                        .withIntermediateDir(incrementalDirForSplit)
                        .withProject(getProject())
                        .withDebuggableBuild(getDebugBuild())
                        .withAcceptedAbis(filter == null ? abiFilters : ImmutableSet.of(filter))
                        .withJniDebuggableBuild(getJniDebugBuild())
                        .build()) {
            packager.updateDex(dexFilesToPackage);
            packager.updateJavaResources(changedJavaResources);
            packager.updateAssets(changedAssets);
            packager.updateAndroidResources(changedAndroidResources);
            packager.updateNativeLibraries(changedNLibs);
            // Only report APK as built if it has actually changed.
            if (packager.hasPendingChangesWithWait()) {
                // FIX-ME : below would not work in multi apk situations. There is code somewhere
                // to ensure we only build ONE multi APK for the target device, make sure it is still
                // active.
                instantRunContext.get().addChangedFile(instantRunFileType, outputFile);
            }
        }

        /*
         * Save all used zips in the cache.
         */
        Stream.concat(
                        dexFilesToPackage.keySet().stream(),
                        Stream.concat(
                                changedJavaResources.keySet().stream(),
                                Stream.concat(
                                        changedAndroidResources.keySet().stream(),
                                        changedNLibs.keySet().stream())))
                .map(RelativeFile::getBase)
                .filter(File::isFile)
                .distinct()
                .forEach(
                        (File f) -> {
                            try {
                                cacheByPath.add(f);
                            } catch (IOException e) {
                                throw new IOExceptionWrapper(e);
                            }
                        });
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }


    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        checkNotNull(changedInputs, "changedInputs == null");
        ExistingBuildElements.from(getTaskInputType(), resourceFiles)
                .transform(
                        (apkInfo, inputFile) -> {
                            try {
                                return splitIncrementalAction(apkInfo, inputFile, changedInputs);
                            } catch (IOException e) {
                                throw new BuildException(e.getMessage(), e);
                            }
                        })
                .into(getInternalArtifactType(), outputDirectory);
    }

    private File splitIncrementalAction(
            @NonNull ApkInfo apkData,
            @Nullable File processedResources,
            Map<File, FileStatus> changedInputs)
            throws IOException {

        Set<File> androidResources = getAndroidResources(processedResources);

        File incrementalDirForSplit = new File(getIncrementalFolder(), apkData.getFullName());

        File cacheByPathDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
        if (!cacheByPathDir.exists()) {
            FileUtils.mkdirs(cacheByPathDir);
        }
        FileCacheByPath cacheByPath = new FileCacheByPath(cacheByPathDir);

        KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalDirForSplit);

        final Set<File> assetsFiles = assets.getFiles();

        Set<Runnable> cacheUpdates = new HashSet<>();
        ImmutableMap<RelativeFile, FileStatus> changedDexFiles =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.DEX,
                        getDexFolders().getFiles(),
                        cacheByPath,
                        cacheUpdates);

        ImmutableMap<RelativeFile, FileStatus> changedJavaResources;
        try {
            changedJavaResources =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            InputSet.JAVA_RESOURCE,
                            getJavaResourceFiles().getFiles(),
                            cacheByPath,
                            cacheUpdates);
        } catch (Zip64NotSupportedException e) {
            // copy all changedInputs into a smaller jar and rerun.
            ImmutableMap.Builder<File, FileStatus> copiedInputs = ImmutableMap.builder();
            for (Map.Entry<File, FileStatus> fileFileStatusEntry : changedInputs.entrySet()) {
                copiedInputs.put(
                        copyJavaResourcesOnly(getIncrementalFolder(), fileFileStatusEntry.getKey()),
                        fileFileStatusEntry.getValue());
            }
            changedJavaResources =
                    KnownFilesSaveData.getChangedInputs(
                            copiedInputs.build(),
                            saveData,
                            InputSet.JAVA_RESOURCE,
                            getJavaResourceFiles().getFiles(),
                            cacheByPath,
                            cacheUpdates);
        }

        ImmutableMap<RelativeFile, FileStatus> changedAssets =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.ASSET,
                        assetsFiles,
                        cacheByPath,
                        cacheUpdates);

        ImmutableMap<RelativeFile, FileStatus> changedAndroidResources =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.ANDROID_RESOURCE,
                        androidResources,
                        cacheByPath,
                        cacheUpdates);

        ImmutableMap<RelativeFile, FileStatus> changedNLibs =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.NATIVE_RESOURCE,
                        getJniFolders().getFiles(),
                        cacheByPath,
                        cacheUpdates);

        BuildOutput buildOutput = computeBuildOutputFile(apkData);
        File outputFile = buildOutput.getOutputFile();

        BuildElements manifestOutputs = ExistingBuildElements.from(manifestType, manifests);

        doTask(
                apkData,
                incrementalDirForSplit,
                outputFile,
                cacheByPath,
                manifestOutputs,
                changedDexFiles,
                changedJavaResources,
                changedAssets,
                changedAndroidResources,
                changedNLibs);

        /*
         * Update the cache
         */
        cacheUpdates.forEach(Runnable::run);

        /*
         * Update the save data keep files.
         */
        ImmutableMap<RelativeFile, FileStatus> allDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
        ImmutableMap<RelativeFile, FileStatus> allJavaResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJavaResourceFiles());
        ImmutableMap<RelativeFile, FileStatus> allAssets =
                IncrementalRelativeFileSets.fromZipsAndDirectories(assetsFiles);
        ImmutableMap<RelativeFile, FileStatus> allAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> allJniResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        saveData.setInputSet(allDex.keySet(), InputSet.DEX);
        saveData.setInputSet(allJavaResources.keySet(), InputSet.JAVA_RESOURCE);
        saveData.setInputSet(allAssets.keySet(), InputSet.ASSET);
        saveData.setInputSet(allAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(allJniResources.keySet(), InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
        return outputFile;
    }

    // ----- ConfigAction -----

    public abstract static class ConfigAction<T extends PackageAndroidArtifact>
            implements TaskConfigAction<T> {

        protected final Project project;
        protected final VariantScope variantScope;
        @NonNull protected final BuildableArtifact manifests;
        @NonNull protected final InternalArtifactType inputResourceFilesType;
        @NonNull protected final File outputDirectory;
        @NonNull protected final OutputScope outputScope;
        @Nullable private final FileCache fileCache;
        @NonNull private final InternalArtifactType manifestType;

        public ConfigAction(
                @NonNull VariantScope variantScope,
                @NonNull File outputDirectory,
                @NonNull InternalArtifactType inputResourceFilesType,
                @NonNull BuildableArtifact manifests,
                @NonNull InternalArtifactType manifestType,
                @Nullable FileCache fileCache,
                @NonNull OutputScope outputScope) {
            this.project = variantScope.getGlobalScope().getProject();
            this.variantScope = checkNotNull(variantScope);
            this.inputResourceFilesType = inputResourceFilesType;
            this.manifests = manifests;
            this.outputDirectory = outputDirectory;
            this.outputScope = outputScope;
            this.manifestType = manifestType;
            this.fileCache = fileCache;
        }

        @Override
        public void execute(@NonNull final T packageAndroidArtifact) {
            GlobalScope globalScope = variantScope.getGlobalScope();
            GradleVariantConfiguration variantConfiguration =
                    variantScope.getVariantConfiguration();

            packageAndroidArtifact.instantRunFileType = FileType.MAIN;
            packageAndroidArtifact.taskInputType = inputResourceFilesType;
            packageAndroidArtifact.setAndroidBuilder(globalScope.getAndroidBuilder());
            packageAndroidArtifact.setVariantName(variantScope.getFullVariantName());
            packageAndroidArtifact.minSdkVersion =
                    TaskInputHelper.memoize(variantScope::getMinSdkVersion);
            packageAndroidArtifact.instantRunContext =
                    TaskInputHelper.memoize(variantScope::getInstantRunBuildContext);
            packageAndroidArtifact.aaptIntermediateFolder =
                    new File(
                            variantScope.getIncrementalDir(packageAndroidArtifact.getName()),
                            "aapt-temp");

            packageAndroidArtifact.resourceFiles =
                    variantScope.getArtifacts().getFinalArtifactFiles(inputResourceFilesType);
            packageAndroidArtifact.outputDirectory = outputDirectory;
            packageAndroidArtifact.setIncrementalFolder(
                    new File(
                            variantScope.getIncrementalDir(packageAndroidArtifact.getName()),
                            "tmp"));
            packageAndroidArtifact.outputScope = outputScope;

            packageAndroidArtifact.fileCache = fileCache;
            packageAndroidArtifact.aaptOptionsNoCompress =
                    DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions())
                            .getNoCompress();

            packageAndroidArtifact.manifests = manifests;

            packageAndroidArtifact.dexFolders = getDexFolders();
            packageAndroidArtifact.javaResourceFiles = getJavaResources();

            packageAndroidArtifact.assets =
                    variantScope.getArtifacts().getFinalArtifactFiles(MERGED_ASSETS);
            packageAndroidArtifact.setAbiFilters(variantConfiguration.getSupportedAbis());
            packageAndroidArtifact.setJniDebugBuild(
                    variantConfiguration.getBuildType().isJniDebuggable());
            packageAndroidArtifact.setDebugBuild(
                    variantConfiguration.getBuildType().isDebuggable());
            packageAndroidArtifact.setPackagingOptions(
                    globalScope.getExtension().getPackagingOptions());

            ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();
            packageAndroidArtifact.projectBaseName = globalScope.getProjectBaseName();
            packageAndroidArtifact.manifestType = manifestType;
            packageAndroidArtifact.aaptGeneration =
                    AaptGeneration.fromProjectOptions(projectOptions);
            packageAndroidArtifact.buildTargetAbi =
                    globalScope.getExtension().getSplits().getAbi().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                            : null;
            packageAndroidArtifact.buildTargetDensity =
                    globalScope.getExtension().getSplits().getDensity().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)
                            : null;

            variantScope
                    .getVariantData()
                    .addTask(
                            TaskContainer.TaskKind.PACKAGE_ANDROID_ARTIFACT,
                            packageAndroidArtifact);
            configure(packageAndroidArtifact);
        }

        protected void configure(T task) {
            GlobalScope globalScope = variantScope.getGlobalScope();
            GradleVariantConfiguration variantConfiguration =
                    variantScope.getVariantConfiguration();
            task.instantRunFileType = FileType.MAIN;

            task.dexFolders = getDexFolders();
            task.javaResourceFiles = getJavaResources();

            if (variantScope.getVariantData().getMultiOutputPolicy()
                    == MultiOutputPolicy.MULTI_APK) {
                task.jniFolders = getJniFolders();
            } else {
                Set<String> filters =
                        AbiSplitOptions.getAbiFilters(
                                globalScope.getExtension().getSplits().getAbiFilters());

                task.jniFolders = filters.isEmpty() ? getJniFolders() : project.files();
            }

            task.apkList =
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.APK_LIST);

            // Don't sign.
            task.setSigningConfig(variantConfiguration.getSigningConfig());
        }

        @NonNull
        public FileCollection getDexFolders() {
            return variantScope
                    .getTransformManager()
                    .getPipelineOutputAsFileCollection(StreamFilter.DEX);
        }

        @NonNull
        public FileCollection getJavaResources() {
            return variantScope
                    .getTransformManager()
                    .getPipelineOutputAsFileCollection(StreamFilter.RESOURCES);
        }

        @NonNull
        public FileCollection getJniFolders() {
            return variantScope
                    .getTransformManager()
                    .getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS);
        }
    }
}
