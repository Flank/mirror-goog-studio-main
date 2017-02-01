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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.SYMBOL_LIST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConfigType.COMPILE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConfigType.RUNTIME;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.LoggingUtil;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.incremental.BuildContext;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.SplitList;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptPackageConfig.LibraryInfo;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;

@ParallelizableTask
public class ProcessAndroidResources extends IncrementalTask {

    private static final Logger LOG = Logging.getLogger(ProcessAndroidResources.class);

    private File manifestFile;

    private File instantRunManifestFile;

    private File resDir;

    @Nullable
    private File sourceOutputDir;

    private Supplier<File> textSymbolOutputDir = () -> null;

    private Supplier<File> packageOutputFile = () -> null;

    private File proguardOutputFile;

    private File mainDexListProguardOutputFile;

    private Collection<String> resourceConfigs;

    private String preferredDensity;

    @Nullable
    private ArtifactCollection manifests;
    @Nullable
    private ArtifactCollection symbolFiles;
    // FIXME find a better way to inject the tested library's content into the main ArtifactCollection
    @Nullable
    private FileCollection testedManifest;
    @Nullable
    private FileCollection testedSymbolFile;

    private String packageForR;

    private SplitList splitList;
    private SplitHandlingPolicy splitHandlingPolicy;

    private boolean enforceUniquePackageName;

    private VariantType type;

    private boolean debuggable;

    private boolean pseudoLocalesEnabled;

    private AaptOptions aaptOptions;

    private File mergeBlameLogFolder;

    private BuildContext buildContext;

    private FileCollection atomResourcePackages;

    private List<LibraryInfo> computedLibraryInfo;

