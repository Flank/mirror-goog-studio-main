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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apkzlib.utils.IOExceptionWrapper;
import com.android.apkzlib.zip.compress.Zip64NotSupportedException;
import com.android.build.gradle.internal.annotations.PackageFile;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.DexPackagingPolicy;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData.InputSet;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.tooling.BuildException;

/**
 * Abstract task to package an Android artifact.
 */
public abstract class PackageAndroidArtifact extends IncrementalTask implements FileSupplier {

    public static final String INSTANT_RUN_PACKAGES_PREFIX = "instant-run";

    // ----- PUBLIC TASK API -----

    @InputFile
    public File getResourceFile() {
        return resourceFile;
    }

    public void setResourceFile(File resourceFile) {
        this.resourceFile = resourceFile;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Input
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(Set<String> abiFilters) {
        this.abiFilters = abiFilters;
    }

    // ----- PRIVATE TASK API -----

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

    private File resourceFile;

    protected FileCollection dexFolders;

    private File assets;

    @InputFiles
    @Optional
    public FileCollection getDexFolders() {
        return dexFolders;
    }

    @InputDirectory
    public File getAssets() {
        return assets;
    }

    public void setAssets(File assets) {
        this.assets = assets;
    }

    /** list of folders and/or jars that contain the merged java resources. */
    protected FileCollection javaResourceFiles;
    protected FileCollection jniFolders;

    @PackageFile
    private File outputFile;

    private Set<String> abiFilters;

    private boolean debugBuild;
    private boolean jniDebugBuild;

    private CoreSigningConfig signingConfig;

    private PackagingOptions packagingOptions;

    private ApiVersion minSdkVersion;

    protected InstantRunBuildContext instantRunContext;

    protected File instantRunSupportDir;

    protected DexPackagingPolicy dexPackagingPolicy;

    protected File manifest;

    protected AaptOptions aaptOptions;

    protected FileType instantRunFileType;

    /**
     * Name of directory, inside the intermediate directory, where zip caches are kept.
     */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";
    private static final String ZIP_64_COPY_DIR = "zip64-copy";

    /**
     * Zip caches to allow incremental updates.
     */
    protected FileCacheByPath cacheByPath;

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
    public String getNativeLibrariesPackagingModeName() {
        return PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifest).toString();
    }

    @Input
    public Collection<String> getNoCompressExtensions() {
        return MoreObjects.<Collection<String>>firstNonNull(
                aaptOptions.getNoCompress(), Collections.emptyList());
    }

