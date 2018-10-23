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
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.SplitList;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.v2.Aapt2DaemonManager;
import com.android.builder.internal.aapt.v2.Aapt2Exception;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.ApkInfo;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.workers.WorkerExecutorException;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
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
import org.gradle.api.tasks.TaskProvider;
import org.gradle.tooling.BuildException;
import org.gradle.workers.WorkerExecutor;

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

    @Nullable private Supplier<Integer> resOffsetSupplier = null;

    private MultiOutputPolicy multiOutputPolicy;

    private VariantType type;

    @Nullable private FileCollection aapt2FromMaven;

    private boolean debuggable;

    private AaptOptions aaptOptions;

    private File mergeBlameLogFolder;

    private InstantRunBuildContext buildContext;

    @Nullable private FileCollection featureResourcePackages;

    private Supplier<String> originalApplicationId;

    private String buildTargetDensity;

    private boolean useConditionalKeepRules;

    private File resPackageOutputFolder;

    private String projectBaseName;

    private InternalArtifactType taskInputType;

    private boolean isNamespaced = false;

    @Input
    public boolean getUseConditionalKeepRules() {
        return useConditionalKeepRules;
    }

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

    @Input
    public boolean canHaveSplits() {
        return variantScope.getType().getCanHaveSplits();
    }

    @NonNull
    @Internal
    private Set<String> getSplits(@NonNull SplitList splitList) {
        return splitList.getSplits(multiOutputPolicy);
    }

    @Input
    public String getApplicationId() {
        return applicationId.get();
    }

    SplitList splitList;

    private Supplier<String> applicationId;

    private File supportDirectory;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getApkList() {
        return apkList;
    }

    private BuildableArtifact apkList;

    private BuildableArtifact convertedLibraryDependencies;

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public BuildableArtifact getConvertedLibraryDependencies() {
        return convertedLibraryDependencies;
    }

    private final WorkerExecutorFacade workers;

    @Inject
    public LinkApplicationAndroidResourcesTask(WorkerExecutor workerExecutor) {
        this.workers = Workers.INSTANCE.getWorker(workerExecutor);
    }

    // FIX-ME : make me incremental !
    @Override
    protected void doFullTaskAction() throws IOException {
        FileUtils.deleteDirectoryContents(resPackageOutputFolder);

        BuildElements manifestBuildElements =
                ExistingBuildElements.from(taskInputType, manifestFiles);

        final Set<File> featureResourcePackages =
                this.featureResourcePackages != null
                        ? this.featureResourcePackages.getFiles()
                        : ImmutableSet.of();

        Set<File> dependencies =
                dependenciesFileCollection != null
                        ? dependenciesFileCollection.getFiles()
                        : Collections.emptySet();
        Set<File> imports =
                sharedLibraryDependencies != null
                        ? sharedLibraryDependencies.getFiles()
                        : Collections.emptySet();
        {
            Aapt2ServiceKey aapt2ServiceKey =
                    Aapt2DaemonManagerService.registerAaptService(
                            aapt2FromMaven, getBuildTools(), getILogger());

            // do a first pass at the list so we generate the code synchronously since it's required
            // by the full splits asynchronous processing below.
            List<BuildOutput> unprocessedManifest =
                    manifestBuildElements.stream().collect(Collectors.toList());

            BuildOutput mainOutput = chooseOutput(manifestBuildElements);

            unprocessedManifest.remove(mainOutput);
            new AaptSplitInvoker(
                            new AaptSplitInvokerParams(
                                    mainOutput,
                                    dependencies,
                                    imports,
                                    splitList,
                                    featureResourcePackages,
                                    mainOutput.getApkInfo(),
                                    true,
                                    aapt2ServiceKey,
                                    this))
                    .run();
            // now all remaining splits will be generated asynchronously.
            if (variantScope.getType().getCanHaveSplits()) {
                for (BuildOutput manifestBuildOutput : unprocessedManifest) {
                    ApkInfo apkInfo = manifestBuildOutput.getApkInfo();
                    if (apkInfo.requiresAapt()) {
                        workers.submit(
                                AaptSplitInvoker.class,
                                new AaptSplitInvokerParams(
                                        manifestBuildOutput,
                                        dependencies,
                                        imports,
                                        splitList,
                                        featureResourcePackages,
                                        apkInfo,
                                        false,
                                        aapt2ServiceKey,
                                        this));
                    }
                }
            }

            try {
                workers.await();
            } catch (WorkerExecutorException e) {
                throw new BuildException(e.getMessage(), e);
            }
        }

        if (multiOutputPolicy == MultiOutputPolicy.SPLITS) {
            List<BuildOutput> unprocessedManifest =
                    manifestBuildElements.stream().collect(Collectors.toList());

            for (BuildOutput manifestBuildOutput : unprocessedManifest) {
                ApkInfo apkInfo = manifestBuildOutput.getApkInfo();
                if (apkInfo.getFilters()
                        .stream()
                        .anyMatch(f -> f.getFilterType().equals(VariantOutput.FilterType.ABI))) {
                    // NOTE: This if exists because ABI splits are produced by
                    // GenerateSplitAbiRes, so for ABI splits we're not supposed to find them
                    // here anyway.
                    continue;
                }

                // In case we generated pure splits, we may have more than one
                // resource AP_ in the output directory. reconcile with the
                // splits list and save it for downstream tasks.
                File packagedResForSplit = findPackagedResForSplit(resPackageOutputFolder, apkInfo);

                if (packagedResForSplit != null) {
                    AaptSplitInvoker.appendOutput(
                            new BuildOutput(
                                    InternalArtifactType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                                    apkInfo,
                                    packagedResForSplit),
                            resPackageOutputFolder);
                } else {
                    getLogger().warn("Cannot find output for " + apkInfo);
                }
            }
        }
    }

    BuildOutput chooseOutput(@NonNull BuildElements manifestBuildElements) {
        switch (multiOutputPolicy) {
            case SPLITS:
                java.util.Optional<BuildOutput> main =
                        manifestBuildElements
                                .stream()
                                .filter(
                                        output ->
                                                output.getApkInfo().getType()
                                                        == OutputFile.OutputType.MAIN)
                                .findFirst();
                if (!main.isPresent()) {
                    throw new RuntimeException("No main apk found");
                }
                return main.get();
            case MULTI_APK:
                java.util.Optional<BuildOutput> nonDensity =
                        manifestBuildElements
                                .stream()
                                .filter(
                                        output ->
                                                output.getApkInfo()
                                                                .getFilter(
                                                                        OutputFile.FilterType
                                                                                .DENSITY)
                                                        == null)
                                .findFirst();
                if (!nonDensity.isPresent()) {
                    throw new RuntimeException("No non-density apk found");
                }
                return nonDensity.get();
            default:
                throw new RuntimeException(
                        "Unexpected MultiOutputPolicy type: " + multiOutputPolicy);
        }
    }

    private static File getOutputBaseNameFile(ApkInfo apkInfo, File resPackageOutputFolder) {
        return new File(
                resPackageOutputFolder,
                FN_RES_BASE + RES_QUALIFIER_SEP + apkInfo.getFullName() + SdkConstants.DOT_RES);
    }

    @Nullable
    private static File findPackagedResForSplit(@Nullable File outputFolder, ApkInfo apkInfo) {
        Pattern resourcePattern =
                Pattern.compile(
                        FN_RES_BASE + RES_QUALIFIER_SEP + apkInfo.getFullName() + ".ap__(.*)");

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
                        && isValidSplit(apkInfo, match.group(1))) {
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
    private static boolean isValidSplit(ApkInfo apkInfo, @NonNull String splitWithOptionalSuffix) {

        FilterData splitFilter = apkInfo.getFilter(OutputFile.FilterType.DENSITY);
        if (splitFilter != null) {
            if (splitWithOptionalSuffix.startsWith(splitFilter.getIdentifier())) {
                return true;
            }
        }
        String mangledName = unMangleSplitName(splitWithOptionalSuffix);
        splitFilter = apkInfo.getFilter(OutputFile.FilterType.LANGUAGE);
        return splitFilter != null && mangledName.equals(splitFilter.getIdentifier());
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

    private abstract static class BaseCreationAction
            extends VariantTaskCreationAction<LinkApplicationAndroidResourcesTask> {
        private final boolean generateLegacyMultidexMainDexProguardRules;
        @Nullable private final String baseName;
        private final boolean isLibrary;
        private File resPackageOutputFolder;
        private File proguardOutputFile;
        private File aaptMainDexListProguardOutputFile;

        public BaseCreationAction(
                @NonNull VariantScope scope,
                boolean generateLegacyMultidexMainDexProguardRules,
                @Nullable String baseName,
                boolean isLibrary) {
            super(scope);
            this.generateLegacyMultidexMainDexProguardRules =
                    generateLegacyMultidexMainDexProguardRules;
            this.baseName = baseName;
            this.isLibrary = isLibrary;
        }

        @NonNull
        @Override
        public final String getName() {
            return getVariantScope().getTaskName("process", "Resources");
        }

        @NonNull
        @Override
        public final Class<LinkApplicationAndroidResourcesTask> getType() {
            return LinkApplicationAndroidResourcesTask.class;
        }

        protected void preconditionsCheck(BaseVariantData variantData) {}

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            final VariantScope variantScope = getVariantScope();

            resPackageOutputFolder =
                    variantScope
                            .getArtifacts()
                            .appendArtifact(InternalArtifactType.PROCESSED_RES, taskName, "out");

            if (generatesProguardOutputFile(variantScope)) {
                proguardOutputFile = variantScope.getProcessAndroidResourcesProguardOutputFile();
                variantScope
                        .getArtifacts()
                        .appendArtifact(
                                InternalArtifactType.AAPT_PROGUARD_FILE,
                                ImmutableList.of(proguardOutputFile),
                                taskName);
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                aaptMainDexListProguardOutputFile =
                        variantScope
                                .getArtifacts()
                                .appendArtifact(
                                        InternalArtifactType
                                                .LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
                                        taskName,
                                        "manifest_keep.txt");
            }
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends LinkApplicationAndroidResourcesTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setProcessAndroidResTask(taskProvider);
        }

        @Override
        public void configure(@NonNull LinkApplicationAndroidResourcesTask task) {
            super.configure(task);
            final VariantScope variantScope = getVariantScope();
            final BaseVariantData variantData = variantScope.getVariantData();
            final ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            preconditionsCheck(variantData);

            task.resPackageOutputFolder = resPackageOutputFolder;
            task.aapt2FromMaven = Aapt2MavenUtils.getAapt2FromMaven(variantScope.getGlobalScope());

            task.applicationId = TaskInputHelper.memoize(config::getApplicationId);

            task.setIncrementalFolder(variantScope.getIncrementalDir(getName()));
            if (variantData.getType().getCanHaveSplits()) {
                Splits splits = variantScope.getGlobalScope().getExtension().getSplits();

                ImmutableSet<String> densitySet =
                        splits.getDensity().isEnable()
                                ? ImmutableSet.copyOf(splits.getDensityFilters())
                                : ImmutableSet.of();
                ImmutableSet<String> languageSet =
                        splits.getLanguage().isEnable()
                                ? ImmutableSet.copyOf(splits.getLanguageFilters())
                                : ImmutableSet.of();
                ImmutableSet<String> abiSet =
                        splits.getAbi().isEnable()
                                ? ImmutableSet.copyOf(splits.getAbiFilters())
                                : ImmutableSet.of();
                ImmutableSet<String> resConfigSet =
                        ImmutableSet.copyOf(
                                variantScope
                                        .getVariantConfiguration()
                                        .getMergedFlavor()
                                        .getResourceConfigurations());

                task.splitList = new SplitList(densitySet, languageSet, abiSet, resConfigSet);
            } else {
                task.splitList =
                        new SplitList(
                                ImmutableSet.of(),
                                ImmutableSet.of(),
                                ImmutableSet.of(),
                                ImmutableSet.of());
            }

            task.multiOutputPolicy = variantData.getMultiOutputPolicy();
            task.apkList =
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.APK_LIST);

            if (generatesProguardOutputFile(variantScope)) {
                task.setProguardOutputFile(proguardOutputFile);
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                task.setAaptMainDexListProguardOutputFile(aaptMainDexListProguardOutputFile);
            }

            task.variantScope = variantScope;
            task.outputScope = variantData.getOutputScope();
            task.originalApplicationId = TaskInputHelper.memoize(config::getOriginalApplicationId);

            boolean aaptFriendlyManifestsFilePresent =
                    variantScope
                            .getArtifacts()
                            .hasFinalProduct(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS);
            task.taskInputType =
                    aaptFriendlyManifestsFilePresent
                            ? InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
                            : variantScope.getManifestArtifactType();
            task.setManifestFiles(variantScope.getArtifacts().getFinalProduct(task.taskInputType));

            task.setType(config.getType());
            task.setDebuggable(config.getBuildType().isDebuggable());
            task.setAaptOptions(variantScope.getGlobalScope().getExtension().getAaptOptions());

            task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY);

            task.useConditionalKeepRules = projectOptions.get(BooleanOption.CONDITIONAL_KEEP_RULES);

            task.setMergeBlameLogFolder(variantScope.getResourceBlameLogDir());

            task.buildContext = variantScope.getInstantRunBuildContext();

            VariantType variantType = variantScope.getType();

            // Tests should not have feature dependencies, however because they include the
            // tested production component in their dependency graph, we see the tested feature
            // package in their graph. Therefore we have to manually not set this up for tests.
            task.featureResourcePackages = variantType.isForTesting() ? null :
                    variantScope.getArtifactFileCollection(
                            COMPILE_CLASSPATH, MODULE, FEATURE_RESOURCE_PKG);

            if (variantType.isFeatureSplit()) {
                task.resOffsetSupplier =
                        FeatureSetMetadata.getInstance()
                                .getResOffsetSupplierForTask(variantScope, task);
            }

            task.projectBaseName = baseName;
            task.isLibrary = isLibrary;
            task.supportDirectory =
                    new File(variantScope.getInstantRunSplitApkOutputFolder(), "resources");
        }
    }

    public static final class CreationAction extends BaseCreationAction {
        protected final Supplier<File> symbolLocation;
        private final File symbolsWithPackageNameOutputFile;
        private final TaskManager.MergeType sourceArtifactType;
        private File sourceOutputDir;

        public CreationAction(
                @NonNull VariantScope scope,
                @NonNull Supplier<File> symbolLocation,
                @NonNull File symbolsWithPackageNameOutputFile,
                boolean generateLegacyMultidexMainDexProguardRules,
                @NonNull TaskManager.MergeType sourceArtifactType,
                @NonNull String baseName,
                boolean isLibrary) {
            super(scope, generateLegacyMultidexMainDexProguardRules, baseName, isLibrary);
            this.symbolLocation = symbolLocation;
            this.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile;
            this.sourceArtifactType = sourceArtifactType;
        }

        @Override
        protected final void preconditionsCheck(BaseVariantData variantData) {
            if (variantData.getType().isAar()) {
                throw new IllegalArgumentException("Use GenerateLibraryRFileTask");
            } else {
                Preconditions.checkState(
                        sourceArtifactType == TaskManager.MergeType.MERGE,
                        "source output type should be MERGE",
                        sourceArtifactType);
            }
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            sourceOutputDir =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES,
                                    taskName,
                                    SdkConstants.FD_RES_CLASS);
        }

        @Override
        public final void configure(@NonNull LinkApplicationAndroidResourcesTask task) {
            super.configure(task);

            // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
            task.sourceOutputDir = sourceOutputDir;

            task.dependenciesFileCollection =
                    getVariantScope()
                            .getArtifactFileCollection(
                                    RUNTIME_CLASSPATH,
                                    ALL,
                                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME);

            task.inputResourcesDir =
                    getVariantScope()
                            .getArtifacts()
                            .getFinalArtifactFiles(sourceArtifactType.getOutputType());

            task.textSymbolOutputDir = symbolLocation;
            task.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile;
        }
    }

    /**
     * TODO: extract in to a separate task implementation once splits are calculated in the split
     * discovery task.
     */
    public static final class NamespacedCreationAction extends BaseCreationAction {
        private File sourceOutputDir;

        public NamespacedCreationAction(
                @NonNull VariantScope scope,
                boolean generateLegacyMultidexMainDexProguardRules,
                @Nullable String baseName) {
            super(scope, generateLegacyMultidexMainDexProguardRules, baseName, false);
        }


        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);

            sourceOutputDir =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.RUNTIME_R_CLASS_SOURCES, taskName, "out");
        }

        @Override
        public final void configure(@NonNull LinkApplicationAndroidResourcesTask task) {
            super.configure(task);

            final VariantScope variantScope = getVariantScope();
            final ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();

            task.sourceOutputDir = sourceOutputDir;

            List<FileCollection> dependencies = new ArrayList<>(2);
            dependencies.add(
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.RES_STATIC_LIBRARY)
                            .get());
            dependencies.add(
                    variantScope.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY));
            if (variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()
                    && projectOptions.get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
                task.convertedLibraryDependencies =
                        variantScope
                                .getArtifacts()
                                .getArtifactFiles(
                                        InternalArtifactType
                                                .RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES);
            }

            task.dependenciesFileCollection =
                    variantScope.getGlobalScope().getProject().files(dependencies);

            task.sharedLibraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY);

            task.isNamespaced = true;
        }
    }

    @Optional
    @Input
    public Integer getResOffset() {
        return resOffsetSupplier != null ? resOffsetSupplier.get() : null;
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

    @Nullable private BuildableArtifact inputResourcesDir;

    @Nullable
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getInputResourcesDir() {
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
        return type.getName();
    }

    @Internal
    public VariantType getType() {
        return type;
    }

    public void setType(VariantType type) {
        this.type = type;
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
    @Nullable
    @Optional
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
        return originalApplicationId.get();
    }

    @Nested
    @Optional
    public SplitList getSplitListInput() {
        return splitList;
    }

    @Input
    @Optional
    public String getBuildTargetDensity() {
        return buildTargetDensity;
    }

    @OutputDirectory
    @NonNull
    public File getResPackageOutputFolder() {
        return resPackageOutputFolder;
    }

    boolean isLibrary;

    @Input
    public boolean isLibrary() {
        return isLibrary;
    }

    @Input
    public boolean isNamespaced() {
        return isNamespaced;
    }

    private static class AaptSplitInvoker implements Runnable {
        private AaptSplitInvokerParams params;

        @Inject
        AaptSplitInvoker(AaptSplitInvokerParams params) {
            this.params = params;
        }

        @Override
        public void run() {
            try {
                invokeAaptForSplit(params);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static synchronized void appendOutput(
                BuildOutput output, File resPackageOutputFolder) throws IOException {
            List<BuildOutput> buildOutputs =
                    new ArrayList<>(
                            ExistingBuildElements.from(resPackageOutputFolder).getElements());
            buildOutputs.add(output);
            new BuildElements(buildOutputs).save(resPackageOutputFolder);
        }

        private static void invokeAaptForSplit(@NonNull AaptSplitInvokerParams params)
                throws IOException {

            ImmutableList.Builder<File> featurePackagesBuilder = ImmutableList.builder();
            for (File featurePackage : params.featureResourcePackages) {
                BuildElements buildElements =
                        ExistingBuildElements.from(
                                InternalArtifactType.PROCESSED_RES, featurePackage);
                if (!buildElements.isEmpty()) {
                    BuildOutput mainBuildOutput =
                            buildElements.elementByType(VariantOutput.OutputType.MAIN);
                    if (mainBuildOutput != null) {
                        featurePackagesBuilder.add(mainBuildOutput.getOutputFile());
                    } else {
                        throw new IOException(
                                "Cannot find PROCESSED_RES output for "
                                        + params.variantScopeMainSplit);
                    }
                }
            }

            File resOutBaseNameFile =
                    getOutputBaseNameFile(params.apkInfo, params.resPackageOutputFolder);
            File manifestFile = params.manifestOutput.getOutputFile();

            String packageForR = null;
            File srcOut = null;
            File symbolOutputDir = null;
            File proguardOutputFile = null;
            File mainDexListProguardOutputFile = null;
            if (params.generateCode) {
                // workaround for b/74068247. Until that's fixed, if it's a namespaced feature,
                // an extra empty dummy R.java file will be generated as well
                if (params.isNamespaced && params.variantDataType == VariantTypeImpl.FEATURE) {
                    packageForR = "dummy";
                } else {
                    packageForR = params.originalApplicationId;
                }

                // we have to clean the source folder output in case the package name changed.
                srcOut = params.sourceOutputDir;
                if (srcOut != null) {
                    FileUtils.cleanOutputDir(srcOut);
                }

                symbolOutputDir = params.textSymbolOutputDir;
                proguardOutputFile = params.proguardOutputFile;
                mainDexListProguardOutputFile = params.mainDexListProguardOutputFile;
            }

            FilterData densityFilterData = params.apkInfo.getFilter(OutputFile.FilterType.DENSITY);
            String preferredDensity =
                    densityFilterData != null
                            ? densityFilterData.getIdentifier()
                            // if resConfigs is set, we should not use our preferredDensity.
                            : params.resourceConfigs.isEmpty() ? params.buildTargetDensity : null;

            try {

                // If we are in instant run mode and we use a split APK for these resources.
                if (params.isInInstantRunMode
                        && params.patchingPolicy
                                == InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES) {
                    params.supportDirectory.mkdirs();
                    // create a split identification manifest.
                    manifestFile =
                            InstantRunSliceSplitApkBuilder.generateSplitApkManifest(
                                    params.supportDirectory,
                                    IR_APK_FILE_NAME,
                                    () -> params.applicationId,
                                    params.apkInfo.getVersionName(),
                                    params.apkInfo.getVersionCode(),
                                    params.manifestOutput
                                            .getProperties()
                                            .get(SdkConstants.ATTR_MIN_SDK_VERSION));
                }

                // If the new resources flag is enabled and if we are dealing with a library process
                // resources through the new parsers
                {
                    AaptPackageConfig.Builder configBuilder =
                            new AaptPackageConfig.Builder()
                                    .setManifestFile(manifestFile)
                                    .setOptions(params.aaptOptions)
                                    .setCustomPackageForR(packageForR)
                                    .setSymbolOutputDir(symbolOutputDir)
                                    .setSourceOutputDir(srcOut)
                                    .setResourceOutputApk(resOutBaseNameFile)
                                    .setProguardOutputFile(proguardOutputFile)
                                    .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                                    .setVariantType(params.variantType)
                                    .setDebuggable(params.debuggable)
                                    .setResourceConfigs(params.resourceConfigs)
                                    .setSplits(params.multiOutputPolicySplitList)
                                    .setPreferredDensity(preferredDensity)
                                    .setPackageId(params.packageId)
                                    .setAllowReservedPackageId(
                                            params.packageId != null
                                                    && params.packageId
                                                            < FeatureSetMetadata.BASE_ID)
                                    .setDependentFeatures(featurePackagesBuilder.build())
                                    .setImports(params.imports)
                                    .setIntermediateDir(params.incrementalFolder)
                                    .setAndroidJarPath(params.androidJarPath)
                                    .setUseConditionalKeepRules(params.useConditionalKeepRules);

                    if (params.isNamespaced) {
                        ImmutableList.Builder<File> packagedDependencies = ImmutableList.builder();
                        packagedDependencies.addAll(params.dependencies);
                        if (params.convertedLibraryDependenciesPath != null) {
                            try (Stream<Path> list =
                                    Files.list(params.convertedLibraryDependenciesPath)) {
                                list.map(Path::toFile).forEach(packagedDependencies::add);
                            }
                        }
                        configBuilder.setStaticLibraryDependencies(packagedDependencies.build());
                    } else {
                        if (params.generateCode) {
                            configBuilder.setLibrarySymbolTableFiles(params.dependencies);
                        }
                        configBuilder.setResourceDir(checkNotNull(params.inputResourcesDir));
                    }

                    Preconditions.checkNotNull(
                            params.aapt2ServiceKey, "AAPT2 daemon manager service not initialized");
                    try (Aapt2DaemonManager.LeasedAaptDaemon aaptDaemon =
                            Aapt2DaemonManagerService.getAaptDaemon(params.aapt2ServiceKey)) {

                        AndroidBuilder.processResources(
                                aaptDaemon,
                                configBuilder.build(),
                                new LoggerWrapper(
                                        Logging.getLogger(
                                                LinkApplicationAndroidResourcesTask.class)));
                    } catch (Aapt2Exception e) {
                        throw Aapt2ErrorUtils.rewriteLinkException(
                                e, new MergingLog(params.mergeBlameFolder));
                    }

                    if (LOG.isInfoEnabled()) {
                        LOG.info("Aapt output file {}", resOutBaseNameFile.getAbsolutePath());
                    }
                }
                if (params.generateCode
                        && (params.isLibrary || !params.dependencies.isEmpty())
                        && params.symbolsWithPackageNameOutputFile != null) {
                    SymbolIo.writeSymbolListWithPackageName(
                            Preconditions.checkNotNull(
                                            params.textSymbolOutputDir != null
                                                    ? new File(
                                                            params.textSymbolOutputDir,
                                                            SdkConstants.R_CLASS
                                                                    + SdkConstants.DOT_TXT)
                                                    : null)
                                    .toPath(),
                            manifestFile.toPath(),
                            params.symbolsWithPackageNameOutputFile.toPath());
                }
                appendOutput(
                        new BuildOutput(
                                InternalArtifactType.PROCESSED_RES,
                                params.apkInfo,
                                resOutBaseNameFile,
                                params.manifestOutput.getProperties()),
                        params.resPackageOutputFolder);
            } catch (ProcessException e) {
                throw new BuildException(
                        "Failed to process resources, see aapt output above for details.", e);
            }
        }
    }

    private static class AaptSplitInvokerParams implements Serializable {
        private final BuildOutput manifestOutput;
        @NonNull private final Set<File> dependencies;
        private final Set<File> imports;
        private final Set<String> resourceConfigs;
        private final Set<String> multiOutputPolicySplitList;
        @NonNull private final Set<File> featureResourcePackages;
        private final ApkInfo apkInfo;
        private final boolean generateCode;
        @Nullable private final Aapt2ServiceKey aapt2ServiceKey;
        private final ApkData variantScopeMainSplit;
        private final File resPackageOutputFolder;
        private final boolean isNamespaced;
        private final VariantType variantDataType;
        private final String originalApplicationId;
        private final File sourceOutputDir;
        private final File textSymbolOutputDir;
        private final File proguardOutputFile;
        private final File mainDexListProguardOutputFile;
        private final String buildTargetDensity;
        private final boolean isInInstantRunMode;
        private final InstantRunPatchingPolicy patchingPolicy;
        private final File supportDirectory;
        private final String applicationId;
        private final com.android.builder.internal.aapt.AaptOptions aaptOptions;
        private final VariantType variantType;
        private final boolean debuggable;
        private final Integer packageId;
        private final File incrementalFolder;
        private final String androidJarPath;
        private final Path convertedLibraryDependenciesPath;
        private final File inputResourcesDir;
        private final File mergeBlameFolder;
        private final boolean isLibrary;
        private final File symbolsWithPackageNameOutputFile;
        private final boolean useConditionalKeepRules;

        AaptSplitInvokerParams(
                BuildOutput manifestOutput,
                @NonNull Set<File> dependencies,
                Set<File> imports,
                @NonNull SplitList splitList,
                @NonNull Set<File> featureResourcePackages,
                ApkInfo apkInfo,
                boolean generateCode,
                @Nullable Aapt2ServiceKey aapt2ServiceKey,
                @NonNull LinkApplicationAndroidResourcesTask task) {
            this.manifestOutput = manifestOutput;
            this.dependencies = dependencies;
            this.imports = imports;
            this.resourceConfigs = splitList.getResourceConfigs();
            this.multiOutputPolicySplitList = splitList.getSplits(task.multiOutputPolicy);
            this.featureResourcePackages = featureResourcePackages;
            this.apkInfo = apkInfo;
            this.generateCode = generateCode;
            this.aapt2ServiceKey = aapt2ServiceKey;
            variantScopeMainSplit = task.variantScope.getOutputScope().getMainSplit();
            resPackageOutputFolder = task.resPackageOutputFolder;
            isNamespaced = task.isNamespaced;
            variantDataType = task.variantScope.getVariantData().getType();
            originalApplicationId = task.originalApplicationId.get();
            sourceOutputDir = task.getSourceOutputDir();
            textSymbolOutputDir = task.textSymbolOutputDir.get();
            proguardOutputFile = task.getProguardOutputFile();
            mainDexListProguardOutputFile = task.getMainDexListProguardOutputFile();
            buildTargetDensity = task.buildTargetDensity;
            isInInstantRunMode = task.buildContext.isInInstantRunMode();
            patchingPolicy = task.buildContext.getPatchingPolicy();
            supportDirectory = task.supportDirectory;
            applicationId = task.applicationId.get();
            aaptOptions = DslAdaptersKt.convert(task.aaptOptions);
            variantType = task.getType();
            debuggable = task.getDebuggable();
            packageId = task.getResOffset();
            incrementalFolder = task.getIncrementalFolder();
            androidJarPath = task.getBuilder().getTarget().getPath(IAndroidTarget.ANDROID_JAR);
            convertedLibraryDependenciesPath =
                    task.convertedLibraryDependencies == null
                            ? null
                            : BuildableArtifactUtil.singleFile(task.convertedLibraryDependencies)
                                    .toPath();
            inputResourcesDir =
                    task.getInputResourcesDir() == null
                            ? null
                            : BuildableArtifactUtil.singleFile(task.getInputResourcesDir());
            mergeBlameFolder = task.getMergeBlameLogFolder();
            isLibrary = task.isLibrary;
            symbolsWithPackageNameOutputFile = task.symbolsWithPackageNameOutputFile;
            useConditionalKeepRules = task.getUseConditionalKeepRules();
        }
    }
}
