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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.LoggingUtil;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.symbols.IdProvider;
import com.android.builder.symbols.ResourceDirectoryParser;
import com.android.builder.symbols.SymbolTable;
import com.android.builder.symbols.SymbolUtils;
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
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
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

    private File mergeResourcesOutputDir;

    private File sourceOutputDir;

    private Supplier<File> textSymbolOutputDir = () -> null;

    private Supplier<File> packageOutputFile = () -> null;

    private File proguardOutputFile;

    private File mainDexListProguardOutputFile;

    private Collection<String> resourceConfigs;

    private String preferredDensity;

    private List<AndroidDependency> androidDependencies;

    private String packageForR;

    private Collection<String> splits;

    private boolean enforceUniquePackageName;

    private VariantType type;

    private boolean debuggable;

    private boolean pseudoLocalesEnabled;

    private AaptOptions aaptOptions;

    private File mergeBlameLogFolder;

    private InstantRunBuildContext instantRunBuildContext;

    private Supplier<File> baseFeature;

    private Collection<File> previousFeatures;

    private boolean enableNewResourceProcessing;

    private boolean enableAapt2;

    @Override
    protected void doFullTaskAction() throws IOException {

        // we have to clean the source folder output in case the package name changed.
        File srcOut = getSourceOutputDir();
        if (srcOut != null) {
            FileUtils.cleanOutputDir(srcOut);
        }

        @Nullable File resOutBaseNameFile = getPackageOutputFile();

        // If we are in instant run mode and we have an instant run enabled manifest
        File instantRunManifest = getInstantRunManifestFile();
        File manifestFileToPackage =
                instantRunBuildContext.isInInstantRunMode()
                                && instantRunManifest != null
                                && instantRunManifest.exists()
                        ? instantRunManifest
                        : getManifestFile();

        // If the new resources flag is enabled and if we are dealing with a library process
        // resources through the new parsers
        if (enableNewResourceProcessing && this.type.equals(VariantType.LIBRARY)) {

            // Get symbol table of resources of the library
            SymbolTable symbolTable =
                    ResourceDirectoryParser.parseDirectory(getResDir(), IdProvider.sequential());

            SymbolUtils.processLibraryMainSymbolTable(
                    symbolTable,
                    getAndroidDependencies(),
                    getEnforceUniquePackageName(),
                    getPackageForR(),
                    manifestFileToPackage,
                    srcOut,
                    getTextSymbolOutputDir(),
                    getProguardOutputFile());
        } else {
            AndroidBuilder builder = getBuilder();
            MergingLog mergingLog = new MergingLog(getMergeBlameLogFolder());

            File resDir = getResDir();
            File mergeResourcesOutputDir = getMergeResourcesOutputDir();

            MergingLogRewriter mergingLogRewriter =
                    new MergingLogRewriter(
                            resDir.equals(mergeResourcesOutputDir)
                                    ? mergingLog::find
                                    : MergingLogRewriter.rewriteDir(resDir, mergeResourcesOutputDir)
                                            .andThen(mergingLog::find),
                            builder.getErrorReporter());

            ProcessOutputHandler processOutputHandler =
                    new ParsingProcessOutputHandler(
                            new ToolOutputParser(new AaptOutputParser(), getILogger()),
                            mergingLogRewriter);

            String preferredDensity =
                    getResourceConfigs().isEmpty()
                            ? getPreferredDensity()
                            : null; /* when resConfigs are set, we should respect it */
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
                                .setLibraries(getAndroidDependencies())
                                .setCustomPackageForR(getPackageForR())
                                .setSymbolOutputDir(getTextSymbolOutputDir())
                                .setSourceOutputDir(srcOut)
                                .setResourceOutputApk(resOutBaseNameFile)
                                .setProguardOutputFile(getProguardOutputFile())
                                .setMainDexListProguardOutputFile(
                                        getMainDexListProguardOutputFile())
                                .setVariantType(getType())
                                .setDebuggable(getDebuggable())
                                .setPseudoLocalize(getPseudoLocalesEnabled())
                                .setResourceConfigs(getResourceConfigs())
                                .setSplits(getSplits())
                                .setPreferredDensity(preferredDensity)
                                .setBaseFeature(getBaseFeature())
                                .setPreviousFeatures(getPreviousFeatures());

                builder.processResources(aapt, config, getEnforceUniquePackageName());

                if (resOutBaseNameFile != null && LOG.isInfoEnabled()) {
                    LOG.info("Aapt output file {}", resOutBaseNameFile.getAbsolutePath());
                }

            } catch (InterruptedException | ProcessException e) {
                throw new RuntimeException(e);
            }
        }
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
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantScope().getVariantData();
            variantOutputData.processResourcesTask = processResources;
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            processResources.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processResources.setVariantName(config.getFullName());
            processResources.setIncrementalFolder(
                    scope.getVariantScope().getIncrementalDir(getName()));
            processResources.setEnableNewResourceProcessing(
                    scope.getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.ENABLE_NEW_RESOURCE_PROCESSING));
            processResources.setEnableAapt2(
                    scope.getGlobalScope().getProjectOptions().get(BooleanOption.ENABLE_AAPT2));

            if (variantData.getSplitHandlingPolicy() ==
                    SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY) {
                Set<String> allFilters = new HashSet<>();
                allFilters.addAll(
                        variantData.getFilters(OutputFile.FilterType.DENSITY));
                allFilters.addAll(
                        variantData.getFilters(OutputFile.FilterType.LANGUAGE));
                processResources.splits = allFilters;
            }

            // only generate code if the density filter is null, and if we haven't generated
            // it yet (if you have abi + density splits, then several abi output will have no
            // densityFilter)
            if (variantOutputData.getMainOutputFile()
                    .getFilter(OutputFile.DENSITY) == null
                    && variantData.generateRClassTask == null) {
                variantData.generateRClassTask = processResources;
                processResources.enforceUniquePackageName = scope.getGlobalScope().getExtension()
                        .getEnforceUniquePackageName();

                ConventionMappingHelper.map(
                        processResources,
                        "androidDependencies",
                        config::getFlatPackageAndroidLibraries);
                ConventionMappingHelper.map(
                        processResources,
                        "packageForR",
                        () -> {
                            String splitName = config.getSplitFromManifest();
                            if (splitName == null) {
                                return config.getOriginalApplicationId();
                            } else {
                                return config.getOriginalApplicationId() + "." + splitName;
                            }
                        });

                // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
                processResources
                        .setSourceOutputDir(scope.getVariantScope().getRClassSourceOutputDir());
                processResources.setTextSymbolOutputDir(symbolLocation);

                if (config.getBuildType().isMinifyEnabled()) {
                    if (config.getBuildType().isShrinkResources() && config.isJackEnabled()) {
                        LoggingUtil.displayWarning(Logging.getLogger(getClass()),
                                scope.getGlobalScope().getProject(),
                                "shrinkResources does not yet work with useJack=true");
                    }
                    processResources.setProguardOutputFile(
                            scope.getVariantScope().getProcessAndroidResourcesProguardOutputFile());

                } else if (config.getBuildType().isShrinkResources()) {
                    LoggingUtil.displayWarning(Logging.getLogger(getClass()),
                            scope.getGlobalScope().getProject(),
                            "To shrink resources you must also enable ProGuard");
                }

                if (generateLegacyMultidexMainDexProguardRules) {
                    processResources.setAaptMainDexListProguardOutputFile(
                            scope.getVariantScope().getManifestKeepListProguardFile());
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
                    () -> scope.getVariantScope().getFinalResourcesDir());

            processResources.setMergeResourcesOutputDir(
                    scope.getVariantScope().getMergeResourcesOutputDir());

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
                        return scope.getGlobalScope()
                                .getProjectOptions()
                                .get(StringOption.IDE_BUILD_TARGET_DENISTY);
                    });

            processResources.setMergeBlameLogFolder(
                    scope.getVariantScope().getResourceBlameLogDir());

            processResources.instantRunBuildContext =
                    scope.getVariantScope().getInstantRunBuildContext();

            processResources.setPreviousFeatures(ImmutableSet.of());

            processResources.setBaseFeature(
                    () -> {
                        AtomDependency baseAtom =
                                scope.getVariantScope()
                                        .getVariantConfiguration()
                                        .getPackageDependencies()
                                        .getBaseAtom();
                        return baseAtom != null ? baseAtom.getResourcePackage() : null;
                    });
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
        return this.instantRunBuildContext.isInInstantRunMode();
    }

    @Input
    public File getMergeResourcesOutputDir() {
        return mergeResourcesOutputDir;
    }

    public void setMergeResourcesOutputDir(File file) {
        mergeResourcesOutputDir = file;
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

    public void setSourceOutputDir(File sourceOutputDir) {
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

    @InputFiles
    @Optional
    @Nullable
    public List<File> getInputFilesFromLibraries() {
        List<AndroidDependency> dependencies = getAndroidDependencies();
        if (dependencies == null) {
            return ImmutableList.of();
        }
        List<File> files = Lists.newArrayListWithCapacity(dependencies.size() * 2);
        for (AndroidDependency dependency : dependencies) {
            files.add(dependency.getManifest());
            files.add(dependency.getSymbolFile());
        }
        return files;
    }


    public List<AndroidDependency> getAndroidDependencies() {
        return androidDependencies;
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

    @Input
    @Optional
    @Nullable
    public Collection<String> getSplits() {
        return splits;
    }

    public void setSplits(Collection<String> splits) {
        this.splits = splits;
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

    @InputFile
    @Optional
    @Nullable
    public File getBaseFeature() {
        return baseFeature.get();
    }

    public void setBaseFeature(Supplier<File> baseFeature) {
        this.baseFeature = baseFeature;
    }

    @InputFiles
    @NonNull
    public Collection<File> getPreviousFeatures() {
        return previousFeatures;
    }

    public void setPreviousFeatures(Collection<File> previousFeatures) {
        this.previousFeatures = previousFeatures;
    }

    @Input
    public boolean isEnabledNewResourceProcessing() {
        return enableNewResourceProcessing;
    }

    public void setEnableNewResourceProcessing(boolean enableNewResourceProcessing) {
        this.enableNewResourceProcessing = enableNewResourceProcessing;
    }

    @Input
    public boolean isAapt2Enabled() {
        return enableAapt2;
    }

    public void setEnableAapt2(boolean enableAapt2) {
        this.enableAapt2 = enableAapt2;
    }
}
