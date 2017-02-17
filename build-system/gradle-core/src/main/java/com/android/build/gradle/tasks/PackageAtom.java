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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_ASSETS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_JAVA_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.apkzlib.utils.IOExceptionWrapper;
import com.android.apkzlib.zfile.ApkCreatorFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData.InputSet;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;

/** Task to package all the Android atoms. */
public class PackageAtom extends IncrementalTask {

    /** Name of directory, inside the intermediate directory, where zip caches are kept. */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException, InterruptedException {
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();
        // Clear the cache to make sure we do not do an incremental build.
        cacheByPath.clear();

        for (AtomConfig.AtomInfo atomInfo : atomConfigTask.getAtomInfoCollection()) {
            executor.execute(
                    () -> {
                        // Also clear the intermediate build directory. We don't know if anything is in there and
                        // since this is a full build, we don't want to get any interference from previous state.
                        File incrementalFolder =
                                new File(getIncrementalFolder(), atomInfo.getName());
                        FileUtils.mkdirs(incrementalFolder);
                        FileUtils.deleteDirectoryContents(incrementalFolder);

                        // Additionally, make sure we have no previous package, if it exists.
                        atomInfo.getFinalOutputFile().delete();

                        ImmutableMap<RelativeFile, FileStatus> updatedDex =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getFinalDexDir()));
                        ImmutableMap<RelativeFile, FileStatus> updatedJavaResources =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getJavaResourcesDir()));
                        ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getAssetDir()));
                        ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getPackageOutputFile()));
                        ImmutableMap<RelativeFile, FileStatus> updatedJniResources =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getJniDir()));

                        doTask(
                                atomInfo,
                                updatedDex,
                                updatedJavaResources,
                                updatedAssets,
                                updatedAndroidResources,
                                updatedJniResources);

                        // Update the known files.
                        KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalFolder);
                        saveData.setInputSet(updatedDex.keySet(), InputSet.DEX);
                        saveData.setInputSet(updatedJavaResources.keySet(), InputSet.JAVA_RESOURCE);
                        saveData.setInputSet(updatedAssets.keySet(), InputSet.ASSET);
                        saveData.setInputSet(
                                updatedAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
                        saveData.setInputSet(
                                updatedJniResources.keySet(), InputSet.NATIVE_RESOURCE);
                        saveData.saveCurrentData();
                        return null;
                    });
        }
        executor.waitForTasksWithQuickFail(true);
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs)
            throws InterruptedException {
        checkNotNull(changedInputs, "changedInputs == null");
        WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();

        for (AtomConfig.AtomInfo atomInfo : atomConfigTask.getAtomInfoCollection()) {
            executor.execute(
                    () -> {
                        String atomName = atomInfo.getName();
                        File incrementalFolder = new File(getIncrementalFolder(), atomName);
                        KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalFolder);

                        Set<Runnable> cacheUpdates = new HashSet<>();
                        ImmutableMap<RelativeFile, FileStatus> changedDexFiles =
                                KnownFilesSaveData.getChangedInputs(
                                        changedInputs,
                                        saveData,
                                        InputSet.DEX,
                                        Collections.singleton(atomInfo.getFinalDexDir()),
                                        cacheByPath,
                                        cacheUpdates);

                        ImmutableMap<RelativeFile, FileStatus> changedJavaResources =
                                KnownFilesSaveData.getChangedInputs(
                                        changedInputs,
                                        saveData,
                                        InputSet.JAVA_RESOURCE,
                                        Collections.singleton(atomInfo.getJavaResourcesDir()),
                                        cacheByPath,
                                        cacheUpdates);

                        ImmutableMap<RelativeFile, FileStatus> changedAssets =
                                KnownFilesSaveData.getChangedInputs(
                                        changedInputs,
                                        saveData,
                                        InputSet.ASSET,
                                        Collections.singleton(atomInfo.getAssetDir()),
                                        cacheByPath,
                                        cacheUpdates);

                        ImmutableMap<RelativeFile, FileStatus> changedAndroidResources =
                                KnownFilesSaveData.getChangedInputs(
                                        changedInputs,
                                        saveData,
                                        InputSet.ANDROID_RESOURCE,
                                        Collections.singleton(atomInfo.getPackageOutputFile()),
                                        cacheByPath,
                                        cacheUpdates);

                        ImmutableMap<RelativeFile, FileStatus> changedNLibs =
                                KnownFilesSaveData.getChangedInputs(
                                        changedInputs,
                                        saveData,
                                        InputSet.NATIVE_RESOURCE,
                                        Collections.singleton(atomInfo.getJniDir()),
                                        cacheByPath,
                                        cacheUpdates);

                        doTask(
                                atomInfo,
                                changedDexFiles,
                                changedJavaResources,
                                changedAssets,
                                changedAndroidResources,
                                changedNLibs);

                        // Update the cache
                        cacheUpdates.forEach(Runnable::run);

                        // Update the save data keep files.
                        ImmutableMap<RelativeFile, FileStatus> allDex =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getFinalDexDir()));
                        ImmutableMap<RelativeFile, FileStatus> allJavaResources =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getJavaResourcesDir()));
                        ImmutableMap<RelativeFile, FileStatus> allAndroidResources =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getPackageOutputFile()));
                        ImmutableMap<RelativeFile, FileStatus> allJniResources =
                                IncrementalRelativeFileSets.fromZipsAndDirectories(
                                        Collections.singleton(atomInfo.getJniDir()));

                        saveData.setInputSet(allDex.keySet(), InputSet.DEX);
                        saveData.setInputSet(allJavaResources.keySet(), InputSet.JAVA_RESOURCE);
                        saveData.setInputSet(
                                allAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
                        saveData.setInputSet(allJniResources.keySet(), InputSet.NATIVE_RESOURCE);
                        saveData.saveCurrentData();
                        return null;
                    });
        }
        executor.waitForTasksWithQuickFail(true);
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
            @NonNull AtomConfig.AtomInfo atomInfo,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {
        // This is a no-op, exit early.
        if (changedDex.isEmpty()
                && changedJavaResources.isEmpty()
                && changedAssets.isEmpty()
                && changedAndroidResources.isEmpty()
                && changedNLibs.isEmpty()) {
            return;
        }

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk = ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        PrivateKey key;
        X509Certificate certificate;
        boolean v1SigningEnabled;
        boolean v2SigningEnabled;

        try {
            if (signingConfig != null && signingConfig.isSigningReady()) {
                CertificateInfo certificateInfo =
                        KeystoreHelper.getCertificateInfo(
                                signingConfig.getStoreType(),
                                checkNotNull(signingConfig.getStoreFile()),
                                checkNotNull(signingConfig.getStorePassword()),
                                checkNotNull(signingConfig.getKeyPassword()),
                                checkNotNull(signingConfig.getKeyAlias()));
                key = certificateInfo.getKey();
                certificate = certificateInfo.getCertificate();
                v1SigningEnabled = signingConfig.isV1SigningEnabled();
                v2SigningEnabled = signingConfig.isV2SigningEnabled();
            } else {
                key = null;
                certificate = null;
                v1SigningEnabled = false;
                v2SigningEnabled = false;
            }

            ApkCreatorFactory.CreationData creationData =
                    new ApkCreatorFactory.CreationData(
                            atomInfo.getFinalOutputFile(),
                            key,
                            certificate,
                            v1SigningEnabled,
                            v2SigningEnabled,
                            null, // BuiltBy
                            getBuilder().getCreatedBy(),
                            getMinSdkVersion(),
                            PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                    atomInfo.getManifestFile()),
                            getNoCompressPredicate(atomInfo.getManifestFile()));

            getLogger()
                    .debug(
                            "Information to create the APK: apkPath={}, v1SigningEnabled={},"
                                    + " v2SigningEnabled={}, builtBy={}, createdBy={}, minSdkVersion={},"
                                    + " nativeLibrariesPackagingMode={}",
                            creationData.getApkPath(),
                            creationData.isV1SigningEnabled(),
                            creationData.isV2SigningEnabled(),
                            creationData.getBuiltBy(),
                            creationData.getCreatedBy(),
                            creationData.getMinSdkVersion(),
                            creationData.getNativeLibrariesPackagingMode());

            try (IncrementalPackager packager =
                    new IncrementalPackager(
                            creationData,
                            new File(getIncrementalFolder(), atomInfo.getName()),
                            ApkCreatorFactories.fromProjectProperties(
                                    getProject(), getDebugBuild()),
                            ImmutableSet.of(),
                            getJniDebugBuild())) {
                packager.updateDex(changedDex);
                packager.updateJavaResources(changedJavaResources);
                packager.updateAssets(changedAssets);
                packager.updateAndroidResources(changedAndroidResources);
                packager.updateNativeLibraries(changedNLibs);
            }
        } catch (PackagerException | KeytoolException e) {
            throw new RuntimeException(e);
        }

        // Save all used zips in the cache.
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
                .forEach(
                        (File f) -> {
                            try {
                                cacheByPath.add(f);
                            } catch (IOException e) {
                                throw new IOExceptionWrapper(e);
                            }
                        });
    }

    private Predicate<String> getNoCompressPredicate(File manifestFile) {
        return PackagingUtils.getNoCompressPredicate(aaptOptions, manifestFile);
    }

    @InputFiles
    @NonNull
    public Collection<File> getPackageOutputFilesCollection() {
        return atomConfigTask.getPackageOutputFilesCollection();
    }

    @OutputFiles
    @NonNull
    public Collection<File> getFinalOutputFilesCollections() {
        return atomConfigTask.getFinalOutputFilesCollection();
    }

    @InputFiles
    @Optional
    @NonNull
    public FileCollection getJavaResourceDirsCollection() {
        return atomJavaResDirs.getArtifactFiles();
    }

    @InputFiles
    @Optional
    @NonNull
    public FileCollection getJniDirsCollection() {
        return atomJniDirs.getArtifactFiles();
    }

    @InputFiles
    @Optional
    @NonNull
    public Collection<File> getFinalDexDirsCollection() {
        return atomConfigTask.getFinalDexDirsCollection();
    }

    @InputFiles
    @Optional
    @NonNull
    public FileCollection getAssetDirsCollection() {
        return atomAssetDirs.getArtifactFiles();
    }

    @InputFiles
    @NonNull
    public FileCollection getManifestFiles() {
        return atomManifests.getArtifactFiles();
    }

    public void setAaptOptions(AaptOptions aaptOptions) {
        this.aaptOptions = aaptOptions;
    }

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

    /*
     * We don't really use this. But this forces a full build if the native packaging mode changes.
     */
    @Input
    public List<String> getNativeLibrariesPackagingModeName() {
        return getManifestFiles()
                .getFiles()
                .stream()
                .map(
                        file ->
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(file)
                                        .toString())
                .collect(Collectors.toList());
    }

    @Input
    public Collection<String> getNoCompressExtensions() {
        return MoreObjects.firstNonNull(
                aaptOptions.getNoCompress(),
                Collections.<String>emptyList());
    }

    /** Zip caches to allow incremental updates. */
    private FileCacheByPath cacheByPath;

    private boolean debugBuild;
    private boolean jniDebugBuild;
    private CoreSigningConfig signingConfig;
    private PackagingOptions packagingOptions;
    private ApiVersion minSdkVersion;
    private AaptOptions aaptOptions;
    private ArtifactCollection atomManifests;
    private ArtifactCollection atomJavaResDirs;
    private ArtifactCollection atomJniDirs;
    private ArtifactCollection atomAssetDirs;
    private AtomConfig atomConfigTask;

    public static class ConfigAction implements TaskConfigAction<PackageAtom> {

        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("packageAll", "Atoms");
        }

        @NonNull
        @Override
        public Class<PackageAtom> getType() {
            return PackageAtom.class;
        }

        @Override
        public void execute(@NonNull PackageAtom packageAtom) {
            GlobalScope globalScope = scope.getGlobalScope();
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            packageAtom.setAndroidBuilder(globalScope.getAndroidBuilder());
            packageAtom.setVariantName(scope.getFullVariantName());
            packageAtom.setIncrementalFolder(scope.getIncrementalDir(packageAtom.getName()));

            File cacheByPathDir = new File(packageAtom.getIncrementalFolder(), ZIP_DIFF_CACHE_DIR);
            FileUtils.mkdirs(cacheByPathDir);
            packageAtom.cacheByPath = new FileCacheByPath(cacheByPathDir);

            packageAtom.debugBuild = config.getBuildType().isDebuggable();
            packageAtom.jniDebugBuild = config.getBuildType().isJniDebuggable();
            packageAtom.setSigningConfig(config.getSigningConfig());
            packageAtom.packagingOptions = globalScope.getExtension().getPackagingOptions();
            packageAtom.setMinSdkVersion(scope.getMinSdkVersion());
            packageAtom.aaptOptions = globalScope.getExtension().getAaptOptions();

            packageAtom.atomManifests =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_MANIFEST);
            packageAtom.atomJavaResDirs =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_JAVA_RES);
            packageAtom.atomJniDirs =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_JNI);
            packageAtom.atomAssetDirs =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_ASSETS);
            packageAtom.atomConfigTask = scope.getVariantData().atomConfigTask;
        }
    }
}
