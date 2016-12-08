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

import com.android.annotations.NonNull;
import com.android.apkzlib.utils.IOExceptionWrapper;
import com.android.apkzlib.zfile.ApkCreatorFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData.InputSet;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;

/** Task to package all the Android atoms. */
public class PackageAtom extends IncrementalTask {

    /** Name of directory, inside the intermediate directory, where zip caches are kept. */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";

    /** Zip caches to allow incremental updates. */
    private FileCacheByPath cacheByPath;

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        // Clear the cache to make sure we do not do an incremental build.
        cacheByPath.clear();

        for (String atomName : getFlatAtomList()) {
            // Also clear the intermediate build directory. We don't know if anything is in there and
            // since this is a full build, we don't want to get any interference from previous state.
            File incrementalFolder = new File(getIncrementalFolder(), atomName);
            FileUtils.mkdirs(incrementalFolder);
            FileUtils.deleteDirectoryContents(incrementalFolder);

            // Additionally, make sure we have no previous package, if it exists.
            getOutputFiles().get(atomName).delete();

            ImmutableMap<RelativeFile, FileStatus> updatedDex =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getDexFolders().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> updatedJavaResources =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getJavaResourceFiles().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getAssetFolders().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getResourceFiles().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> updatedJniResources =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getJniFolders().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> updatedAtomMetadata =
                    IncrementalRelativeFileSets.fromDirectory(
                            getAtomMetadataFolders().get(atomName));

            doTask(
                    atomName,
                    updatedDex,
                    updatedJavaResources,
                    updatedAssets,
                    updatedAndroidResources,
                    updatedJniResources,
                    updatedAtomMetadata);

            // Update the known files.
            KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalFolder);
            saveData.setInputSet(updatedDex.keySet(), InputSet.DEX);
            saveData.setInputSet(updatedJavaResources.keySet(), InputSet.JAVA_RESOURCE);
            saveData.setInputSet(updatedAssets.keySet(), InputSet.ASSET);
            saveData.setInputSet(updatedAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
            saveData.setInputSet(updatedJniResources.keySet(), InputSet.NATIVE_RESOURCE);
            saveData.setInputSet(updatedAtomMetadata.keySet(), InputSet.ATOM_METADATA);
            saveData.saveCurrentData();
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        checkNotNull(changedInputs, "changedInputs == null");

        for (String atomName : getFlatAtomList()) {
            File incrementalFolder = new File(getIncrementalFolder(), atomName);
            KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalFolder);

            Set<Runnable> cacheUpdates = new HashSet<>();
            ImmutableMap<RelativeFile, FileStatus> changedDexFiles =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            InputSet.DEX,
                            Collections.singleton(getDexFolders().get(atomName)),
                            cacheByPath,
                            cacheUpdates);

