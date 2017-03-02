/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.DOT_RES;
import static com.android.SdkConstants.FD_ATOMS;
import static com.android.SdkConstants.FD_BLAME;
import static com.android.SdkConstants.FD_CLASSES_OUTPUT;
import static com.android.SdkConstants.FD_DEX;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_CLASS;
import static com.android.SdkConstants.FD_SOURCE_GEN;
import static com.android.SdkConstants.FD_SYMBOLS;
import static com.android.SdkConstants.FN_RES_BASE;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_ANDROID_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_ASSETS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_DEX;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_JAVA_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_LIB_INFO;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_RESOURCE_PKG;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.tasks.Input;

/** Configuration task for all atom packaging-related tasks. */
public class AtomConfig extends BaseTask {

    @NonNull
    public Collection<File> getSourceOutputDirsCollection() {
        return sourceOutputDirsCollection;
    }

    @NonNull
    public Collection<File> getTextSymbolOutputDirsCollection() {
        return textSymbolOutputDirsCollection;
    }

    @NonNull
    public Collection<File> getPackageOutputFilesCollection() {
        return packageOutputFilesCollection;
    }

    @NonNull
    public Collection<File> getMergeBlameLogDirsCollection() {
        return mergeBlameLogDirsCollection;
    }

    @NonNull
    public Collection<File> getJavaClassDirsCollection() {
        return javaClassDirsCollection;
    }

    @NonNull
    public Collection<File> getDexTempDirsCollection() {
        return dexTempDirsCollection;
    }

    @NonNull
    public Collection<File> getFinalDexDirsCollection() {
        return finalDexDirsCollection;
    }

    @NonNull
    public Collection<File> getFinalOutputFilesCollection() {
        return finalOutputFilesCollection;
    }

