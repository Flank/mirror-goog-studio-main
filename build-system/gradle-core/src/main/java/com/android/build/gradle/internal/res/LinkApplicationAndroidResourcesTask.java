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
package com.android.build.gradle.internal.res;

import static com.android.SdkConstants.FN_RES_BASE;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_IDS_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.res.namespaced.Aapt2MavenUtils;
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.SplitList;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.v2.Aapt2DaemonManager;
import com.android.builder.internal.aapt.v2.Aapt2Exception;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.symbols.SymbolIo;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.tooling.BuildException;

@CacheableTask
public class LinkApplicationAndroidResourcesTask extends ProcessAndroidResources {

    private static final String IR_APK_FILE_NAME = "resources";

    private static final Logger LOG = Logging.getLogger(LinkApplicationAndroidResourcesTask.class);

    @Nullable private File sourceOutputDir;

    private Supplier<File> textSymbolOutputDir = () -> null;

    @Nullable private File symbolsWithPackageNameOutputFile;

    private File proguardOutputFile;

    private File mainDexListProguardOutputFile;

    @Nullable private FileCollection dependenciesFileCollection;
    @Nullable private FileCollection sharedLibraryDependencies;
    @Nullable private ArtifactCollection packageIdsFiles;

    private MultiOutputPolicy multiOutputPolicy;

    private VariantType type;

    private AaptGeneration aaptGeneration;

    @Nullable private FileCollection aapt2FromMaven;

    private boolean debuggable;

    private boolean pseudoLocalesEnabled;

    private AaptOptions aaptOptions;

    private File mergeBlameLogFolder;

    private InstantRunBuildContext buildContext;

    private FileCollection featureResourcePackages;

    private String originalApplicationId;

    private String buildTargetDensity;

    private File resPackageOutputFolder;

    private String projectBaseName;

    private InternalArtifactType taskInputType;

    private boolean isNamespaced = false;

    @Input
    public InternalArtifactType getTaskInputType() {
        return taskInputType;
    }

    @Input
    public InstantRunPatchingPolicy getPatchingPolicy() {
        return buildContext.getPatchingPolicy();
    }

    @Input
    public String getProjectBaseName() {
        return projectBaseName;
    }

    private VariantScope variantScope;

    @NonNull
    @Internal
    private Set<String> getSplits(@NonNull SplitList splitList) throws IOException {
        return SplitList.getSplits(splitList, multiOutputPolicy);
    }

    @Input
    public String getApplicationId() {
        return applicationId;
    }

    FileCollection splitListInput;

    private OutputScope outputScope;

    private OutputFactory outputFactory;

    private boolean enableAapt2;

    private String applicationId;

    private File supportDirectory;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getApkList() {
        return apkList;
    }

    private FileCollection apkList;

