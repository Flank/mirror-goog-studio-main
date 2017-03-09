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

import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MERGED_ASSETS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apkzlib.utils.IOExceptionWrapper;
import com.android.apkzlib.zip.compress.Zip64NotSupportedException;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.DexPackagingPolicy;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData.InputSet;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
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

/** Abstract task to package an Android artifact. */
public abstract class PackageAndroidArtifact extends IncrementalTask {

    public static final String INSTANT_RUN_PACKAGES_PREFIX = "instant-run";

    // ----- PUBLIC TASK API -----

    @InputFiles
    public FileCollection getManifests() {
        return manifests;
    }

    @InputFiles
    public FileCollection getResourceFiles() {
        return resourceFiles;
    }

    File outputDirectory;

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
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

    protected VariantScope.TaskOutputType manifestType;

    @Input
    public VariantScope.TaskOutputType getManifestType() {
        return manifestType;
    }

    @InputFiles
    @Optional
    public FileCollection getJavaResourceFiles() {
        return javaResourceFiles;
    }

    @InputFiles
    @Optional
    public FileCollection getJniFolders() {
        return jniFolders;
    }

    protected FileCollection resourceFiles;

    protected FileCollection dexFolders;

    protected FileCollection assets;

    @InputFiles
    @Optional
    public FileCollection getDexFolders() {
        return dexFolders;
    }

    @InputFiles
    public FileCollection getAssets() {
        return assets;
    }

    /** list of folders and/or jars that contain the merged java resources. */
    protected FileCollection javaResourceFiles;
    protected FileCollection jniFolders;

    @NonNull private Set<String> abiFilters;

    private boolean debugBuild;
    private boolean jniDebugBuild;

    private CoreSigningConfig signingConfig;

    private PackagingOptions packagingOptions;

    private ApiVersion minSdkVersion;

    protected InstantRunBuildContext instantRunContext;

    protected File instantRunSupportDir;

    protected DexPackagingPolicy dexPackagingPolicy;

    protected FileCollection manifests;

    protected AaptOptions aaptOptions;

    protected FileType instantRunFileType;

    protected SplitScope splitScope;

    protected String projectBaseName;