    @Input
    @NonNull
    public Collection<AtomInfo> getAtomInfoCollection() {
        if (computedAtomInfo == null) {
            Map<ComponentIdentifier, File> resPackageMap = new HashMap<>();
            for (ResolvedArtifactResult artifactResult : atomResourcePackages.getArtifacts()) {
                resPackageMap.put(
                        artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
            }

            Map<ComponentIdentifier, File> androidResMap = new HashMap<>();
            for (ResolvedArtifactResult artifactResult : atomAndroidRes.getArtifacts()) {
                androidResMap.put(
                        artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
            }

            Map<ComponentIdentifier, File> dexMap = new HashMap<>();
            for (ResolvedArtifactResult artifactResult : atomDexDirs.getArtifacts()) {
                dexMap.put(
                        artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
            }

            Map<ComponentIdentifier, File> javaResMap = new HashMap<>();
            for (ResolvedArtifactResult artifactResult : atomJavaRes.getArtifacts()) {
                javaResMap.put(
                        artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
            }

            Map<ComponentIdentifier, File> jniDirMap = new HashMap<>();
            for (ResolvedArtifactResult artifactResult : atomJniDirs.getArtifacts()) {
                jniDirMap.put(
                        artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
            }

            Map<ComponentIdentifier, File> assetDirMap = new HashMap<>();
            for (ResolvedArtifactResult artifactResult : atomAssetDirs.getArtifacts()) {
                assetDirMap.put(
                        artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
            }

            Map<ComponentIdentifier, File> libInfoMap = new HashMap<>();
            for (ResolvedArtifactResult artifactResult : atomLibInfoFiles.getArtifacts()) {
                libInfoMap.put(
                        artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
            }

            Set<ResolvedArtifactResult> atomManifestArtifacts = atomManifests.getArtifacts();
            computedAtomInfo = new ArrayList<>(atomManifestArtifacts.size());

            ImmutableList.Builder<File> sourceOutputDirsBuilder = ImmutableList.builder();
            ImmutableList.Builder<File> textSymbolOutputDirsBuilder = ImmutableList.builder();
            ImmutableList.Builder<File> packageOutputFilesBuilder = ImmutableList.builder();
            ImmutableList.Builder<File> mergeBlameLogDirsBuilder = ImmutableList.builder();
            ImmutableList.Builder<File> javaClassDirsBuilder = ImmutableList.builder();
            ImmutableList.Builder<File> dexTempDirsBuilder = ImmutableList.builder();
            ImmutableList.Builder<File> finalDexDirsBuilder = ImmutableList.builder();
            ImmutableList.Builder<File> finalOutputFilesBuilder = ImmutableList.builder();

            for (ResolvedArtifactResult artifactResult : atomManifestArtifacts) {
                ComponentIdentifier componentIdentifier =
                        artifactResult.getId().getComponentIdentifier();
                String atomName = componentIdentifier.getDisplayName();
                atomName = atomName.substring(atomName.indexOf(':') + 1);

                File sourceOutputDir = getSourceOutputDir(atomName);
                File textSymbolOutputDir = getTextSymbolOutputDir(atomName);
                File packageOutputFile = getPackageOutputFile(atomName);
                File mergeBlameLogDir = getMergeBlameLogDir(atomName);
                File javaClassDir = getJavaClassDir(atomName);
                File dexTempDir = getDexTempDir(atomName);
                File finalDexDir = getFinalDexDir(atomName);
                File finalOutputFile = getFinalOutputFile(atomName);

                computedAtomInfo.add(
                        new AtomInfo(
                                atomName,
                                getManifestFile(artifactResult.getFile()),
                                getPackageFile(resPackageMap.get(componentIdentifier)),
                                androidResMap.get(componentIdentifier),
                                dexMap.get(componentIdentifier),
                                javaResMap.get(componentIdentifier),
                                jniDirMap.get(componentIdentifier),
                                assetDirMap.get(componentIdentifier),
                                libInfoMap.get(componentIdentifier),
                                sourceOutputDir,
                                textSymbolOutputDir,
                                packageOutputFile,
                                mergeBlameLogDir,
                                javaClassDir,
                                dexTempDir,
                                finalDexDir,
                                finalOutputFile));

                sourceOutputDirsBuilder.add(sourceOutputDir);
                textSymbolOutputDirsBuilder.add(textSymbolOutputDir);
                packageOutputFilesBuilder.add(packageOutputFile);
                mergeBlameLogDirsBuilder.add(mergeBlameLogDir);
                javaClassDirsBuilder.add(javaClassDir);
                dexTempDirsBuilder.add(dexTempDir);
                finalDexDirsBuilder.add(finalDexDir);
                finalOutputFilesBuilder.add(finalOutputFile);
            }

            // Initialize all the lists here so it is only done once.
            sourceOutputDirsCollection = sourceOutputDirsBuilder.build();
            textSymbolOutputDirsCollection = textSymbolOutputDirsBuilder.build();
            packageOutputFilesCollection = packageOutputFilesBuilder.build();
            mergeBlameLogDirsCollection = mergeBlameLogDirsBuilder.build();
            javaClassDirsCollection = javaClassDirsBuilder.build();
            dexTempDirsCollection = dexTempDirsBuilder.build();
            finalDexDirsCollection = finalDexDirsBuilder.build();
            finalOutputFilesCollection = finalOutputFilesBuilder.build();
        }
        return computedAtomInfo;
    }

    private ArtifactCollection atomManifests;
    private ArtifactCollection atomResourcePackages;
    private ArtifactCollection atomAndroidRes;
    private ArtifactCollection atomDexDirs;
    private ArtifactCollection atomJavaRes;
    private ArtifactCollection atomJniDirs;
    private ArtifactCollection atomAssetDirs;
    private ArtifactCollection atomLibInfoFiles;
    private VariantScope variantScope;

    // So we don't have to compute them twice.
    private Collection<File> sourceOutputDirsCollection;
    private Collection<File> textSymbolOutputDirsCollection;
    private Collection<File> packageOutputFilesCollection;
    private Collection<File> mergeBlameLogDirsCollection;
    private Collection<File> javaClassDirsCollection;
    private Collection<File> dexTempDirsCollection;
    private Collection<File> finalDexDirsCollection;
    private Collection<File> finalOutputFilesCollection;

    private Collection<AtomInfo> computedAtomInfo;

    @NonNull
    private static File getManifestFile(@NonNull File manifestDir) {
        // FIXME: Remove this once we support config splits.
        Collection<BuildOutput> splitOutputs =
                BuildOutputs.load(VariantScope.TaskOutputType.MERGED_MANIFESTS, manifestDir);
        if (splitOutputs.isEmpty()) {
            throw new GradleException("Could not load manifest from " + manifestDir);
        }
        return splitOutputs.iterator().next().getOutputFile();
    }

    @Nullable
    private static File getPackageFile(@NonNull File packageDir) {
        // FIXME: Remove this once we support config splits.
        Collection<BuildOutput> splitOutputs =
                BuildOutputs.load(VariantScope.TaskOutputType.PROCESSED_RES, packageDir);
        if (splitOutputs.isEmpty()) {
            return null;
        }
        return splitOutputs.iterator().next().getOutputFile();
    }

    @NonNull
    private File getSourceOutputDir(String atomName) {
        return FileUtils.join(
                variantScope.getGlobalScope().getGeneratedDir(),
                FD_SOURCE_GEN,
                FD_RES_CLASS,
                atomName + RES_QUALIFIER_SEP + variantScope.getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getTextSymbolOutputDir(String atomName) {
        return FileUtils.join(
                variantScope.getGlobalScope().getIntermediatesDir(),
                FD_SYMBOLS,
                atomName,
                variantScope.getVariantData().getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getPackageOutputFile(String atomName) {
        return FileUtils.join(
                variantScope.getGlobalScope().getIntermediatesDir(),
                FD_RES,
                FN_RES_BASE
                        + RES_QUALIFIER_SEP
                        + atomName
                        + RES_QUALIFIER_SEP
                        + variantScope.getVariantConfiguration().getBaseName()
                        + DOT_RES);
    }

    @NonNull
    private File getMergeBlameLogDir(String atomName) {
        return FileUtils.join(
                variantScope.getGlobalScope().getIntermediatesDir(),
                StringHelper.toStrings(
                        FD_BLAME,
                        FD_RES,
                        atomName,
                        variantScope.getVariantConfiguration().getDirectorySegments()));
    }

    @NonNull
    private File getJavaClassDir(String atomName) {
        return FileUtils.join(
                variantScope.getGlobalScope().getIntermediatesDir(),
                FD_CLASSES_OUTPUT,
                atomName + RES_QUALIFIER_SEP + variantScope.getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getDexTempDir(String atomName) {
        return variantScope.getIncrementalDir(
                atomName + RES_QUALIFIER_SEP + variantScope.getFullVariantName());
    }

    @NonNull
    private File getFinalDexDir(String atomName) {
        return FileUtils.join(
                variantScope.getGlobalScope().getIntermediatesDir(),
                FD_DEX,
                atomName + RES_QUALIFIER_SEP + variantScope.getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getFinalOutputFile(String atomName) {
        return FileUtils.join(
                variantScope.getGlobalScope().getIntermediatesDir(),
                FD_ATOMS,
                variantScope.getVariantConfiguration().getDirName(),
                atomName + DOT_ANDROID_PACKAGE);
    }

    // Struct containing all necessary information for one atom.
    public static class AtomInfo {

        public AtomInfo(
                @NonNull String name,
                @NonNull File manifestFile,
                @Nullable File resourcePackageFile,
                @NonNull File resourceDir,
                @NonNull File atomDexDir,
                @NonNull File javaResourcesDir,
                @NonNull File jniDir,
                @NonNull File assetDir,
                @NonNull File libInfoFile,
                @NonNull File sourceOutputDir,
                @NonNull File textSymbolOutputDir,
                @NonNull File packageOutputFile,
                @NonNull File mergeBlameLogDir,
                @NonNull File javaClassDir,
                @NonNull File dexTempDir,
                @NonNull File finalDexDir,
                @NonNull File finalOutputFile) {
            this.name = name;
            this.manifestFile = manifestFile;
            this.resourcePackageFile = resourcePackageFile;
            this.resourceDir = resourceDir;
            this.atomDexDir = atomDexDir;
            this.javaResourcesDir = javaResourcesDir;
            this.jniDir = jniDir;
            this.assetDir = assetDir;
            this.libInfoFile = libInfoFile;
            this.sourceOutputDir = sourceOutputDir;
            this.textSymbolOutputDir = textSymbolOutputDir;
            this.packageOutputFile = packageOutputFile;
            this.mergeBlameLogDir = mergeBlameLogDir;
            this.javaClassDir = javaClassDir;
            this.dexTempDir = dexTempDir;
            this.finalDexDir = finalDexDir;
            this.finalOutputFile = finalOutputFile;
            this.hashCode = computeHashCode();
        }

        public String getName() {
            return name;
        }

        @Nullable
        public File getResourcePackageFile() {
            return resourcePackageFile;
        }

        @NonNull
        public File getManifestFile() {
            return manifestFile;
        }

        @NonNull
        public File getResourceDir() {
            return resourceDir;
        }

        @NonNull
        public File getAtomDexDir() {
            return atomDexDir;
        }

        @NonNull
        public File getJavaResourcesDir() {
            return javaResourcesDir;
        }

        @NonNull
        public File getJniDir() {
            return jniDir;
        }

        @NonNull
        public File getAssetDir() {
            return assetDir;
        }

        @NonNull
        public File getLibInfoFile() {
            return libInfoFile;
        }

        @NonNull
        public File getSourceOutputDir() {
            return sourceOutputDir;
        }

        @NonNull
        public File getTextSymbolOutputDir() {
            return textSymbolOutputDir;
        }

        @NonNull
        public File getPackageOutputFile() {
            return packageOutputFile;
        }

        @NonNull
        public File getMergeBlameLogDir() {
            return mergeBlameLogDir;
        }

        @NonNull
        public File getJavaClassDir() {
            return javaClassDir;
        }

        @NonNull
        public File getDexTempDir() {
            return dexTempDir;
        }

        @NonNull
        public File getFinalDexDir() {
            return finalDexDir;
        }

        @NonNull
        public File getFinalOutputFile() {
            return finalOutputFile;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("manifestFile", manifestFile)
                    .add("resourcePackageFile", resourcePackageFile)
                    .add("resourceDir", resourceDir)
                    .add("atomDexDir", atomDexDir)
                    .add("javaResourcesDir", javaResourcesDir)
                    .add("jniDir", jniDir)
                    .add("assetDir", assetDir)
                    .add("libInfoFile", libInfoFile)
                    .add("sourceOutputDir", sourceOutputDir)
                    .add("textSymbolOutputDir", textSymbolOutputDir)
                    .add("packageOutputFile", packageOutputFile)
                    .add("mergeBlameLogDir", mergeBlameLogDir)
                    .add("javaClassDir", javaClassDir)
                    .add("dexTempDir", dexTempDir)
                    .add("finalDexDir", finalDexDir)
                    .add("finalOutputFile", finalOutputFile)
                    .toString();
        }

        private int computeHashCode() {
            return Objects.hashCode(
                    name,
                    manifestFile,
                    resourcePackageFile,
                    resourceDir,
                    atomDexDir,
                    javaResourcesDir,
                    jniDir,
                    assetDir,
                    libInfoFile,
                    sourceOutputDir,
                    textSymbolOutputDir,
                    packageOutputFile,
                    mergeBlameLogDir,
                    javaClassDir,
                    dexTempDir,
                    finalDexDir,
                    finalOutputFile);
        }

        private final String name;

        private final File manifestFile;
        private final File resourcePackageFile;
        private final File resourceDir;
        private final File atomDexDir;
        private final File javaResourcesDir;
        private final File jniDir;
        private final File assetDir;
        private final File libInfoFile;

        private final File sourceOutputDir;
        private final File textSymbolOutputDir;
        private final File packageOutputFile;
        private final File mergeBlameLogDir;
        private final File javaClassDir;
        private final File dexTempDir;
        private final File finalDexDir;
        private final File finalOutputFile;

        private final int hashCode;
    }

    public static class ConfigAction implements TaskConfigAction<AtomConfig> {
        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("configure", "Atoms");
        }

        @NonNull
        @Override
        public Class<AtomConfig> getType() {
            return AtomConfig.class;
        }

        @Override
        public void execute(@NonNull AtomConfig atomConfig) {
            final GlobalScope globalScope = scope.getGlobalScope();
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            variantData.atomConfigTask = atomConfig;

            atomConfig.setAndroidBuilder(globalScope.getAndroidBuilder());
            atomConfig.setVariantName(config.getFullName());

            atomConfig.atomManifests =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_MANIFEST);
            atomConfig.atomResourcePackages =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_RESOURCE_PKG);
            atomConfig.atomAndroidRes =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_ANDROID_RES);
            atomConfig.atomDexDirs =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_DEX);
            atomConfig.atomJavaRes =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_JAVA_RES);
            atomConfig.atomJniDirs =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_JNI);
            atomConfig.atomAssetDirs =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_ASSETS);
            atomConfig.atomLibInfoFiles =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_LIB_INFO);
            atomConfig.variantScope = scope;
        }
    }
}