    // FIX-ME : make me incremental !
    @Override
    protected void doFullTaskAction() throws IOException {

        WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

        BuildElements manifestBuildElements =
                ExistingBuildElements.from(taskInputType, manifestFiles);

        final Set<File> packageIdFileSet =
                packageIdsFiles != null
                        ? packageIdsFiles.getArtifactFiles().getAsFileTree().getFiles()
                        : null;

        final Set<File> featureResourcePackages = this.featureResourcePackages.getFiles();

        SplitList splitList =
                splitListInput == null ? SplitList.EMPTY : SplitList.load(splitListInput);

        Set<File> dependencies =
                dependenciesFileCollection != null
                        ? dependenciesFileCollection.getFiles()
                        : Collections.emptySet();
        Set<File> imports =
                sharedLibraryDependencies != null
                        ? sharedLibraryDependencies.getFiles()
                        : Collections.emptySet();

        ImmutableList.Builder<BuildOutput> buildOutputs = ImmutableList.builder();

        try (@Nullable Aapt aapt = makeAapt()) {

            Aapt2ServiceKey aapt2ServiceKey;
            if (aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_SHARED_POOL) {
                aapt2ServiceKey =
                        Aapt2DaemonManagerService.registerAaptService(
                                aapt2FromMaven, getBuildTools(), getILogger());
            } else {
                aapt2ServiceKey = null;
            }

            // do a first pass at the list so we generate the code synchronously since it's required
            // by the full splits asynchronous processing below.
            List<BuildOutput> unprocessedManifest =
                    manifestBuildElements.stream().collect(Collectors.toList());

            for (BuildOutput manifestBuildOutput : manifestBuildElements) {
                ApkInfo apkInfo = manifestBuildOutput.getApkInfo();
                boolean codeGen =
                        (apkInfo.getType() == OutputFile.OutputType.MAIN
                                || apkInfo.getFilter(OutputFile.FilterType.DENSITY) == null);
                if (codeGen) {
                    unprocessedManifest.remove(manifestBuildOutput);
                    buildOutputs.add(
                            invokeAaptForSplit(
                                    manifestBuildOutput,
                                    dependencies,
                                    imports,
                                    packageIdFileSet,
                                    splitList,
                                    featureResourcePackages,
                                    apkInfo,
                                    true,
                                    aapt,
                                    aapt2ServiceKey));
                    break;
                }
            }
            // now all remaining splits will be generated asynchronously.
            for (BuildOutput manifestBuildOutput : unprocessedManifest) {
                ApkInfo apkInfo = manifestBuildOutput.getApkInfo();
                if (apkInfo.requiresAapt()) {
                    executor.execute(
                            () ->
                                    invokeAaptForSplit(
                                            manifestBuildOutput,
                                            dependencies,
                                            imports,
                                            packageIdFileSet,
                                            splitList,
                                            featureResourcePackages,
                                            apkInfo,
                                            false,
                                            aapt,
                                            aapt2ServiceKey));
                }
            }

            List<WaitableExecutor.TaskResult<BuildOutput>> taskResults = executor.waitForAllTasks();
            taskResults.forEach(
                    taskResult -> {
                        if (taskResult.getException() != null) {
                            throw new BuildException(
                                    taskResult.getException().getMessage(),
                                    taskResult.getException());
                        } else {
                            buildOutputs.add(taskResult.getValue());
                        }
                    });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (multiOutputPolicy == MultiOutputPolicy.SPLITS) {
            // now populate the pure splits list in the SplitScope (this should eventually move
            // to the SplitDiscoveryTask.
            splitList.forEach(
                    (filterType, filters) -> {
                        // only for densities and languages.
                        if (filterType != VariantOutput.FilterType.DENSITY
                                && filterType != VariantOutput.FilterType.LANGUAGE) {
                            return;
                        }
                        filters.forEach(
                                filter -> {
                                    ApkData configurationApkData =
                                            outputFactory.addConfigurationSplit(
                                                    filterType,
                                                    filter.getValue(),
                                                    "" /* replaced later */,
                                                    filter.getDisplayName());
                                    configurationApkData.setVersionCode(
                                            variantScope
                                                    .getVariantConfiguration()
                                                    .getVersionCode());
                                    configurationApkData.setVersionName(
                                            variantScope
                                                    .getVariantConfiguration()
                                                    .getVersionName());

                                    // call user's script for the newly discovered resources split.
                                    variantScope
                                            .getVariantData()
                                            .variantOutputFactory
                                            .create(configurationApkData);

                                    // in case we generated pure splits, we may have more than one
                                    // resource AP_ in the output directory. reconcile with the
                                    // splits list and save it for downstream tasks.
                                    File packagedResForSplit =
                                            findPackagedResForSplit(
                                                    resPackageOutputFolder, configurationApkData);
                                    if (packagedResForSplit != null) {
                                        configurationApkData.setOutputFileName(
                                                packagedResForSplit.getName());
                                        buildOutputs.add(
                                                new BuildOutput(
                                                        InternalArtifactType
                                                                .DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                                                        configurationApkData,
                                                        packagedResForSplit));
                                    } else {
                                        getLogger()
                                                .warn(
                                                        "Cannot find output for "
                                                                + configurationApkData);
                                    }
                                });
                    });
        }
        // and save the metadata file.
        new BuildElements(buildOutputs.build()).save(resPackageOutputFolder);
    }

    BuildOutput invokeAaptForSplit(
            BuildOutput manifestOutput,
            @NonNull Set<File> dependencies,
            Set<File> imports,
            @Nullable Set<File> packageIdFileSet,
            @NonNull SplitList splitList,
            @NonNull Set<File> featureResourcePackages,
            ApkInfo apkData,
            boolean generateCode,
            @Nullable Aapt aapt,
            @Nullable Aapt2ServiceKey aapt2ServiceKey)
            throws IOException {

        ImmutableList.Builder<File> featurePackagesBuilder = ImmutableList.builder();
        for (File featurePackage : featureResourcePackages) {
            BuildElements buildElements =
                    ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, featurePackage);
            if (!buildElements.isEmpty()) {
                BuildOutput mainBuildOutput =
                        buildElements.element(variantScope.getOutputScope().getMainSplit());
                if (mainBuildOutput != null) {
                    featurePackagesBuilder.add(mainBuildOutput.getOutputFile());
                } else {
                    throw new IOException(
                            "Cannot find PROCESSED_RES output for "
                                    + variantScope.getOutputScope().getMainSplit());
                }
            }
        }

        File resOutBaseNameFile =
                new File(
                        resPackageOutputFolder,
                        FN_RES_BASE
                                + RES_QUALIFIER_SEP
                                + apkData.getFullName()
                                + SdkConstants.DOT_RES);

        File manifestFile = manifestOutput.getOutputFile();

        String packageForR = null;
        File srcOut = null;
        File symbolOutputDir = null;
        File proguardOutputFile = null;
        File mainDexListProguardOutputFile = null;
        if (generateCode) {
            packageForR = originalApplicationId;

            // we have to clean the source folder output in case the package name changed.
            srcOut = getSourceOutputDir();
            if (srcOut != null) {
                FileUtils.cleanOutputDir(srcOut);
            }

            symbolOutputDir = textSymbolOutputDir.get();
            proguardOutputFile = getProguardOutputFile();
            mainDexListProguardOutputFile = getMainDexListProguardOutputFile();
        }

        FilterData densityFilterData = apkData.getFilter(OutputFile.FilterType.DENSITY);
        String preferredDensity =
                densityFilterData != null
                        ? densityFilterData.getIdentifier()
                        // if resConfigs is set, we should not use our preferredDensity.
                        : splitList.getFilters(SplitList.RESOURCE_CONFIGS).isEmpty()
                                ? buildTargetDensity
                                : null;

        Integer packageId = null;
        if (packageIdFileSet != null
                && FeatureSplitPackageIds.getOutputFile(packageIdFileSet) != null) {
            FeatureSplitPackageIds featurePackageIds =
                    FeatureSplitPackageIds.load(packageIdFileSet);
            packageId = featurePackageIds.getIdFor(getProject().getPath());
        }

        try {

            // If we are in instant run mode and we use a split APK for these resources.
            if (buildContext.isInInstantRunMode()
                    && buildContext.getPatchingPolicy()
                            == InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES) {
                supportDirectory.mkdirs();
                // create a split identification manifest.
                manifestFile =
                        InstantRunSliceSplitApkBuilder.generateSplitApkManifest(
                                supportDirectory,
                                IR_APK_FILE_NAME,
                                applicationId,
                                apkData.getVersionName(),
                                apkData.getVersionCode(),
                                manifestOutput
                                        .getProperties()
                                        .get(SdkConstants.ATTR_MIN_SDK_VERSION));
            }

            // If the new resources flag is enabled and if we are dealing with a library process
            // resources through the new parsers
            {
                AaptPackageConfig.Builder configBuilder =
                        new AaptPackageConfig.Builder()
                                .setManifestFile(manifestFile)
                                .setOptions(DslAdaptersKt.convert(aaptOptions))
                                .setCustomPackageForR(packageForR)
                                .setSymbolOutputDir(symbolOutputDir)
                                .setSourceOutputDir(srcOut)
                                .setResourceOutputApk(resOutBaseNameFile)
                                .setProguardOutputFile(proguardOutputFile)
                                .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                                .setVariantType(getType())
                                .setDebuggable(getDebuggable())
                                .setPseudoLocalize(getPseudoLocalesEnabled())
                                .setResourceConfigs(
                                        splitList.getFilters(SplitList.RESOURCE_CONFIGS))
                                .setSplits(getSplits(splitList))
                                .setPreferredDensity(preferredDensity)
                                .setPackageId(packageId)
                                .setDependentFeatures(featurePackagesBuilder.build())
                                .setImports(imports)
                                .setAndroidTarget(getBuilder().getTarget());

                if (isNamespaced) {
                    configBuilder.setStaticLibraryDependencies(ImmutableList.copyOf(dependencies));
                } else {
                    if (generateCode) {
                        configBuilder.setLibrarySymbolTableFiles(dependencies);
                    }
                    configBuilder.setResourceDir(
                            checkNotNull(getInputResourcesDir()).getSingleFile());
                }
                AaptPackageConfig config = configBuilder.build();

                if (aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_SHARED_POOL) {
                    Preconditions.checkNotNull(
                            aapt2ServiceKey, "AAPT2 daemon manager service not initialized");
                    try (Aapt2DaemonManager.LeasedAaptDaemon aaptDaemon =
                            Aapt2DaemonManagerService.getAaptDaemon(aapt2ServiceKey)) {
                        AndroidBuilder.processResources(aaptDaemon, config, getILogger());
                    } catch (Aapt2Exception e) {
                        throw Aapt2ErrorUtils.rewriteLinkException(
                                e, new MergingLog(getMergeBlameLogFolder()));
                    }
                } else {
                    Preconditions.checkNotNull(
                            aapt,
                            "AAPT needs be instantiated for linking if bypassing AAPT is disabled");
                    AndroidBuilder.processResources(aapt, config, getILogger());
                }

                if (LOG.isInfoEnabled()) {
                    LOG.info("Aapt output file {}", resOutBaseNameFile.getAbsolutePath());
                }
            }
            if (generateCode
                    && (isLibrary || !dependencies.isEmpty())
                    && symbolsWithPackageNameOutputFile != null) {
                SymbolIo.writeSymbolTableWithPackage(
                        Preconditions.checkNotNull(getTextSymbolOutputFile()).toPath(),
                        manifestFile.toPath(),
                        symbolsWithPackageNameOutputFile.toPath());
            }

            return new BuildOutput(
                    InternalArtifactType.PROCESSED_RES,
                    apkData,
                    resOutBaseNameFile,
                    manifestOutput.getProperties());
        } catch (ProcessException e) {
            throw new BuildException(
                    "Failed to process resources, see aapt output above for details.", e);
        }
    }