    @Input
    public String getProjectBaseName() {
        return projectBaseName;
    }

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
        return this.minSdkVersion.getApiLevel();
    }

    public void setMinSdkVersion(ApiVersion version) {
        this.minSdkVersion = version;
    }

    @Input
    public String getDexPackagingPolicy() {
        return dexPackagingPolicy.toString();
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
                                                        manifest)
                                                .toString());
                            }
                        });
        return listBuilder.build();
    }

    @Input
    public Collection<String> getNoCompressExtensions() {
        return MoreObjects.<Collection<String>>firstNonNull(
                aaptOptions.getNoCompress(), Collections.emptyList());
    }

    interface OutputFileProvider {
        File getOutputFile(ApkData apkData);
    }

    public OutputFileProvider outputFileProvider;

    VariantScope.TaskOutputType taskInputType;

    @Input
    public VariantScope.TaskOutputType getTaskInputType() {
        return taskInputType;
    }

    protected abstract VariantScope.TaskOutputType getTaskOutputType();

    @Override
    protected void doFullTaskAction() throws IOException {

        Collection<BuildOutput> mergedResources =
                BuildOutputs.load(getTaskInputType(), resourceFiles);
        splitScope.parallelForEachOutput(
                mergedResources, getTaskInputType(), getTaskOutputType(), this::splitFullAction);
        splitScope.save(getTaskOutputType(), outputDirectory);
    }

    public File splitFullAction(@NonNull ApkData apkData, @Nullable File processedResources)
            throws IOException {

        File incrementalDirForSplit = new File(getIncrementalFolder(), apkData.getDirName());

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

        Set<File> androidResources = new HashSet<>();
        if (processedResources != null) {
            androidResources.add(processedResources);
        }

        FileUtils.mkdirs(getOutputDirectory());
        // TO DO : move ALL output file name calculations to Split.
        String splitOutputFileName = apkData.getOutputFileName();
        File outputFile =
                splitOutputFileName == null
                        ? outputFileProvider.getOutputFile(apkData)
                        : new File(outputDirectory, splitOutputFileName);

        /*
         * Additionally, make sure we have no previous package, if it exists.
         */
        FileUtils.deleteIfExists(outputFile);

        ImmutableMap<RelativeFile, FileStatus> updatedDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
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
        ImmutableMap<RelativeFile, FileStatus> updatedJavaResources =
                updatedJavaResourcesBuilder.build();
        ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                IncrementalRelativeFileSets.fromZipsAndDirectories(assets.getFiles());
        ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> updatedJniResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        Collection<BuildOutput> manifestOutputs = BuildOutputs.load(manifestType, manifests);
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
            @NonNull ApkData apkData,
            @NonNull File incrementalDirForSplit,
            @NonNull File outputFile,
            @NonNull FileCacheByPath cacheByPath,
            @NonNull Collection<BuildOutput> manifestOutputs,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        if (dexPackagingPolicy == DexPackagingPolicy.INSTANT_RUN_MULTI_APK) {
            changedDex = ImmutableMap.copyOf(
                    Maps.filterKeys(
                            changedDex,
                            Predicates.compose(
                                    Predicates.in(getDexFolders().getFiles()),
                                    RelativeFile::getBase
                            )));
        }
        final ImmutableMap<RelativeFile, FileStatus> dexFilesToPackage = changedDex;

        String abiFilter = apkData.getFilter(com.android.build.OutputFile.FilterType.ABI);

        // find the manifest file for this split.
        BuildOutput manifestForSplit = SplitScope.getOutput(manifestOutputs, manifestType, apkData);

        if (manifestForSplit == null || manifestForSplit.getOutputFile() == null) {
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
                        // TODO: allow extra metadata to be saved in the split scope to avoid reparsing
                        // these manifest files.
                        .withNativeLibraryPackagingMode(
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                        manifestForSplit.getOutputFile()))
                        .withNoCompressPredicate(
                                PackagingUtils.getNoCompressPredicate(
                                        aaptOptions, manifestForSplit.getOutputFile()))
                        .withIntermediateDir(incrementalDirForSplit)
                        .withProject(getProject())
                        .withDebuggableBuild(getDebugBuild())
                        .withAcceptedAbis(
                                abiFilter == null ? abiFilters : ImmutableSet.of(abiFilter))
                        .withJniDebuggableBuild(getJniDebugBuild())
                        .build()) {
            packager.updateDex(dexFilesToPackage);
            packager.updateJavaResources(changedJavaResources);
            packager.updateAssets(changedAssets);
            packager.updateAndroidResources(changedAndroidResources);
            packager.updateNativeLibraries(changedNLibs);
        }

        // FIX-ME : below would not work in multi apk situations. There is code somewhere
        // to ensure we only build ONE multi APK for the target device, make sure it is still
        // active.

        // Mark this APK production, this might get overridden if the apk is signed/aligned.
        instantRunContext.addChangedFile(instantRunFileType, outputFile);

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
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        checkNotNull(changedInputs, "changedInputs == null");
        splitScope.parallelForEachOutput(
                BuildOutputs.load(getTaskInputType(), resourceFiles),
                getTaskInputType(),
                getTaskOutputType(),
                (split, output) -> splitIncrementalAction(split, output, changedInputs));
        splitScope.save(getTaskOutputType(), outputDirectory);
    }

    private File splitIncrementalAction(
            ApkData apkData, @Nullable File resourceFile, Map<File, FileStatus> changedInputs)
            throws IOException {

        Set<File> androidResources = new HashSet<>();
        if (resourceFile != null) {
            androidResources.add(resourceFile);
        }

        File incrementalDirForSplit = new File(getIncrementalFolder(), apkData.getDirName());

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

        ImmutableMap<RelativeFile, FileStatus> changedJavaResources =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        InputSet.JAVA_RESOURCE,
                        getJavaResourceFiles().getFiles(),
                        cacheByPath,
                        cacheUpdates);

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

        // TO DO : move ALL output file name calculations to Split.
        String splitOutputFileName = apkData.getOutputFileName();
        File outputFile =
                splitOutputFileName == null
                        ? outputFileProvider.getOutputFile(apkData)
                        : new File(outputDirectory, splitOutputFileName);

        Collection<BuildOutput> manifestOutputs = BuildOutputs.load(manifestType, manifests);

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
        recordMetrics(outputFile, resourceFile);
        return outputFile;
    }

    // ----- ConfigAction -----

    public abstract static class ConfigAction<T extends PackageAndroidArtifact>
            implements TaskConfigAction<T> {

        protected final Project project;
        protected final PackagingScope packagingScope;
        @Nullable protected final DexPackagingPolicy dexPackagingPolicy;
        @NonNull protected final FileCollection manifests;
        @NonNull protected final VariantScope.TaskOutputType inputResourceFilesType;
        @NonNull protected final FileCollection resourceFiles;
        @NonNull protected final File outputDirectory;
        @NonNull protected final SplitScope splitScope;
        @NonNull private final VariantScope.TaskOutputType manifestType;

        public ConfigAction(
                @NonNull PackagingScope packagingScope,
                @NonNull File outputDirectory,
                @Nullable InstantRunPatchingPolicy patchingPolicy,
                @NonNull VariantScope.TaskOutputType inputResourceFilesType,
                @NonNull FileCollection resourceFiles,
                @NonNull FileCollection manifests,
                @NonNull VariantScope.TaskOutputType manifestType,
                @NonNull SplitScope splitScope) {
            this.project = packagingScope.getProject();
            this.packagingScope = checkNotNull(packagingScope);
            this.inputResourceFilesType = inputResourceFilesType;
            dexPackagingPolicy = patchingPolicy == null
                    ? DexPackagingPolicy.STANDARD
                    : patchingPolicy.getDexPatchingPolicy();
            this.manifests = manifests;
            this.outputDirectory = outputDirectory;
            this.resourceFiles = resourceFiles;
            this.splitScope = splitScope;
            this.manifestType = manifestType;
        }

        @Override
        public void execute(@NonNull final T packageAndroidArtifact) {
            packageAndroidArtifact.instantRunFileType = FileType.MAIN;
            packageAndroidArtifact.taskInputType = inputResourceFilesType;
            packageAndroidArtifact.setAndroidBuilder(packagingScope.getAndroidBuilder());
            packageAndroidArtifact.setVariantName(packagingScope.getFullVariantName());
            packageAndroidArtifact.setMinSdkVersion(packagingScope.getMinSdkVersion());
            packageAndroidArtifact.instantRunContext = packagingScope.getInstantRunBuildContext();
            packageAndroidArtifact.dexPackagingPolicy = dexPackagingPolicy;
            packageAndroidArtifact.instantRunSupportDir =
                    packagingScope.getInstantRunSupportDir();
            packageAndroidArtifact.resourceFiles = resourceFiles;
            packageAndroidArtifact.outputDirectory = outputDirectory;
            packageAndroidArtifact.setIncrementalFolder(
                    packagingScope.getIncrementalDir(packageAndroidArtifact.getName()));
            packageAndroidArtifact.splitScope = splitScope;

            packageAndroidArtifact.aaptOptions = packagingScope.getAaptOptions();

            packageAndroidArtifact.outputDirectory = outputDirectory;
            packageAndroidArtifact.manifests = manifests;

            packageAndroidArtifact.dexFolders = packagingScope.getDexFolders();
            packageAndroidArtifact.javaResourceFiles = packagingScope.getJavaResources();

            packageAndroidArtifact.assets = packagingScope.getOutputs(MERGED_ASSETS);
            packageAndroidArtifact.setAbiFilters(packagingScope.getSupportedAbis());
            packageAndroidArtifact.setJniDebugBuild(packagingScope.isJniDebuggable());
            packageAndroidArtifact.setDebugBuild(packagingScope.isDebuggable());
            packageAndroidArtifact.setPackagingOptions(packagingScope.getPackagingOptions());

            packageAndroidArtifact.outputFileProvider =
                    split ->
                            packagingScope.getOutputPackageFile(
                                    outputDirectory, packagingScope.getProjectBaseName(), split);
            packageAndroidArtifact.projectBaseName = packagingScope.getProjectBaseName();
            packageAndroidArtifact.manifestType = manifestType;
            packagingScope.addTask(
                    TaskContainer.TaskKind.PACKAGE_ANDROID_ARTIFACT, packageAndroidArtifact);
            configure(packageAndroidArtifact);
        }

        protected void configure(T task) {
            task.instantRunFileType = FileType.MAIN;
            task.dexPackagingPolicy = dexPackagingPolicy;

            task.dexFolders = packagingScope.getDexFolders();
            task.javaResourceFiles = packagingScope.getJavaResources();

            if (packagingScope.getSplitHandlingPolicy() == SplitHandlingPolicy.PRE_21_POLICY) {
                task.jniFolders = packagingScope.getJniFolders();
            } else {
                Set<String> filters =
                        AbiSplitOptions.getAbiFilters(packagingScope.getAbiFilters());

                task.jniFolders =
                        filters.isEmpty() ? packagingScope.getJniFolders() : project.files();
            }

            // Don't sign.
            task.setSigningConfig(packagingScope.getSigningConfig());
        }
    }
}
