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

import static com.android.SdkConstants.FN_RES_BASE;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_IDS_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.SYMBOL_LIST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.options.BooleanOption.BUILD_ONLY_TARGET_ABI;
import static com.android.build.gradle.options.BooleanOption.ENABLE_NEW_RESOURCE_PROCESSING;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.SplitFactory;
import com.android.build.gradle.internal.scope.SplitList;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptPackageConfig.LibraryInfo;
import com.android.builder.symbols.IdProvider;
import com.android.builder.symbols.ResourceDirectoryParser;
import com.android.builder.symbols.SymbolTable;
import com.android.builder.symbols.SymbolUtils;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.resources.Density;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.tooling.BuildException;

@ParallelizableTask
public class ProcessAndroidResources extends IncrementalTask {

    private static final Logger LOG = Logging.getLogger(ProcessAndroidResources.class);

    private File resDir;

    private String buildTargetAbi;
    private Set<String> supportedAbis;

    @Nullable
    private File sourceOutputDir;

    private Supplier<File> textSymbolOutputDir = () -> null;

    private File proguardOutputFile;

    private File mainDexListProguardOutputFile;

    @Nullable
    private ArtifactCollection manifests;
    @Nullable
    private ArtifactCollection symbolFiles;
    @Nullable private ArtifactCollection packageIdsFiles;

    private Supplier<String> packageForR;

    private SplitHandlingPolicy splitHandlingPolicy;

    private boolean enforceUniquePackageName;

    private VariantType type;

    @NonNull private AaptGeneration aaptGeneration;

    private boolean debuggable;

    private boolean pseudoLocalesEnabled;

    private AaptOptions aaptOptions;

    private File mergeBlameLogFolder;

    private InstantRunBuildContext buildContext;

    private FileCollection featureResourcePackages;

    private List<LibraryInfo> computedLibraryInfo;

    private String originalApplicationId;

    private String buildTargetDensity;

    private File resPackageOutputFolder;

    private String projectBaseName;

    private TaskOutputHolder.TaskOutputType taskInputType;

    @Input
    public TaskOutputHolder.TaskOutputType getTaskInputType() {
        return taskInputType;
    }

    @Input
    public String getProjectBaseName() {
        return projectBaseName;
    }

    private VariantScope variantScope;

    @NonNull
    @InputDirectory
    public File getResDir() {
        return resDir;
    }

    @Input
    @Optional
    public String getBuildTargetAbi() {
        return buildTargetAbi;
    }

    @Input
    @Optional
    Set<String> getSupportedAbis() {
        return supportedAbis;
    }

    @NonNull
    private Set<String> getSplits(@NonNull SplitList splitList) throws IOException {
        return SplitList.getSplits(splitList, splitHandlingPolicy);
    }

    FileCollection splitListInput;

    private SplitScope splitScope;

    private SplitFactory splitFactory;

    private boolean enableNewResourceProcessing;

    private boolean enableAapt2;