    protected Predicate<String> getNoCompressPredicate() {
        return PackagingUtils.getNoCompressPredicate(aaptOptions, manifest);
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        /*
         * Clear the cache to make sure we have do not do an incremental build.
         */
        cacheByPath.clear();

        /*
         * Also clear the intermediate build directory. We don't know if anything is in there and
         * since this is a full build, we don't want to get any interference from previous state.
         */
        FileUtils.deleteDirectoryContents(getIncrementalFolder());

        Set<File> androidResources = new HashSet<>();
        File androidResourceFile = getResourceFile();
        if (androidResourceFile != null) {
            androidResources.add(androidResourceFile);
        }

        /*
         * Additionally, make sure we have no previous package, if it exists.
         */
        getOutputFile().delete();

        ImmutableMap<RelativeFile, FileStatus> updatedDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
        ImmutableMap<RelativeFile, FileStatus> updatedJavaResources = getJavaResourcesChanges();
        ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getAssets()));
        ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> updatedJniResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        doTask(
                updatedDex,
                updatedJavaResources,
                updatedAssets,
                updatedAndroidResources,
                updatedJniResources);

        /*
         * Update the known files.
         */
        KnownFilesSaveData saveData = KnownFilesSaveData.make(getIncrementalFolder());
        saveData.setInputSet(updatedDex.keySet(), InputSet.DEX);
        saveData.setInputSet(updatedJavaResources.keySet(), InputSet.JAVA_RESOURCE);
        saveData.setInputSet(updatedAssets.keySet(), InputSet.ASSET);
        saveData.setInputSet(updatedAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(updatedJniResources.keySet(), InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
    }

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
     * @param changedDex incremental dex packaging data
     * @param changedJavaResources incremental java resources
     * @param changedAssets incremental assets
     * @param changedAndroidResources incremental Android resource
     * @param changedNLibs incremental native libraries changed
     * @throws IOException failed to package the APK
     */
    private void doTask(
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        Collection<File> instantRunDexBaseFiles;
        if (dexPackagingPolicy == DexPackagingPolicy.INSTANT_RUN_MULTI_APK) {
            changedDex = ImmutableMap.copyOf(
                    Maps.filterKeys(
                            changedDex,
                            Predicates.compose(
                                    Predicates.in(getDexFolders().getFiles()),
                                    RelativeFile::getBase
                            )));
        }

        try (IncrementalPackager packager = new IncrementalPackagerBuilder()
                .withOutputFile(getOutputFile())
                .withSigning(signingConfig)
                .withCreatedBy(getBuilder().getCreatedBy())
                .withMinSdk(getMinSdkVersion())
                .withNativeLibraryPackagingMode(
                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifest))
                .withNoCompressPredicate(getNoCompressPredicate())
                .withIntermediateDir(getIncrementalFolder())
                .withProject(getProject())
                .withDebuggableBuild(getDebugBuild())
                .withAcceptedAbis(getAbiFilters())
                .withJniDebuggableBuild(getJniDebugBuild())
                .build()) {
            packager.updateDex(changedDex);
            packager.updateJavaResources(changedJavaResources);
            packager.updateAssets(changedAssets);
            packager.updateAndroidResources(changedAndroidResources);
            packager.updateNativeLibraries(changedNLibs);
        }

        /*
         * Save all used zips in the cache.
         */
        Stream.concat(
            changedDex.keySet().stream(),
            Stream.concat(
                    changedJavaResources.keySet().stream(),
                    Stream.concat(
                            changedAndroidResources.keySet().stream(),
                            changedNLibs.keySet().stream())))
            .map(RelativeFile::getBase)
            .filter(File::isFile)
            .distinct()
            .forEach((File f) -> {
                try {
                    cacheByPath.add(f);
                } catch (IOException e) {
                    throw new IOExceptionWrapper(e);
                }
            });

        // Mark this APK production, this will eventually be saved when instant-run is enabled.
        // this might get overridden if the apk is signed/aligned.
        try {
            instantRunContext.addChangedFile(instantRunFileType, getOutputFile());
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        }
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        checkNotNull(changedInputs, "changedInputs == null");

        Set<File> androidResources = new HashSet<>();
        File androidResourceFile = getResourceFile();
        if (androidResourceFile != null) {
            androidResources.add(androidResourceFile);
        }

        KnownFilesSaveData saveData = KnownFilesSaveData.make(getIncrementalFolder());

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
                        Collections.singleton(getAssets()),
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

        doTask(
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
                IncrementalRelativeFileSets.fromZipsAndDirectories(
                        Collections.singleton(getAssets()));
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
    }

    /**
     * Creates the new instant run resources from the dex files. This method is not
     * incremental. It will ignore updates and look at all dex files and always rebuild the
     * instant run resources.
     *
     * <p>The instant run resources are resources that package dex files.
     *
     * @param dexBaseFiles the base files to dex
     * @return the instant run resources
     * @throws IOException failed to create the instant run resources
     */
    @NonNull
    private ImmutableMap<RelativeFile, FileStatus> makeInstantRunResourcesFromDex(
            @NonNull Iterable<File> dexBaseFiles) throws IOException {

        File tmpZipFile = new File(instantRunSupportDir, "instant-run.zip");
        boolean existedBefore = tmpZipFile.exists();

        Files.createParentDirs(tmpZipFile);
        ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmpZipFile)));
        // no need to compress a zip, the APK itself gets compressed.
        zipFile.setLevel(0);

        try {
            for (File dexFolder : dexBaseFiles) {
                for (File file : Files.fileTreeTraverser().breadthFirstTraversal(dexFolder)) {
                    if (file.isFile() && file.getName().endsWith(SdkConstants.DOT_DEX)) {
                        // There are several pieces of code in the runtime library that depend
                        // on this exact pattern, so it should not be changed without thorough
                        // testing (it's basically part of the contract).
                        String entryName =
                                file.getParentFile().getName() + "-" + file.getName();
                        zipFile.putNextEntry(new ZipEntry(entryName));
                        try {
                            Files.copy(file, zipFile);
                        } finally {
                            zipFile.closeEntry();
                        }
                    }
                }
            }
        } finally {
            zipFile.close();
        }

        RelativeFile resourcesFile = new RelativeFile(instantRunSupportDir, tmpZipFile);
        return ImmutableMap.of(resourcesFile, existedBefore? FileStatus.CHANGED : FileStatus.NEW);
    }

    // ----- FileSupplierTask -----

    @Override
    public File get() {
        return getOutputFile();
    }

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    // ----- ConfigAction -----

    public abstract static class ConfigAction<T extends PackageAndroidArtifact>
            implements TaskConfigAction<T> {

        protected final Project project;
        protected final PackagingScope packagingScope;
        protected final DexPackagingPolicy dexPackagingPolicy;

        public ConfigAction(
                @NonNull PackagingScope packagingScope,
                @Nullable InstantRunPatchingPolicy patchingPolicy) {
            this.project = packagingScope.getProject();
            this.packagingScope = checkNotNull(packagingScope);
            dexPackagingPolicy = patchingPolicy == null
                    ? DexPackagingPolicy.STANDARD
                    : patchingPolicy.getDexPatchingPolicy();
        }

        @Override
        public void execute(@NonNull final T packageAndroidArtifact) {
            packageAndroidArtifact.instantRunFileType = FileType.MAIN;
            packageAndroidArtifact.setAndroidBuilder(packagingScope.getAndroidBuilder());
            packageAndroidArtifact.setVariantName(packagingScope.getFullVariantName());
            packageAndroidArtifact.setMinSdkVersion(packagingScope.getMinSdkVersion());
            packageAndroidArtifact.instantRunContext =
                    packagingScope.getInstantRunBuildContext();
            packageAndroidArtifact.dexPackagingPolicy = dexPackagingPolicy;
            packageAndroidArtifact.instantRunSupportDir =
                    packagingScope.getInstantRunSupportDir();
            packageAndroidArtifact.setIncrementalFolder(
                    packagingScope.getIncrementalDir(packageAndroidArtifact.getName()));

            packageAndroidArtifact.aaptOptions = packagingScope.getAaptOptions();
            packageAndroidArtifact.manifest = packagingScope.getManifestFile();

            File cacheByPathDir = new File(packageAndroidArtifact.getIncrementalFolder(),
                    ZIP_DIFF_CACHE_DIR);
            FileUtils.mkdirs(cacheByPathDir);
            packageAndroidArtifact.cacheByPath = new FileCacheByPath(cacheByPathDir);

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "resourceFile", packagingScope::getFinalResourcesFile);

            packageAndroidArtifact.dexFolders = packagingScope.getDexFolders();
            packageAndroidArtifact.javaResourceFiles = packagingScope.getJavaResources();

            packageAndroidArtifact.setAssets(packagingScope.getAssetsDir());

            if (packagingScope.getSplitHandlingPolicy() == SplitHandlingPolicy.PRE_21_POLICY) {
                packageAndroidArtifact.jniFolders = packagingScope.getJniFolders();
            } else {
                Set<String> filters =
                        AbiSplitOptions.getAbiFilters(packagingScope.getAbiFilters());

                packageAndroidArtifact.jniFolders = filters.isEmpty()
                        ? packagingScope.getJniFolders()
                        : project.files();
            }

            ConventionMappingHelper.map(packageAndroidArtifact, "abiFilters", () -> {
                String filter = packagingScope.getMainOutputFile().getFilter(
                        com.android.build.OutputFile.ABI);
                if (filter != null) {
                    return ImmutableSet.of(filter);
                }
                Set<String> supportedAbis = packagingScope.getSupportedAbis();
                // TODO: nullability
                if (supportedAbis != null) {
                    return supportedAbis;
                }

                return ImmutableSet.of();
            });

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "jniDebugBuild", packagingScope::isJniDebuggable);

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "debugBuild", packagingScope::isDebuggable);

            packageAndroidArtifact.setSigningConfig(packagingScope.getSigningConfig());

            ConventionMappingHelper.map(
                    packageAndroidArtifact, "packagingOptions", packagingScope::getPackagingOptions);
        }
    }
}