            ImmutableMap<RelativeFile, FileStatus> changedJavaResources =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            InputSet.JAVA_RESOURCE,
                            Collections.singleton(getJavaResourceFiles().get(atomName)),
                            cacheByPath,
                            cacheUpdates);

            ImmutableMap<RelativeFile, FileStatus> changedAssets =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            InputSet.ASSET,
                            Collections.singleton(getAssetFolders().get(atomName)),
                            cacheByPath,
                            cacheUpdates);

            ImmutableMap<RelativeFile, FileStatus> changedAndroidResources =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            InputSet.ANDROID_RESOURCE,
                            Collections.singleton(getResourceFiles().get(atomName)),
                            cacheByPath,
                            cacheUpdates);

            ImmutableMap<RelativeFile, FileStatus> changedNLibs =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            InputSet.NATIVE_RESOURCE,
                            Collections.singleton(getJniFolders().get(atomName)),
                            cacheByPath,
                            cacheUpdates);

            ImmutableMap<RelativeFile, FileStatus> changedAtomMetadata =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            InputSet.ATOM_METADATA,
                            Collections.singleton(getAtomMetadataFolders().get(atomName)),
                            cacheByPath,
                            cacheUpdates);

            doTask(
                    atomName,
                    changedDexFiles,
                    changedJavaResources,
                    changedAssets,
                    changedAndroidResources,
                    changedNLibs,
                    changedAtomMetadata);

            // Update the cache
            cacheUpdates.forEach(Runnable::run);

            // Update the save data keep files.
            ImmutableMap<RelativeFile, FileStatus> allDex =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getDexFolders().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> allJavaResources =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getJavaResourceFiles().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> allAndroidResources =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getResourceFiles().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> allJniResources =
                    IncrementalRelativeFileSets.fromZipsAndDirectories(
                            Collections.singleton(getJniFolders().get(atomName)));
            ImmutableMap<RelativeFile, FileStatus> allAtomMetadataFiles =
                    IncrementalRelativeFileSets.fromDirectory(
                            getAtomMetadataFolders().get(atomName));

            saveData.setInputSet(allDex.keySet(), InputSet.DEX);
            saveData.setInputSet(allJavaResources.keySet(), InputSet.JAVA_RESOURCE);
            saveData.setInputSet(allAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
            saveData.setInputSet(allJniResources.keySet(), InputSet.NATIVE_RESOURCE);
            saveData.setInputSet(allAtomMetadataFiles.keySet(), InputSet.ATOM_METADATA);
            saveData.saveCurrentData();
        }
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
     * @param changedAtomMetadata incremental atom metadata changed
     * @throws IOException failed to package the APK
     */
    private void doTask(
            @NonNull String atomName,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAtomMetadata)
            throws IOException {
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
                            getOutputFiles().get(atomName),
                            key,
                            certificate,
                            v1SigningEnabled,
                            v2SigningEnabled,
                            null, // BuiltBy
                            getBuilder().getCreatedBy(),
                            getMinSdkVersion(),
                            PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                    getManifestFiles().get(atomName)),
                            getNoCompressPredicate(atomName));

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
                            new File(getIncrementalFolder(), atomName),
                            ApkCreatorFactories.fromProjectProperties(
                                    getProject(), getDebugBuild()),
                            ImmutableSet.of(),
                            getJniDebugBuild())) {
                packager.updateDex(changedDex);
                packager.updateJavaResources(changedJavaResources);
                packager.updateAssets(changedAssets);
                packager.updateAndroidResources(changedAndroidResources);
                packager.updateNativeLibraries(changedNLibs);
                packager.updateAtomMetadata(changedAtomMetadata);
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

    private Predicate<String> getNoCompressPredicate(String atomName) {
        return PackagingUtils.getNoCompressPredicate(aaptOptions, getManifestFiles().get(atomName));
    }

    @SuppressWarnings("unused")
    @InputFiles
    @NonNull
    public Collection<File> getResourceFileCollections() {
        return getResourceFiles().values();
    }

    @Input
    @NonNull
    public Map<String, File> getResourceFiles() {
        return resourceFiles;
    }

    public void setResourceFiles(@NonNull Map<String, File> resourceFiles) {
        this.resourceFiles = resourceFiles;
    }

    @SuppressWarnings("unused")
    @OutputFiles
    @NonNull
    public Collection<File> getOutputFileCollections() {
        return getOutputFiles().values();
    }

    @Input
    @NonNull
    public Map<String, File> getOutputFiles() {
        return outputFiles;
    }

    public void setOutputFiles(@NonNull Map<String, File> outputFiles) {
        this.outputFiles = outputFiles;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    @NonNull
    public Collection<File> getJavaResoruceFilesCollection() {
        return getJavaResourceFiles().values();
    }

    @Input
    @Optional
    @NonNull
    public Map<String, File> getJavaResourceFiles() {
        return javaResourceFiles;
    }

    public void setJavaResourceFiles(@NonNull Map<String, File> javaResourceFiles) {
        this.javaResourceFiles = javaResourceFiles;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    @NonNull
    public Collection<File> getJniFoldersCollection() {
        return getJniFolders().values();
    }

    @Input
    @Optional
    @NonNull
    public Map<String, File> getJniFolders() {
        return jniFolders;
    }

    public void setJniFolders(@NonNull Map<String, File> jniFolders) {
        this.jniFolders = jniFolders;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    @NonNull
    public Collection<File> getDexFoldersCollection() {
        return getDexFolders().values();
    }

    @Input
    @Optional
    @NonNull
    public Map<String, File> getDexFolders() {
        return dexFolders;
    }

    public void setDexFolders(@NonNull Map<String, File> dexFolders) {
        this.dexFolders = dexFolders;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    @NonNull
    public Collection<File> getAssetFoldersCollection() {
        return getAssetFolders().values();
    }

    @Input
    @Optional
    @NonNull
    public Map<String, File> getAssetFolders() {
        return assetFolders;
    }

    public void setAssetFolders(@NonNull Map<String, File> assetFolders) {
        this.assetFolders = assetFolders;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @NonNull
    public Collection<File> getAtomMetadataFolderCollection() {
        return getAtomMetadataFolders().values();
    }

    @Input
    @NonNull
    public Map<String, File> getAtomMetadataFolders() {
        return atomMetadataFolders;
    }

    public void setAtomMetadataFolders(@NonNull Map<String, File> atomMetadataFolders) {
        this.atomMetadataFolders = atomMetadataFolders;
    }

    @Input
    @NonNull
    public Map<String, File> getManifestFiles() {
        return manifestFiles;
    }

    public void setManifestFiles(Map<String, File> manifestFiles) {
        this.manifestFiles = manifestFiles;
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
    @SuppressWarnings("unused")
    @Input
    public List<String> getNativeLibrariesPackagingModeName() {
        return getManifestFiles()
                .values()
                .stream()
                .map(
                        file ->
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(file)
                                        .toString())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    @Input
    public Collection<String> getNoCompressExtensions() {
        return MoreObjects.firstNonNull(aaptOptions.getNoCompress(), Collections.emptyList());
    }

    @Input
    public List<String> getFlatAtomList() {
        return flatAtomList.get();
    }

    public void setFlatAtomList(@NonNull Supplier<List<String>> flatAtomList) {
        this.flatAtomList = flatAtomList;
    }

    private Map<String, File> resourceFiles;
    private Map<String, File> outputFiles;
    private Map<String, File> javaResourceFiles;
    private Map<String, File> jniFolders;
    private Map<String, File> dexFolders;
    private Map<String, File> assetFolders;
    private Map<String, File> atomMetadataFolders;
    private Map<String, File> manifestFiles;
    private boolean debugBuild;
    private boolean jniDebugBuild;
    private CoreSigningConfig signingConfig;
    private PackagingOptions packagingOptions;
    private ApiVersion minSdkVersion;
    private AaptOptions aaptOptions;
    private Supplier<List<String>> flatAtomList;

    public static class ConfigAction implements TaskConfigAction<PackageAtom> {

        private final VariantOutputScope scope;

        public ConfigAction(VariantOutputScope scope) {
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
            VariantScope variantScope = scope.getVariantScope();
            GlobalScope globalScope = variantScope.getGlobalScope();
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    variantScope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            packageAtom.setAndroidBuilder(globalScope.getAndroidBuilder());
            packageAtom.setVariantName(scope.getFullVariantName());
            packageAtom.setIncrementalFolder(variantScope.getIncrementalDir(packageAtom.getName()));

            File cacheByPathDir = new File(packageAtom.getIncrementalFolder(), ZIP_DIFF_CACHE_DIR);
            FileUtils.mkdirs(cacheByPathDir);
            packageAtom.cacheByPath = new FileCacheByPath(cacheByPathDir);

            ConventionMappingHelper.map(
                    packageAtom,
                    "resourceFiles",
                    () -> {
                        Map<String, File> resourceFiles = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            resourceFiles.put(
                                    atom.getAtomName(),
                                    scope.getProcessResourcePackageOutputFile(atom));
                        }
                        return resourceFiles;
                    });
            ConventionMappingHelper.map(
                    packageAtom,
                    "outputFiles",
                    () -> {
                        Map<String, File> outputFiles = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            outputFiles.put(atom.getAtomName(), variantScope.getPackageAtom(atom));
                        }
                        return outputFiles;
                    });
            ConventionMappingHelper.map(
                    packageAtom,
                    "javaResourceFiles",
                    () -> {
                        Map<String, File> javaResourceFiles = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            javaResourceFiles.put(atom.getAtomName(), atom.getJavaResFolder());
                        }
                        return javaResourceFiles;
                    });
            ConventionMappingHelper.map(
                    packageAtom,
                    "jniFolders",
                    () -> {
                        Map<String, File> jniFolders = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            jniFolders.put(atom.getAtomName(), atom.getLibFolder());
                        }
                        return jniFolders;
                    });
            ConventionMappingHelper.map(
                    packageAtom,
                    "dexFolders",
                    () -> {
                        Map<String, File> dexFolders = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            dexFolders.put(
                                    atom.getAtomName(), variantScope.getDexOutputFolder(atom));
                        }
                        return dexFolders;
                    });
            ConventionMappingHelper.map(
                    packageAtom,
                    "assetFolders",
                    () -> {
                        Map<String, File> assetFolders = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            assetFolders.put(atom.getAtomName(), atom.getAssetsFolder());
                        }
                        return assetFolders;
                    });
            ConventionMappingHelper.map(
                    packageAtom,
                    "atomMetadataFolders",
                    () -> {
                        Map<String, File> atomMetadataFolders = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            atomMetadataFolders.put(
                                    atom.getAtomName(), atom.getAtomMetadataFile().getParentFile());
                        }
                        return atomMetadataFolders;
                    });
            ConventionMappingHelper.map(
                    packageAtom,
                    "manifestFiles",
                    () -> {
                        Map<String, File> manifestFiles = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            manifestFiles.put(atom.getAtomName(), atom.getManifest());
                        }
                        return manifestFiles;
                    });

            ConventionMappingHelper.map(
                    packageAtom, "debugBuild", config.getBuildType()::isDebuggable);
            ConventionMappingHelper.map(
                    packageAtom, "jniDebugBuild", config.getBuildType()::isJniDebuggable);
            packageAtom.setSigningConfig(config.getSigningConfig());
            ConventionMappingHelper.map(
                    packageAtom,
                    "packagingOptions",
                    globalScope.getExtension()::getPackagingOptions);
            packageAtom.setMinSdkVersion(variantScope.getMinSdkVersion());
            packageAtom.aaptOptions = globalScope.getExtension().getAaptOptions();

            packageAtom.setFlatAtomList(
                    () ->
                            config.getFlatAndroidAtomsDependencies()
                                    .stream()
                                    .map(AtomDependency::getAtomName)
                                    .collect(Collectors.toList()));
        }
    }
}