    // FIX-ME : make me incremental !
    @Override
    protected void doFullTaskAction() throws IOException {

        WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

        if (buildTargetAbi != null
                && supportedAbis != null
                && !supportedAbis.isEmpty()
                && !supportedAbis.contains(buildTargetAbi)) {
            getLogger()
                    .debug(
                            "Cannot build for "
                                    + buildTargetAbi
                                    + " when supportedAbis are "
                                    + Joiner.on(",").join(supportedAbis));
            return;
        }

        // FIX ME : the code below should move to the SplitsDiscoveryTask that should persist
        // the list of splits and their enabled/disabled state.

        // comply when the IDE restricts the full splits we should produce
        Density density = Density.getEnum(buildTargetDensity);

        List<ApkData> splitsToGenerate =
                buildTargetAbi == null
                        ? splitScope.getApkDatas()
                        : SplitOutputMatcher.computeBestOutput(
                                splitScope.getApkDatas(),
                                supportedAbis,
                                density == null ? -1 : density.getDpiValue(),
                                Arrays.asList(Strings.nullToEmpty(buildTargetAbi).split(",")));

        if (splitsToGenerate.isEmpty()) {
            throw new RuntimeException(
                    "Cannot build for ABI \'"
                            + buildTargetAbi
                            + "\'"
                            + ", no suitable splits configured : "
                            + Joiner.on(", ")
                                    .join(
                                            splitScope
                                                    .getApkDatas()
                                                    .stream()
                                                    .map(ApkData::getFilterName)
                                                    .collect(Collectors.toList())));
        }

        for (ApkData apkData : splitScope.getApkDatas()) {
            if (!splitsToGenerate.contains(apkData)) {
                getLogger()
                        .log(
                                LogLevel.DEBUG,
                                "With ABI " + buildTargetAbi + ", disabled " + apkData);
                apkData.disable();
            }
        }
        Collection<BuildOutput> manifestsOutputs = BuildOutputs.load(taskInputType, manifestFiles);

        // compute the library info list up front in the normal thread since it's resolving
        // dependencies and we cannot do this in the executor threads.
        getLibraryInfoList();

        final Set<File> packageIdFileSet =
                packageIdsFiles != null
                        ? packageIdsFiles.getArtifactFiles().getAsFileTree().getFiles()
                        : null;

        final Set<File> featureResourcePackages = this.featureResourcePackages.getFiles();

        SplitList splitList = SplitList.load(splitListInput);

        // do a first pass at the list so we generate the code synchronously since it's required
        // by the full splits asynchronous processing below.
        List<ApkData> apkDataList = new ArrayList<>(splitsToGenerate);
        for (ApkData apkData : splitsToGenerate) {
            if (apkData.requiresAapt()) {
                boolean codeGen =
                        (apkData.getType() == OutputFile.OutputType.MAIN
                                || apkData.getFilter(OutputFile.FilterType.DENSITY) == null);
                if (codeGen) {
                    apkDataList.remove(apkData);
                    invokeAaptForSplit(
                            manifestsOutputs,
                            packageIdFileSet,
                            splitList,
                            featureResourcePackages,
                            apkData,
                            codeGen);
                    break;
                }
            }
        }

        // now all remaining splits will be generated asynchronously.
        for (ApkData apkData : apkDataList) {
            if (apkData.requiresAapt()) {
                executor.execute(
                        () -> {
                            invokeAaptForSplit(
                                    manifestsOutputs,
                                    packageIdFileSet,
                                    splitList,
                                    featureResourcePackages,
                                    apkData,
                                    false);
                            return null;
                        });
            }
        }
        try {
            List<WaitableExecutor.TaskResult<Void>> taskResults = executor.waitForAllTasks();
            taskResults.forEach(
                    taskResult -> {
                        if (taskResult.getException() != null) {
                            throw new BuildException(
                                    taskResult.getException().getMessage(),
                                    taskResult.getException());
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (splitHandlingPolicy == SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY) {
            // now populate the pure splits list in the SplitScope (this should eventually move
            // to the SplitDiscoveryTask.
            splitScope.deleteAllEntries(
                    VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES);
            splitList.forEach(
                    (filterType, filterValues) -> {
                        // only for densities and languages.
                        if (filterType != VariantOutput.FilterType.DENSITY
                                && filterType != VariantOutput.FilterType.LANGUAGE) {
                            return;
                        }
                        filterValues.forEach(
                                filterValue -> {
                                    ApkData configurationApkData =
                                            splitFactory.addConfigurationSplit(
                                                    filterType, filterValue);
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
                                        splitScope.addOutputForSplit(
                                                VariantScope.TaskOutputType
                                                        .DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                                                configurationApkData,
                                                packagedResForSplit);
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
        splitScope.save(
                ImmutableList.of(
                        VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                        VariantScope.TaskOutputType.PROCESSED_RES),
                resPackageOutputFolder);
    }

    void invokeAaptForSplit(
            Collection<BuildOutput> manifestsOutputs,
            @Nullable Set<File> packageIdFileSet,
            @NonNull SplitList splitList,
            @NonNull Set<File> featureResourcePackages,
            ApkData apkData,
            boolean generateCode)
            throws IOException {

        AndroidBuilder builder = getBuilder();
        MergingLog mergingLog = new MergingLog(getMergeBlameLogFolder());

        //TODO: get rid of the rewriter
        MergingLogRewriter mergingLogRewriter =
                new MergingLogRewriter(mergingLog::find, builder.getErrorReporter());

        ProcessOutputHandler processOutputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new AaptOutputParser(), getILogger()),
                        mergingLogRewriter);

        ImmutableList.Builder<File> featurePackagesBuilder = ImmutableList.builder();
        for (File featurePackage : featureResourcePackages) {
            Collection<BuildOutput> splitOutputs =
                    BuildOutputs.load(VariantScope.TaskOutputType.PROCESSED_RES, featurePackage);
            if (!splitOutputs.isEmpty()) {
                featurePackagesBuilder.add(Iterables.getOnlyElement(splitOutputs).getOutputFile());
            }
        }

        File resOutBaseNameFile =
                new File(
                        resPackageOutputFolder,
                        FN_RES_BASE
                                + RES_QUALIFIER_SEP
                                + apkData.getFullName()
                                + SdkConstants.DOT_RES);

        // FIX MEy : there should be a better way to always get the manifest file to merge.
        // for instance, should the library task also output the .gson ?
        BuildOutput manifestOutput = SplitScope.getOutput(manifestsOutputs, taskInputType, apkData);
        if (manifestOutput == null) {
            throw new RuntimeException("Cannot find merged manifest file");
        }
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

            symbolOutputDir = getTextSymbolOutputDir();
            proguardOutputFile = getProguardOutputFile();
            mainDexListProguardOutputFile = getMainDexListProguardOutputFile();
        }

        String splitFilter = apkData.getFilter(OutputFile.FilterType.DENSITY);
        String preferredDensity =
                splitFilter != null
                        ? splitFilter
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
            // If the new resources flag is enabled and if we are dealing with a library process
            // resources through the new parsers
            if (enableNewResourceProcessing && this.type.equals(VariantType.LIBRARY)) {

                // Get symbol table of resources of the library
                SymbolTable symbolTable =
                        ResourceDirectoryParser
                                .parseDirectory(getResDir(), IdProvider.sequential());

                SymbolUtils.processLibraryMainSymbolTable(
                        symbolTable,
                        generateCode ? getLibraryInfoList() : ImmutableList.of(),
                        generateCode && getEnforceUniquePackageName(),
                        packageForR,
                        manifestFile,
                        srcOut,
                        symbolOutputDir,
                        proguardOutputFile);
            } else {

                Aapt aapt =
                        AaptGradleFactory.make(
                                aaptGeneration,
                                builder,
                                processOutputHandler,
                                true,
                                FileUtils.mkdirs(new File(getIncrementalFolder(), "aapt-temp")),
                                aaptOptions.getCruncherProcesses());

                AaptPackageConfig.Builder config =
                        new AaptPackageConfig.Builder()
                                .setManifestFile(manifestFile)
                                .setOptions(getAaptOptions())
                                .setResourceDir(getResDir())
                                .setLibraries(
                                        generateCode ? getLibraryInfoList() : ImmutableList.of())
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
                                .setListResourceFiles(aaptGeneration == AaptGeneration.AAPT_V2);

                builder.processResources(aapt, config,
                        generateCode && getEnforceUniquePackageName());
                if (LOG.isInfoEnabled()) {
                    LOG.info("Aapt output file {}", resOutBaseNameFile.getAbsolutePath());
                }
            }

            splitScope.addOutputForSplit(
                    VariantScope.TaskOutputType.PROCESSED_RES,
                    apkData,
                    resOutBaseNameFile,
                    manifestOutput.getProperties());
        } catch (InterruptedException | ProcessException e) {
            getLogger().error(e.getMessage(), e);
            throw new BuildException(e.getMessage(), e);
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
     * Returns true if the passed split identifier is a valid identifier (valid mean it is a
     * requested split for this task). A density split identifier can be suffixed with characters
     * added by aapt.
     */
    private static boolean isValidSplit(ApkData apkData, @NonNull String splitWithOptionalSuffix) {

        String splitFilter = apkData.getFilter(OutputFile.FilterType.DENSITY);
        if (splitFilter != null) {
            if (splitWithOptionalSuffix.startsWith(splitFilter)) {
                return true;
            }
        }
        String mangledName = unMangleSplitName(splitWithOptionalSuffix);
        splitFilter = apkData.getFilter(OutputFile.FilterType.LANGUAGE);
        if (mangledName.equals(splitFilter)) {
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


    @NonNull
    private List<LibraryInfo> getLibraryInfoList() {
        if (computedLibraryInfo == null) {
            if (symbolFiles != null && manifests != null) {
                computedLibraryInfo = computeLibraryInfoList(symbolFiles, manifests);
            } else {
                computedLibraryInfo = ImmutableList.of();
            }
        }

        return computedLibraryInfo;
    }

    @NonNull
    public static List<LibraryInfo> computeLibraryInfoList(@NonNull VariantScope variantScope) {
        return computeLibraryInfoList(
                variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, SYMBOL_LIST),
                variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST));
    }

    @NonNull
    private static List<LibraryInfo> computeLibraryInfoList(
            @NonNull ArtifactCollection symbolFiles, @NonNull ArtifactCollection manifests) {
        // first build a map for the optional symbols.
        Map<ComponentIdentifier, File> symbolMap = new HashMap<>();
        for (ResolvedArtifactResult artifactResult : symbolFiles.getArtifacts()) {
            symbolMap.put(
                    artifactResult.getId().getComponentIdentifier(), artifactResult.getFile());
        }

        // now loop through all the manifests and associate to a symbol file, if applicable.
        Set<ResolvedArtifactResult> manifestArtifacts = manifests.getArtifacts();
        List<LibraryInfo> libraryInfoList = new ArrayList<>(manifestArtifacts.size());
        for (ResolvedArtifactResult artifactResult : manifestArtifacts) {
            libraryInfoList.add(
                    new LibraryInfo(
                            artifactResult.getFile(),
                            symbolMap.get(artifactResult.getId().getComponentIdentifier())));
        }

        return libraryInfoList;
    }

    public static class ConfigAction implements TaskConfigAction<ProcessAndroidResources> {
        protected final VariantScope variantScope;
        protected final Supplier<File> symbolLocation;
        @NonNull private final File resPackageOutputFolder;
        private final boolean generateLegacyMultidexMainDexProguardRules;
        private final TaskManager.MergeType sourceTaskOutputType;
        private final String baseName;

        public ConfigAction(
                @NonNull VariantScope scope,
                @NonNull Supplier<File> symbolLocation,
                @NonNull File resPackageOutputFolder,
                boolean generateLegacyMultidexMainDexProguardRules,
                @NonNull TaskManager.MergeType sourceTaskOutputType,
                @NonNull String baseName) {
            this.variantScope = scope;
            this.symbolLocation = symbolLocation;
            this.resPackageOutputFolder = resPackageOutputFolder;
            this.generateLegacyMultidexMainDexProguardRules
                    = generateLegacyMultidexMainDexProguardRules;
            this.baseName = baseName;
            this.sourceTaskOutputType = sourceTaskOutputType;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("process", "Resources");
        }

        @NonNull
        @Override
        public Class<ProcessAndroidResources> getType() {
            return ProcessAndroidResources.class;
        }

        @Override
        public void execute(@NonNull ProcessAndroidResources processResources) {
            final BaseVariantData variantData = variantScope.getVariantData();

            variantData.addTask(TaskContainer.TaskKind.PROCESS_ANDROID_RESOURCES, processResources);

            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            processResources.setAndroidBuilder(variantScope.getGlobalScope().getAndroidBuilder());
            processResources.setVariantName(config.getFullName());
            processResources.resPackageOutputFolder = resPackageOutputFolder;
            processResources.aaptGeneration =
                    AaptGeneration.fromProjectOptions(
                            variantScope.getGlobalScope().getProjectOptions());

            processResources.setEnableNewResourceProcessing(
                    variantScope
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(ENABLE_NEW_RESOURCE_PROCESSING));
            processResources.setEnableAapt2(
                    variantScope
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.ENABLE_AAPT2));

            // per exec
            processResources.setIncrementalFolder(variantScope.getIncrementalDir(getName()));

            processResources.splitListInput =
                    variantScope.getOutput(TaskOutputHolder.TaskOutputType.SPLIT_LIST);

            processResources.splitHandlingPolicy =
                    variantData.getSplitScope().getSplitHandlingPolicy();

            processResources.enforceUniquePackageName =
                    variantScope.getGlobalScope().getExtension().getEnforceUniquePackageName();

                processResources.manifests = variantScope.getArtifactCollection(
                        RUNTIME_CLASSPATH, ALL, MANIFEST);

                processResources.symbolFiles = variantScope.getArtifactCollection(
                        RUNTIME_CLASSPATH, ALL, SYMBOL_LIST);

                processResources.packageForR =
                        TaskInputHelper.memoize(
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
                        .setSourceOutputDir(variantScope.getRClassSourceOutputDir());
                processResources.setTextSymbolOutputDir(symbolLocation);

            if (variantScope.getCodeShrinker() != null) {
                processResources.setProguardOutputFile(
                        variantScope.getProcessAndroidResourcesProguardOutputFile());
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                processResources.setAaptMainDexListProguardOutputFile(
                        variantScope.getManifestKeepListProguardFile());
            }

            processResources.variantScope = variantScope;
            processResources.splitScope = variantData.getSplitScope();
            processResources.splitFactory = variantData.getSplitFactory();
            processResources.originalApplicationId =
                    variantScope.getVariantConfiguration().getOriginalApplicationId();

            boolean aaptFriendlyManifestsFilePresent =
                    variantScope.hasOutput(
                            TaskOutputHolder.TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS);
            processResources.taskInputType =
                    aaptFriendlyManifestsFilePresent
                            ? VariantScope.TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS
                            : variantScope.getInstantRunBuildContext().isInInstantRunMode()
                                    ? VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS
                                    : VariantScope.TaskOutputType.MERGED_MANIFESTS;
            processResources.setManifestFiles(
                    variantScope.getOutput(processResources.taskInputType));

            // FIX ME : we should not have both a file collection and a resDir plus, we should
            // express the dependency on the databinding task through a file collection.
            processResources.mergedResources =
                    variantScope.getOutput(sourceTaskOutputType.getOutputType());
            processResources.resDir = variantScope.getMergeResourcesOutputDir();

            processResources.setType(config.getType());
            processResources.setDebuggable(config.getBuildType().isDebuggable());
            processResources.setAaptOptions(
                    variantScope.getGlobalScope().getExtension().getAaptOptions());
            processResources
                    .setPseudoLocalesEnabled(config.getBuildType().isPseudoLocalesEnabled());

            ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();
            processResources.buildTargetDensity =
                    projectOptions.get(StringOption.IDE_BUILD_TARGET_DENISTY);

            processResources.setMergeBlameLogFolder(
                    variantScope.getResourceBlameLogDir());

            processResources.buildContext = variantScope.getInstantRunBuildContext();

            processResources.featureResourcePackages =
                    variantScope.getArtifactFileCollection(
                            COMPILE_CLASSPATH, MODULE, FEATURE_RESOURCE_PKG);

            processResources.projectBaseName = baseName;
            processResources.buildTargetAbi =
                    projectOptions.get(BUILD_ONLY_TARGET_ABI)
                                    || variantScope
                                            .getGlobalScope()
                                            .getExtension()
                                            .getSplits()
                                            .getAbi()
                                            .isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                            : null;
            processResources.supportedAbis = config.getSupportedAbis();
        }
    }

    public static class FeatureSplitConfigAction extends ConfigAction {

        public FeatureSplitConfigAction(
                @NonNull VariantScope scope,
                @NonNull Supplier<File> symbolLocation,
                @NonNull File resPackageOutputFolder,
                boolean generateLegacyMultidexMainDexProguardRules,
                @NonNull TaskManager.MergeType mergeType,
                @NonNull String baseName) {
            super(
                    scope,
                    symbolLocation,
                    resPackageOutputFolder,
                    generateLegacyMultidexMainDexProguardRules,
                    mergeType,
                    baseName);
        }

        @Override
        public void execute(@NonNull ProcessAndroidResources processResources) {
            super.execute(processResources);
            // sets the packageIds list.
            processResources.packageIdsFiles =
                    variantScope.getArtifactCollection(
                            COMPILE_CLASSPATH, MODULE, FEATURE_IDS_DECLARATION);
        }
    }

    FileCollection manifestFiles;

    public File getManifestFile() {
        File manifestDirectory = Iterables.getFirst(manifestFiles.getFiles(), null);
        Preconditions.checkNotNull(manifestDirectory);
        Preconditions.checkNotNull(splitScope.getMainSplit());
        return FileUtils.join(
                manifestDirectory,
                splitScope.getMainSplit().getDirName(),
                SdkConstants.ANDROID_MANIFEST_XML);
    }

    @InputFiles
    public FileCollection getManifestFiles() {
        return manifestFiles;
    }

    public void setManifestFiles(FileCollection manifestFiles) {
        this.manifestFiles = manifestFiles;
    }

    @Optional
    @InputFiles
    public FileCollection getPackageIdsFiles() {
        return packageIdsFiles != null ? packageIdsFiles.getArtifactFiles() : null;
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

    private FileCollection mergedResources;

    @NonNull
    @InputFiles
    public FileCollection getMergedResources() {
        return mergedResources;
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
        return packageForR != null ? packageForR.get() : null;
    }



    @Input
    public boolean getEnforceUniquePackageName() {
        return enforceUniquePackageName;
    }

    public void setEnforceUniquePackageName(boolean enforceUniquePackageName) {
        this.enforceUniquePackageName = enforceUniquePackageName;
    }

    @Input
    public String getTypeAsString() {
        return type.name();
    }

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
    public FileCollection getFeatureResourcePackages() {
        return featureResourcePackages;
    }

    @Input
    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return splitHandlingPolicy;
    }

    @Input
    public String getOriginalApplicationId() {
        return originalApplicationId;
    }

    @InputFiles
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