    public Set<String> getSplits() throws IOException {
        return splitHandlingPolicy == SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY
                ? splitList.getResourcesSplit()
                : ImmutableSet.of();
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        // we have to clean the source folder output in case the package name changed.
        File srcOut = getSourceOutputDir();
        if (srcOut != null) {
            FileUtils.cleanOutputDir(srcOut);
        }

        // Find the base atom package, if it exists.
        @Nullable File baseAtomPackage = null;
        for (File atomPackage : atomResourcePackages) {
            if (atomPackage.exists()) {
                baseAtomPackage = atomPackage;
                break;
            }
        }

        // For atoms, the output package must only be set for the base atom.
        @Nullable File resOutBaseNameFile = null;
        if (getType() != VariantType.ATOM || baseAtomPackage == null) {
            resOutBaseNameFile = getPackageOutputFile();
        }

        // If are in instant run mode and we have an instant run enabled manifest
        File instantRunManifest = getInstantRunManifestFile();
        File manifestFileToPackage = buildContext.isInInstantRunMode() &&
                instantRunManifest != null && instantRunManifest.exists()
                    ? instantRunManifest
                    : getManifestFile();

        AndroidBuilder builder = getBuilder();
        MergingLog mergingLog = new MergingLog(getMergeBlameLogFolder());
        ProcessOutputHandler processOutputHandler = new ParsingProcessOutputHandler(
                new ToolOutputParser(new AaptOutputParser(), getILogger()),
                new MergingLogRewriter(mergingLog, builder.getErrorReporter()));

        try {
            Aapt aapt =
                    AaptGradleFactory.make(
                            builder,
                            processOutputHandler,
                            true,
                            getProject(),
                            FileUtils.mkdirs(new File(getIncrementalFolder(), "aapt-temp")),
                            aaptOptions.getCruncherProcesses());

            AaptPackageConfig.Builder config =
                    new AaptPackageConfig.Builder()
                            .setManifestFile(manifestFileToPackage)
                            .setOptions(getAaptOptions())
                            .setResourceDir(getResDir())
                            .setLibraries(getLibraryInfoList())
                            .setCustomPackageForR(getPackageForR())
                            .setSymbolOutputDir(getTextSymbolOutputDir())
                            .setSourceOutputDir(srcOut)
                            .setResourceOutputApk(resOutBaseNameFile)
                            .setProguardOutputFile(getProguardOutputFile())
                            .setMainDexListProguardOutputFile(getMainDexListProguardOutputFile())
                            .setVariantType(getType())
                            .setDebuggable(getDebuggable())
                            .setPseudoLocalize(getPseudoLocalesEnabled())
                            .setResourceConfigs(getResourceConfigs())
                            .setSplits(getSplits())
                            .setPreferredDensity(getPreferredDensity())
                            .setBaseFeature(baseAtomPackage);

            builder.processResources(aapt, config, getEnforceUniquePackageName());

            if (resOutBaseNameFile != null && LOG.isInfoEnabled()) {
                LOG.info("Aapt output file {}", resOutBaseNameFile.getAbsolutePath());
            }

        } catch (IOException | InterruptedException | ProcessException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public List<LibraryInfo> getLibraryInfoList() {
        if (computedLibraryInfo == null) {
            if (symbolFiles != null && manifests != null) {
                // first build a map for the optional symbols.
                Map<ComponentIdentifier, File> symbolMap = new HashMap<>();
                for (ResolvedArtifactResult artifactResult : symbolFiles.getArtifacts()) {
                    symbolMap.put(artifactResult.getId().getComponentIdentifier(),
                            artifactResult.getFile());
                }

                // now loop through all the manifests and associate to a symbol file, if applicable.
                Set<ResolvedArtifactResult> manifestArtifacts = manifests.getArtifacts();
                computedLibraryInfo = new ArrayList<>(manifestArtifacts.size());
                for (ResolvedArtifactResult artifactResult : manifestArtifacts) {
                    computedLibraryInfo.add(new LibraryInfo(
                            artifactResult.getFile(),
                            symbolMap.get(artifactResult.getId().getComponentIdentifier())));
                }

                // add the tested library if present
                if (testedManifest != null) {
                    File symbolFile = testedSymbolFile != null
                            ? testedSymbolFile.getSingleFile()
                            : null;
                    computedLibraryInfo.add(new LibraryInfo(
                            testedManifest.getSingleFile(),
                            symbolFile));
                }
                computedLibraryInfo = ImmutableList.copyOf(computedLibraryInfo);
            } else {
                computedLibraryInfo = ImmutableList.of();
            }
        }

        return computedLibraryInfo;
    }

    public static class ConfigAction implements TaskConfigAction<ProcessAndroidResources> {

        protected final VariantOutputScope scope;
        protected final Supplier<File> symbolLocation;
        private final Supplier<File> packageOutputSupplier;
        private final boolean generateLegacyMultidexMainDexProguardRules;

        public ConfigAction(
                @NonNull VariantOutputScope scope,
                @NonNull Supplier<File> symbolLocation,
                @NonNull Supplier<File> packageOutputSupplier,
                boolean generateLegacyMultidexMainDexProguardRules) {
            this.scope = scope;
            this.symbolLocation = symbolLocation;
            this.packageOutputSupplier = packageOutputSupplier;
            this.generateLegacyMultidexMainDexProguardRules = generateLegacyMultidexMainDexProguardRules;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "Resources");
        }

        @NonNull
        @Override
        public Class<ProcessAndroidResources> getType() {
            return ProcessAndroidResources.class;
        }

        @Override
        public void execute(@NonNull ProcessAndroidResources processResources) {
            final BaseVariantOutputData variantOutputData = scope.getVariantOutputData();
            final VariantScope variantScope = scope.getVariantScope();
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    variantScope.getVariantData();

            variantOutputData.processResourcesTask = processResources;
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            processResources.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processResources.setVariantName(config.getFullName());
            processResources.setIncrementalFolder(
                    variantScope.getIncrementalDir(getName()));

            processResources.splitList = scope.getVariantScope().getSplitList();
            processResources.splitHandlingPolicy = variantData.getSplitHandlingPolicy();

            // only generate code if the density filter is null, and if we haven't generated
            // it yet (if you have abi + density splits, then several abi output will have no
            // densityFilter)
            if (variantOutputData.getMainOutputFile()
                    .getFilter(OutputFile.DENSITY) == null
                    && variantData.generateRClassTask == null) {
                variantData.generateRClassTask = processResources;
                processResources.enforceUniquePackageName = scope.getGlobalScope().getExtension()
                        .getEnforceUniquePackageName();

                processResources.manifests = variantScope.getArtifactCollection(
                        RUNTIME, ALL, MANIFEST);

                processResources.symbolFiles = variantScope.getArtifactCollection(
                        RUNTIME, ALL, SYMBOL_LIST);

                processResources.testedManifest = variantScope.getTestedArtifact(
                        MANIFEST, VariantType.LIBRARY);
                processResources.testedSymbolFile = variantScope.getTestedArtifact(
                        SYMBOL_LIST, VariantType.LIBRARY);

                ConventionMappingHelper.map(processResources, "packageForR",
                        () -> {
                                String splitName = config.getSplitFromManifest();
                                if (splitName == null) {
                                    return config.getOriginalApplicationId();
                                } else {
                                    return config.getOriginalApplicationId() + "." + splitName;
                                }
                            }) ;

                // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
                processResources
                        .setSourceOutputDir(variantScope.getRClassSourceOutputDir());
                processResources.setTextSymbolOutputDir(symbolLocation);

                if (config.getBuildType().isMinifyEnabled()) {
                    if (config.getBuildType().isShrinkResources() && config.isJackEnabled()) {
                        LoggingUtil.displayWarning(Logging.getLogger(getClass()),
                                scope.getGlobalScope().getProject(),
                                "shrinkResources does not yet work with useJack=true");
                    }
                    processResources.setProguardOutputFile(
                            variantScope.getProcessAndroidResourcesProguardOutputFile());

                } else if (config.getBuildType().isShrinkResources()) {
                    LoggingUtil.displayWarning(Logging.getLogger(getClass()),
                            scope.getGlobalScope().getProject(),
                            "To shrink resources you must also enable ProGuard");
                }

                if (generateLegacyMultidexMainDexProguardRules) {
                    processResources.setAaptMainDexListProguardOutputFile(
                            variantScope.getManifestKeepListProguardFile());
                }
            }

            ConventionMappingHelper.map(
                    processResources,
                    "manifestFile",
                    () -> variantOutputData.manifestProcessorTask.getOutputFile());

            ConventionMappingHelper.map(
                    processResources,
                    "instantRunManifestFile",
                    () ->
                            variantOutputData.manifestProcessorTask
                                    .getInstantRunManifestOutputFile());

            ConventionMappingHelper.map(
                    processResources,
                    "resDir",
                    variantScope::getFinalResourcesDir);

            processResources.setPackageOutputFile(packageOutputSupplier);

            processResources.setType(config.getType());
            processResources.setDebuggable(config.getBuildType().isDebuggable());
            processResources.setAaptOptions(scope.getGlobalScope().getExtension().getAaptOptions());
            processResources
                    .setPseudoLocalesEnabled(config.getBuildType().isPseudoLocalesEnabled());

            ConventionMappingHelper.map(
                    processResources,
                    "resourceConfigs",
                    () -> {
                        Collection<String> resConfigs =
                                config.getMergedFlavor().getResourceConfigurations();
                        if (resConfigs.size() == 1
                                && Iterators.getOnlyElement(resConfigs.iterator()).equals("auto")) {
                            return variantData.discoverListOfResourceConfigsNotDensities();
                        }
                        return config.getMergedFlavor().getResourceConfigurations();
                    });

            ConventionMappingHelper.map(
                    processResources,
                    "preferredDensity",
                    () -> {
                        String variantFilter =
                                variantOutputData.getMainOutputFile().getFilter(OutputFile.DENSITY);
                        if (variantFilter != null) {
                            return variantFilter;
                        }
                        return AndroidGradleOptions.getBuildTargetDensity(
                                scope.getGlobalScope().getProject());
                    });

            processResources.setMergeBlameLogFolder(
                    variantScope.getResourceBlameLogDir());

            processResources.buildContext =
                    variantScope.getBuildContext();

            processResources.setAtomResourcePackages(
                    variantScope.getArtifactFileCollection(
                            COMPILE,
                            ALL,
                            AndroidArtifacts.ArtifactType.RESOURCES_PKG));
        }
    }

    @InputFile
    public File getManifestFile() {
        return manifestFile;
    }

    public void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    /**
     * Not an input, it's optional and should never change independently of the main manifest file.
     * <p>
     * The change is handled by {@link #isInstantRunMode()}.
     */
    public File getInstantRunManifestFile() {
        return instantRunManifestFile;
    }

    public void setInstantRunManifestFile(File manifestFile) {
        this.instantRunManifestFile = manifestFile;
    }

    /**
     * To force the task to execute when the manifest file to use changes.
     * <p>
     * Fix for <a href="http://b.android.com/209985">b.android.com/209985</a>.
     */
    @Input
    public boolean isInstantRunMode() {
        return this.buildContext.isInInstantRunMode();
    }

    @NonNull
    @InputDirectory
    public File getResDir() {
        return resDir;
    }

    public void setResDir(@NonNull File resDir) {
        this.resDir = resDir;
    }

    @OutputDirectory
    @Optional
    @Nullable
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(@Nullable File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    @OutputDirectory
    @Optional
    @Nullable
    public File getTextSymbolOutputDir() {
        return textSymbolOutputDir.get();
    }

    public void setTextSymbolOutputDir(Supplier<File> textSymbolOutputDir) {
        this.textSymbolOutputDir = textSymbolOutputDir;
    }

    @org.gradle.api.tasks.OutputFile
    @Optional
    @Nullable
    public File getPackageOutputFile() {
        return packageOutputFile.get();
    }

    public void setPackageOutputFile(Supplier<File> packageOutputFile) {
        this.packageOutputFile = packageOutputFile;
    }

    @org.gradle.api.tasks.OutputFile
    @Optional
    @Nullable
    public File getProguardOutputFile() {
        return proguardOutputFile;
    }

    public void setProguardOutputFile(File proguardOutputFile) {
        this.proguardOutputFile = proguardOutputFile;
    }

    @org.gradle.api.tasks.OutputFile
    @Optional
    @Nullable
    public File getMainDexListProguardOutputFile() {
        return mainDexListProguardOutputFile;
    }

    public void setAaptMainDexListProguardOutputFile(File mainDexListProguardOutputFile) {
        this.mainDexListProguardOutputFile = mainDexListProguardOutputFile;
    }

    @Input
    public Collection<String> getResourceConfigs() {
        return resourceConfigs;
    }

    public void setResourceConfigs(Collection<String> resourceConfigs) {
        this.resourceConfigs = resourceConfigs;
    }

    @Input
    @Optional
    @Nullable
    public String getPreferredDensity() {
        return preferredDensity;
    }

    public void setPreferredDensity(String preferredDensity) {
        this.preferredDensity = preferredDensity;
    }

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @Nullable
    @InputFiles
    @Optional
    public FileCollection getTestedManifest() {
        return testedManifest;
    }

    @Nullable
    @InputFiles
    @Optional
    public FileCollection getTestedSymbolFile() {
        return testedSymbolFile;
    }

    @InputFiles
    @Optional
    public FileCollection getManifests() {
        return manifests == null ? null : manifests.getArtifactFiles();
    }

    @InputFiles
    @Optional
    public FileCollection getSymbolFiles() {
        return symbolFiles == null ? null : symbolFiles.getArtifactFiles();
    }

    @Input
    @Optional
    @Nullable
    public String getPackageForR() {
        return packageForR;
    }

    public void setPackageForR(String packageForR) {
        this.packageForR = packageForR;
    }

    @InputFiles
    public FileCollection getSplitListResource() {
        return splitList.getFileCollection();
    }

    @Input
    public boolean getEnforceUniquePackageName() {
        return enforceUniquePackageName;
    }

    public void setEnforceUniquePackageName(boolean enforceUniquePackageName) {
        this.enforceUniquePackageName = enforceUniquePackageName;
    }

    /** Does not change between incremental builds, so does not need to be @Input. */
    public VariantType getType() {
        return type;
    }

    public void setType(VariantType type) {
        this.type = type;
    }

    @Input
    public boolean getDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Input
    public boolean getPseudoLocalesEnabled() {
        return pseudoLocalesEnabled;
    }

    public void setPseudoLocalesEnabled(boolean pseudoLocalesEnabled) {
        this.pseudoLocalesEnabled = pseudoLocalesEnabled;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    public void setAaptOptions(AaptOptions aaptOptions) {
        this.aaptOptions = aaptOptions;
    }

    @Input
    public File getMergeBlameLogFolder() {
        return mergeBlameLogFolder;
    }

    public void setMergeBlameLogFolder(File mergeBlameLogFolder) {
        this.mergeBlameLogFolder = mergeBlameLogFolder;
    }

    @InputFiles
    @NonNull
    public FileCollection getAtomResourcePackages() {
        return atomResourcePackages;
    }

    public void setAtomResourcePackages(FileCollection atomResourcePackages) {
        this.atomResourcePackages = atomResourcePackages;
    }

    @Input
    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return splitHandlingPolicy;
    }

    public void setSplitHandlingPolicy(SplitHandlingPolicy splitHandlingPolicy) {
        this.splitHandlingPolicy = splitHandlingPolicy;
    }
}