    @Nullable
    private static File findPackagedResForSplit(@Nullable File outputFolder, ApkData apkData) {
        Pattern resourcePattern =
                Pattern.compile(
                        FN_RES_BASE + RES_QUALIFIER_SEP + apkData.getFullName() + ".ap__(.*)");

        if (outputFolder == null) {
            return null;
        }
        File[] files = outputFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                Matcher match = resourcePattern.matcher(file.getName());
                // each time we match, we remove the associated filter from our copies.
                if (match.matches()
                        && !match.group(1).isEmpty()
                        && isValidSplit(apkData, match.group(1))) {
                    return file;
                }
            }
        }
        return null;
    }

    /**
     * Create an instance of AAPT. Whenever calling this method make sure the close() method is
     * called on the instance once the work is done.
     *
     * <p>Returns null if the worker action compatible mode should be used, as instances must not be
     * shared between threads.
     */
    @Nullable
    private Aapt makeAapt() {
        AndroidBuilder builder = getBuilder();
        if (aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_SHARED_POOL) {

            return null;
        }

        MergingLog mergingLog = new MergingLog(getMergeBlameLogFolder());

        ProcessOutputHandler processOutputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(
                                aaptGeneration == AaptGeneration.AAPT_V1
                                        ? new AaptOutputParser()
                                        : new Aapt2OutputParser(),
                                getILogger()),
                        new MergingLogRewriter(mergingLog::find, builder.getMessageReceiver()));

        return AaptGradleFactory.make(
                aaptGeneration,
                builder,
                processOutputHandler,
                true,
                FileUtils.mkdirs(new File(getIncrementalFolder(), "aapt-temp")),
                aaptOptions.getCruncherProcesses());
    }

    /**
     * Returns true if the passed split identifier is a valid identifier (valid mean it is a
     * requested split for this task). A density split identifier can be suffixed with characters
     * added by aapt.
     */
    private static boolean isValidSplit(ApkInfo apkData, @NonNull String splitWithOptionalSuffix) {

        FilterData splitFilter = apkData.getFilter(OutputFile.FilterType.DENSITY);
        if (splitFilter != null) {
            if (splitWithOptionalSuffix.startsWith(splitFilter.getIdentifier())) {
                return true;
            }
        }
        String mangledName = unMangleSplitName(splitWithOptionalSuffix);
        splitFilter = apkData.getFilter(OutputFile.FilterType.LANGUAGE);
        if (splitFilter != null && mangledName.equals(splitFilter.getIdentifier())) {
            return true;
        }
        return false;
    }

    /**
     * Un-mangle a split name as created by the aapt tool to retrieve a split name as configured in
     * the project's build.gradle.
     *
     * <p>when dealing with several split language in a single split, each language (+ optional
     * region) will be separated by an underscore.
     *
     * <p>note that there is currently an aapt bug, remove the 'r' in the region so for instance,
     * fr-rCA becomes fr-CA, temporarily put it back until it is fixed.
     *
     * @param splitWithOptionalSuffix the mangled split name.
     */
    public static String unMangleSplitName(String splitWithOptionalSuffix) {
        String mangledName = splitWithOptionalSuffix.replaceAll("_", ",");
        return mangledName.contains("-r") ? mangledName : mangledName.replace("-", "-r");
    }

    public static class ConfigAction
            implements TaskConfigAction<LinkApplicationAndroidResourcesTask> {
        protected final VariantScope variantScope;
        protected final Supplier<File> symbolLocation;
        private final File symbolsWithPackageNameOutputFile;
        @NonNull private final File resPackageOutputFolder;
        private final boolean generateLegacyMultidexMainDexProguardRules;
        private final TaskManager.MergeType sourceArtifactType;
        private final String baseName;
        private final boolean isLibrary;

        public ConfigAction(
                @NonNull VariantScope scope,
                @NonNull Supplier<File> symbolLocation,
                @NonNull File symbolsWithPackageNameOutputFile,
                @NonNull File resPackageOutputFolder,
                boolean generateLegacyMultidexMainDexProguardRules,
                @NonNull TaskManager.MergeType sourceArtifactType,
                @NonNull String baseName,
                boolean isLibrary) {
            this.variantScope = scope;
            this.symbolLocation = symbolLocation;
            this.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile;
            this.resPackageOutputFolder = resPackageOutputFolder;
            this.generateLegacyMultidexMainDexProguardRules =
                    generateLegacyMultidexMainDexProguardRules;
            this.baseName = baseName;
            this.sourceArtifactType = sourceArtifactType;
            this.isLibrary = isLibrary;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("process", "Resources");
        }

        @NonNull
        @Override
        public Class<LinkApplicationAndroidResourcesTask> getType() {
            return LinkApplicationAndroidResourcesTask.class;
        }

        @Override
        public void execute(@NonNull LinkApplicationAndroidResourcesTask processResources) {
            final BaseVariantData variantData = variantScope.getVariantData();

            final ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();

            variantData.addTask(TaskContainer.TaskKind.PROCESS_ANDROID_RESOURCES, processResources);

            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            processResources.setAndroidBuilder(variantScope.getGlobalScope().getAndroidBuilder());
            processResources.setVariantName(config.getFullName());
            processResources.resPackageOutputFolder = resPackageOutputFolder;
            processResources.aaptGeneration = AaptGeneration.fromProjectOptions(projectOptions);
            processResources.aapt2FromMaven =
                    Aapt2MavenUtils.getAapt2FromMavenIfEnabled(variantScope.getGlobalScope());

            if (variantData.getType() == VariantType.LIBRARY) {
                throw new IllegalArgumentException("Use GenerateLibraryRFileTask");
            } else {
                Preconditions.checkState(
                        sourceArtifactType == TaskManager.MergeType.MERGE,
                        "source output type should be MERGE",
                        sourceArtifactType);
            }

            processResources.setEnableAapt2(projectOptions.get(BooleanOption.ENABLE_AAPT2));

            processResources.applicationId = config.getApplicationId();

            // per exec
            processResources.setIncrementalFolder(variantScope.getIncrementalDir(getName()));

            if (variantData.getType().getCanHaveSplits()) {
                processResources.splitListInput =
                        variantScope.getOutput(InternalArtifactType.SPLIT_LIST);
            }

            processResources.apkList = variantScope.getOutput(InternalArtifactType.APK_LIST);

            processResources.multiOutputPolicy = variantData.getMultiOutputPolicy();

            processResources.dependenciesFileCollection =
                    variantScope.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME);

            // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
            File sourceOut = variantScope.getRClassSourceOutputDir();
            processResources.setSourceOutputDir(sourceOut);
            variantScope.addTaskOutput(
                    InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES, sourceOut, getName());

            processResources.textSymbolOutputDir = symbolLocation;
            processResources.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile;

            if (variantScope.getCodeShrinker() != null) {
                processResources.setProguardOutputFile(
                        variantScope.getProcessAndroidResourcesProguardOutputFile());
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                processResources.setAaptMainDexListProguardOutputFile(
                        variantScope.getManifestKeepListProguardFile());
            }

            processResources.variantScope = variantScope;
            processResources.outputScope = variantData.getOutputScope();
            processResources.outputFactory = variantData.getOutputFactory();
            processResources.originalApplicationId =
                    variantScope.getVariantConfiguration().getOriginalApplicationId();

            boolean aaptFriendlyManifestsFilePresent =
                    variantScope.hasOutput(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS);
            processResources.taskInputType =
                    aaptFriendlyManifestsFilePresent
                            ? InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
                            : variantScope.getInstantRunBuildContext().isInInstantRunMode()
                                    ? InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS
                                    : InternalArtifactType.MERGED_MANIFESTS;
            processResources.setManifestFiles(
                    variantScope.getOutput(processResources.taskInputType));

            processResources.inputResourcesDir =
                    variantScope.getOutput(sourceArtifactType.getOutputType());

            processResources.setType(config.getType());
            processResources.setDebuggable(config.getBuildType().isDebuggable());
            processResources.setAaptOptions(
                    variantScope.getGlobalScope().getExtension().getAaptOptions());
            processResources.setPseudoLocalesEnabled(
                    config.getBuildType().isPseudoLocalesEnabled());

            processResources.buildTargetDensity =
                    projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY);

            processResources.setMergeBlameLogFolder(variantScope.getResourceBlameLogDir());

            processResources.buildContext = variantScope.getInstantRunBuildContext();

            processResources.featureResourcePackages =
                    variantScope.getArtifactFileCollection(
                            COMPILE_CLASSPATH, MODULE, FEATURE_RESOURCE_PKG);

            processResources.projectBaseName = baseName;
            processResources.isLibrary = isLibrary;
            processResources.supportDirectory =
                    new File(variantScope.getInstantRunSplitApkOutputFolder(), "resources");

            if (!variantScope.isBaseFeature()) {
                // sets the packageIds list.
                processResources.packageIdsFiles =
                        variantScope.getArtifactCollection(
                                COMPILE_CLASSPATH, MODULE, FEATURE_IDS_DECLARATION);
            }
        }
    }

    /**
     * TODO: extract in to a separate task implementation once splits are calculated in the split
     * discovery task.
     */
    public static final class NamespacedConfigAction
            implements TaskConfigAction<LinkApplicationAndroidResourcesTask> {
        protected final VariantScope variantScope;
        @NonNull private final File resPackageOutputDir;
        @NonNull private final File sourceOutputDir;
        private final boolean generateLegacyMultidexMainDexProguardRules;
        @Nullable private final String baseName;

        public NamespacedConfigAction(
                @NonNull VariantScope scope,
                @NonNull File sourceOutputDir,
                @NonNull File resPackageOutputDir,
                boolean generateLegacyMultidexMainDexProguardRules,
                @Nullable String baseName) {
            this.variantScope = scope;
            this.resPackageOutputDir = resPackageOutputDir;
            this.sourceOutputDir = sourceOutputDir;
            this.generateLegacyMultidexMainDexProguardRules =
                    generateLegacyMultidexMainDexProguardRules;
            this.baseName = baseName;
        }

        @NonNull
        @Override
        public final String getName() {
            return variantScope.getTaskName("process", "Resources");
        }

        @NonNull
        @Override
        public final Class<LinkApplicationAndroidResourcesTask> getType() {
            return LinkApplicationAndroidResourcesTask.class;
        }

        @Override
        public final void execute(@NonNull LinkApplicationAndroidResourcesTask task) {
            final BaseVariantData variantData = variantScope.getVariantData();
            final ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            task.setAndroidBuilder(variantScope.getGlobalScope().getAndroidBuilder());
            task.setVariantName(config.getFullName());
            task.resPackageOutputFolder = resPackageOutputDir;
            task.aaptGeneration = AaptGeneration.fromProjectOptions(projectOptions);
            task.aapt2FromMaven = Aapt2MavenUtils.getAapt2FromMaven(variantScope.getGlobalScope());
            task.setEnableAapt2(true);

            task.applicationId = config.getApplicationId();

            // per exec
            task.setIncrementalFolder(variantScope.getIncrementalDir(getName()));
            if (variantData.getType().getCanHaveSplits()) {
                task.splitListInput = variantScope.getOutput(InternalArtifactType.SPLIT_LIST);
            }
            task.multiOutputPolicy = variantData.getMultiOutputPolicy();
            task.apkList = variantScope.getOutput(InternalArtifactType.APK_LIST);

            task.sourceOutputDir = sourceOutputDir;

            if (variantScope.getCodeShrinker() != null) {
                task.setProguardOutputFile(
                        variantScope.getProcessAndroidResourcesProguardOutputFile());
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                task.setAaptMainDexListProguardOutputFile(
                        variantScope.getManifestKeepListProguardFile());
            }

            task.variantScope = variantScope;
            task.outputScope = variantData.getOutputScope();
            task.outputFactory = variantData.getOutputFactory();
            task.originalApplicationId =
                    variantScope.getVariantConfiguration().getOriginalApplicationId();

            boolean aaptFriendlyManifestsFilePresent =
                    variantScope.hasOutput(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS);
            task.taskInputType =
                    aaptFriendlyManifestsFilePresent
                            ? InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
                            : variantScope.getInstantRunBuildContext().isInInstantRunMode()
                                    ? InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS
                                    : InternalArtifactType.MERGED_MANIFESTS;
            task.setManifestFiles(variantScope.getOutput(task.taskInputType));

            List<FileCollection> dependencies = new ArrayList<>(2);
            dependencies.add(variantScope.getOutput(InternalArtifactType.RES_STATIC_LIBRARY));
            dependencies.add(
                    variantScope.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY));

            task.dependenciesFileCollection =
                    variantScope.getGlobalScope().getProject().files(dependencies);
            task.sharedLibraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY);
            task.setType(config.getType());
            task.setDebuggable(config.getBuildType().isDebuggable());
            task.setAaptOptions(variantScope.getGlobalScope().getExtension().getAaptOptions());
            task.setPseudoLocalesEnabled(config.getBuildType().isPseudoLocalesEnabled());

            task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY);

            task.setMergeBlameLogFolder(variantScope.getResourceBlameLogDir());

            task.buildContext = variantScope.getInstantRunBuildContext();

            task.featureResourcePackages =
                    variantScope.getArtifactFileCollection(
                            COMPILE_CLASSPATH, MODULE, FEATURE_RESOURCE_PKG);
            if (variantScope.getVariantData().getType() == VariantType.FEATURE
                    && !variantScope.isBaseFeature()) {
                task.packageIdsFiles =
                        variantScope.getArtifactCollection(
                                COMPILE_CLASSPATH, MODULE, FEATURE_IDS_DECLARATION);
            }

            task.projectBaseName = baseName;
            task.isLibrary = false;
            task.supportDirectory =
                    new File(variantScope.getInstantRunSplitApkOutputFolder(), "resources");
            task.isNamespaced = true;
        }
    }

    FileCollection manifestFiles;

    public File getManifestFile() {
        File manifestDirectory = Iterables.getFirst(manifestFiles.getFiles(), null);
        Preconditions.checkNotNull(manifestDirectory);
        Preconditions.checkNotNull(outputScope.getMainSplit());
        return FileUtils.join(
                manifestDirectory,
                outputScope.getMainSplit().getDirName(),
                SdkConstants.ANDROID_MANIFEST_XML);
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getManifestFiles() {
        return manifestFiles;
    }

    public void setManifestFiles(FileCollection manifestFiles) {
        this.manifestFiles = manifestFiles;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getPackageIdsFiles() {
        return packageIdsFiles != null ? packageIdsFiles.getArtifactFiles() : null;
    }

    /**
     * To force the task to execute when the manifest file to use changes.
     *
     * <p>Fix for <a href="http://b.android.com/209985">b.android.com/209985</a>.
     */
    @Input
    public boolean isInstantRunMode() {
        return this.buildContext.isInInstantRunMode();
    }

    @Nullable private FileCollection inputResourcesDir;

    @Nullable
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getInputResourcesDir() {
        return inputResourcesDir;
    }

    @Override
    @OutputDirectory
    @Optional
    @Nullable
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(@Nullable File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    @org.gradle.api.tasks.OutputFile
    @Optional
    @Nullable
    public File getTextSymbolOutputFile() {
        File outputDir = textSymbolOutputDir.get();
        return outputDir != null
                ? new File(outputDir, SdkConstants.R_CLASS + SdkConstants.DOT_TXT)
                : null;
    }

    @org.gradle.api.tasks.OutputFile
    @Optional
    @Nullable
    public File getSymbolslWithPackageNameOutputFile() {
        return symbolsWithPackageNameOutputFile;
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
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @Nullable
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getDependenciesFileCollection() {
        return dependenciesFileCollection;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    @Nullable
    public FileCollection getSharedLibraryDependencies() {
        return sharedLibraryDependencies;
    }

    @Input
    public String getTypeAsString() {
        return type.name();
    }

    @Internal
    public VariantType getType() {
        return type;
    }

    public void setType(VariantType type) {
        this.type = type;
    }

    @Input
    public String getAaptGeneration() {
        return aaptGeneration.name();
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @Nullable
    public FileCollection getAapt2FromMaven() {
        return aapt2FromMaven;
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

    /** Only used for rewriting error messages. Should not affect task result. */
    @Internal
    public File getMergeBlameLogFolder() {
        return mergeBlameLogFolder;
    }

    public void setMergeBlameLogFolder(File mergeBlameLogFolder) {
        this.mergeBlameLogFolder = mergeBlameLogFolder;
    }

    @InputFiles
    @NonNull
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getFeatureResourcePackages() {
        return featureResourcePackages;
    }

    @Input
    public MultiOutputPolicy getMultiOutputPolicy() {
        return multiOutputPolicy;
    }

    @Input
    public String getOriginalApplicationId() {
        return originalApplicationId;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    FileCollection getSplitListInput() {
        return splitListInput;
    }

    @Input
    @Optional
    String getBuildTargetDensity() {
        return buildTargetDensity;
    }

    @OutputDirectory
    @NonNull
    File getResPackageOutputFolder() {
        return resPackageOutputFolder;
    }

    @Input
    public boolean isAapt2Enabled() {
        return enableAapt2;
    }

    public void setEnableAapt2(boolean enableAapt2) {
        this.enableAapt2 = enableAapt2;
    }

    boolean isLibrary;

    @Input
    boolean isLibrary() {
        return isLibrary;
    }

    @Input
    public boolean isNamespaced() {
        return isNamespaced;
    }
}
