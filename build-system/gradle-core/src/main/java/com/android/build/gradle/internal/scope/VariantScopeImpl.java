/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_CLASSES_OUTPUT;
import static com.android.SdkConstants.FD_DEX;
import static com.android.SdkConstants.FD_MERGED;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_CLASS;
import static com.android.SdkConstants.FD_SOURCE_GEN;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.build.gradle.internal.TaskManager.ATOM_SUFFIX;
import static com.android.build.gradle.internal.TaskManager.DIR_ATOMBUNDLES;
import static com.android.build.gradle.internal.TaskManager.DIR_BUNDLES;
import static com.android.builder.core.VariantType.LIBRARY;
import static com.android.builder.core.VariantType.UNIT_TEST;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.internal.tasks.ResolveDependenciesTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BootClasspathBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.builder.dexing.DexingMode;
import com.android.builder.model.ApiVersion;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * A scope containing data for a specific variant.
 */
public class VariantScopeImpl extends GenericVariantScopeImpl implements VariantScope {

    private static final ILogger LOGGER = LoggerWrapper.getLogger(VariantScopeImpl.class);

    @NonNull
    private GlobalScope globalScope;
    @NonNull
    private BaseVariantData<? extends BaseVariantOutputData> variantData;
    @NonNull
    private TransformManager transformManager;
    @Nullable
    private Collection<Object> ndkBuildable;
    @Nullable
    private Collection<File> ndkSoFolder;
    @Nullable
    private File ndkObjFolder;
    @NonNull
    private Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    @Nullable
    private File mergeResourceOutputDir;

    // Tasks
    private AndroidTask<DefaultTask> assembleTask;
    private AndroidTask<DefaultTask> preBuildTask;
    private AndroidTask<PrepareDependenciesTask> prepareDependenciesTask;
    @Nullable
    private AndroidTask<ResolveDependenciesTask> resolveDependenciesTask;
    private AndroidTask<ProcessAndroidResources> generateRClassTask;

    private AndroidTask<Task> sourceGenTask;
    private AndroidTask<Task> resourceGenTask;
    private AndroidTask<Task> assetGenTask;
    private AndroidTask<CheckManifest> checkManifestTask;

    private AndroidTask<RenderscriptCompile> renderscriptCompileTask;
    private AndroidTask<AidlCompile> aidlCompileTask;
    @Nullable
    private AndroidTask<MergeResources> mergeResourcesTask;
    @Nullable
    private AndroidTask<MergeSourceSetFolders> mergeAssetsTask;
    private AndroidTask<GenerateBuildConfig> generateBuildConfigTask;
    private AndroidTask<GenerateResValues> generateResValuesTask;
    private AndroidTask<ShaderCompile> shaderCompileTask;

    private AndroidTask<Sync> processJavaResourcesTask;
    private AndroidTask<TransformTask> mergeJavaResourcesTask;

    private AndroidTask<MergeSourceSetFolders> mergeJniLibsFolderTask;

    @Nullable
    private AndroidTask<DataBindingProcessLayoutsTask> dataBindingProcessLayoutsTask;
    @Nullable
    private AndroidTask<TransformTask> dataBindingMergeBindingArtifactsTask;

    /** @see BaseVariantData#javaCompilerTask */
    @Nullable
    private AndroidTask<? extends Task> javaCompilerTask;
    @Nullable
    private AndroidTask<? extends JavaCompile> javacTask;

    // empty anchor compile task to set all compilations tasks as dependents.
    private AndroidTask<Task> compileTask;

    private AndroidTask<GenerateApkDataTask> microApkTask;

    @Nullable
    private AndroidTask<ExternalNativeBuildTask> externalNativeBuild;

    @Nullable
    private ExternalNativeJsonGenerator externalNativeJsonGenerator;

    @NonNull
    private List<NativeBuildConfigValue> externalNativeBuildConfigValues = Lists.newArrayList();

    /**
     * This is an instance of {@link JacocoReportTask} in android test variants, an umbrella
     * {@link Task} in app and lib variants and null in unit test variants.
     */
    private AndroidTask<?> coverageReportTask;

    private File resourceOutputDir;

    private InstantRunTaskManager instantRunTaskManager;

    public VariantScopeImpl(
            @NonNull GlobalScope globalScope,
            @NonNull TransformManager transformManager,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        this.globalScope = globalScope;
        this.transformManager = transformManager;
        this.variantData = variantData;
    }

    @Override
    @NonNull
    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    @Override
    @NonNull
    public BaseVariantData<? extends BaseVariantOutputData> getVariantData() {
        return variantData;
    }

    @Override
    @NonNull
    public GradleVariantConfiguration getVariantConfiguration() {
        return variantData.getVariantConfiguration();
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return getVariantConfiguration().getFullName();
    }

    @Override
    public boolean isMinifyEnabled() {
        return getVariantConfiguration().getBuildType().isMinifyEnabled();
    }

    @Override
    public boolean useResourceShrinker() {
        // Cases when resource shrinking is disabled despite the user setting the flag should
        // be explained in TaskManager#maybeCreateShrinkResourcesTransform.

        CoreBuildType buildType = getVariantConfiguration().getBuildType();
        return buildType.isShrinkResources()
                && buildType.isMinifyEnabled()
                && !getInstantRunBuildContext().isInInstantRunMode();
    }

    @Override
    public boolean isJackEnabled() {
        return getVariantConfiguration().isJackEnabled();
    }

    /**
     * Determine if the final output should be marked as testOnly to prevent uploading to Play store.
     *
     * <p>Uploading to Play store is disallowed if:
     *
     * <ul>
     *   <li>An injected option is set (usually by the IDE for testing purposes).
     *   <li>compileSdkVersion, minSdkVersion or targetSdkVersion is a preview
     * </ul>
     *
     * <p>This value can be overridden by the OptionalBooleanOption.IDE_TEST_ONLY property.
     */
    @Override
    public boolean isTestOnly() {
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        Boolean isTestOnlyOverride = projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY);

        if (isTestOnlyOverride != null) {
            return isTestOnlyOverride;
        }

        return !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
                || !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_DENISTY))
                || projectOptions.get(IntegerOption.IDE_TARGET_API_LEVEL) != null
                || globalScope.getAndroidBuilder().isPreviewTarget()
                || getMinSdkVersion().getCodename() != null
                || getVariantConfiguration().getTargetSdkVersion().getCodename() != null;
    }

    @NonNull
    @Override
    public DexingMode getDexingMode() {
        if (variantData.getType().isForTesting()
                && getTestedVariantData() != null
                && getTestedVariantData().getType() != VariantType.LIBRARY) {
            // for non-library test variants, we always want to have exactly one DEX file
            return DexingMode.MONO_DEX;
        } else if (isInstantRunDexingModeOverride()) {
            return DexingMode.NATIVE_MULTIDEX;
        } else if (variantData.getVariantConfiguration().isLegacyMultiDexMode()) {
            return DexingMode.LEGACY_MULTIDEX;
        } else if (variantData.getVariantConfiguration().isMultiDexEnabled()) {
            return DexingMode.NATIVE_MULTIDEX;
        } else {
            return DexingMode.MONO_DEX;
        }
    }

    private boolean isInstantRunDexingModeOverride() {
        return getInstantRunBuildContext().isInInstantRunMode()
                && getInstantRunBuildContext().getPatchingPolicy()
                        == InstantRunPatchingPolicy.MULTI_APK;
    }

    @NonNull
    @Override
    public ApiVersion getMinSdkVersion() {
        return getVariantConfiguration().getMinSdkVersion();
    }

    @NonNull
    @Override
    public String getDirName() {
        return variantData.getVariantConfiguration().getDirName();
    }

    @NonNull
    @Override
    public Collection<String> getDirectorySegments() {
        return variantData.getVariantConfiguration().getDirectorySegments();
    }

    @NonNull
    @Override
    public TransformManager getTransformManager() {
        return transformManager;
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix) {
        return getTaskName(prefix, "");
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        if (variantData.getType() == VariantType.ATOM) suffix = ATOM_SUFFIX + suffix;
        return prefix + StringHelper.capitalize(getVariantConfiguration().getFullName()) + suffix;
    }

    @Override
    @Nullable
    public Collection<Object> getNdkBuildable() {
        return ndkBuildable;
    }

    @Override
    public void setNdkBuildable(@NonNull Collection<Object> ndkBuildable) {
        this.ndkBuildable = ndkBuildable;
    }

    @Nullable
    @Override
    public AndroidTask<DataBindingProcessLayoutsTask> getDataBindingProcessLayoutsTask() {
        return dataBindingProcessLayoutsTask;
    }

    @Override
    public void setDataBindingProcessLayoutsTask(
            @Nullable AndroidTask<DataBindingProcessLayoutsTask> dataBindingProcessLayoutsTask) {
        this.dataBindingProcessLayoutsTask = dataBindingProcessLayoutsTask;
    }

    @Override
    public void setDataBindingMergeArtifactsTask(
            @Nullable AndroidTask<TransformTask> mergeArtifactsTask) {
        this.dataBindingMergeBindingArtifactsTask = mergeArtifactsTask;
    }

    @Nullable
    @Override
    public AndroidTask<TransformTask> getDataBindingMergeArtifactsTask() {
        return dataBindingMergeBindingArtifactsTask;
    }

    @Override
    @Nullable
    public Collection<File> getNdkSoFolder() {
        return ndkSoFolder;
    }

    @Override
    public void setNdkSoFolder(@NonNull Collection<File> ndkSoFolder) {
        this.ndkSoFolder = ndkSoFolder;
    }

    @Override
    @Nullable
    public File getNdkObjFolder() {
        return ndkObjFolder;
    }

    @Override
    public void setNdkObjFolder(@NonNull File ndkObjFolder) {
        this.ndkObjFolder = ndkObjFolder;
    }

    /**
     * Return the folder containing the shared object with debugging symbol for the specified ABI.
     */
    @Override
    @Nullable
    public File getNdkDebuggableLibraryFolders(@NonNull Abi abi) {
        return ndkDebuggableLibraryFolders.get(abi);
    }

    @Override
    public void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath) {
        this.ndkDebuggableLibraryFolders.put(abi, searchPath);
    }

    @Override
    @Nullable
    public BaseVariantData getTestedVariantData() {
        return variantData instanceof TestVariantData ?
                (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData() :
                null;
    }

    @NonNull
    @Override
    public File getDexOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/dex/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getDexOutputFolder(@NonNull AtomDependency androidAtom) {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                FD_DEX,
                androidAtom.getAtomName() + "-" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getReloadDexOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/reload-dex/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRestartDexOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/restart-dex/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/split-apk/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunPastIterationsFolder() {
        return new File(globalScope.getIntermediatesDir(), "/builds/" + getVariantConfiguration().getDirName());
    }

    // Precomputed file paths.

    @Override
    @NonNull
    public FileCollection getJavaClasspath() {
        return getGlobalScope()
                .getProject()
                .files(
                        getGlobalScope()
                                .getAndroidBuilder()
                                .getCompileClasspath(variantData.getVariantConfiguration()));
    }

    @Override
    public boolean keepDefaultBootstrap() {
        // javac 1.8 may generate code that uses class not available in android.jar.  This is fine
        // if jack or desugar are used to compile code for the app or compile task is created only
        // for unit test. In those cases, we want to keep the default bootstrap classpath.
        if (!JavaVersion.current().isJava8Compatible()) {
            return false;
        }

        VariantScope.Java8LangSupport java8LangSupport = getJava8LangSupportType();
        if (java8LangSupport == VariantScope.Java8LangSupport.JACK) {
            return true;
        }

        // only if target and source is explicitly specified to 1.8 (and above), we keep the
        // default bootclasspath with Desugar. Otherwise, we use android.jar.
        CompileOptions compileOptions = getGlobalScope().getExtension().getCompileOptions();
        return java8LangSupport == VariantScope.Java8LangSupport.DESUGAR
                && compileOptions.getTargetCompatibility().isJava8Compatible()
                && compileOptions.getSourceCompatibility().isJava8Compatible();
    }

    @NonNull
    @Override
    public FileCollection getPreJavacClasspath() {
        Project project = globalScope.getProject();
        boolean keepDefaultBootstrap = keepDefaultBootstrap();
        final BaseVariantData testedVariantData = getTestedVariantData();

        ConfigurableFileCollection classpath = project.files();
        classpath.from(getJavaClasspath());

        if (keepDefaultBootstrap) {
            classpath.from(project.files(globalScope.getAndroidBuilder().getBootClasspath(false)));
        }

        if (testedVariantData != null) {
            // add the generated bytecode of the tested component
            classpath.from(testedVariantData.getGeneratedBytecodeCollection());

            final VariantType testedType = testedVariantData.getType();
            final VariantType type = variantData.getType();

            if (testedType != LIBRARY || type == UNIT_TEST) {
                // For libraries, the classpath from androidBuilder includes the library
                // output (bundle/classes.jar) as a normal dependency. In unit tests we
                // don't want to package the jar at every run, so we use the *.class
                // files instead.
                classpath.from(
                        project.files(
                                testedVariantData.getScope().getJavaClasspath(),
                                testedVariantData.getScope().getJavaOutputDir()));
            }

            if (testedType == LIBRARY) {
                if (type == UNIT_TEST) {
                    // The bundled classes.jar may exist, but it's probably old. Don't
                    // use it, we already have the *.class files in the classpath.
                    AndroidDependency testedLibrary =
                            testedVariantData.getVariantConfiguration().getOutput();
                    if (testedLibrary != null) {
                        return classpath.minus(project.files(testedLibrary.getJarFile()));
                    }
                } else {
                    // here we want to use the library's bundle jar. Add a dependency on it.
                    // This is to ensure that the external compiler, using the bytecode generation
                    // hooks are properly included in the dependency of the task consuming this
                    // file collection.
                    // FIXME: fix this to handle all dependencies the same way (this will be fixed in 2.5+)
                    LibVariantOutputData output =
                            ((LibraryVariantData) testedVariantData).getMainOutput();
                    classpath.builtBy(output.packageLibTask);
                }
            }
        }

        return classpath;
    }

    @Override
    @NonNull
    public File getJavaOutputDir() {
        return new File(globalScope.getIntermediatesDir(), "/classes/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getJavaOutputDir(@NonNull AtomDependency androidAtom) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                FD_CLASSES_OUTPUT,
                androidAtom.getAtomName() + "-" +
                        variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunSupportDir() {
        return new File(globalScope.getIntermediatesDir(), "/instant-run-support/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunSliceSupportDir() {
        return new File(globalScope.getIntermediatesDir(), "/instant-run-slices/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getIncrementalRuntimeSupportJar() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-runtime-classes/" +
                variantData.getVariantConfiguration().getDirName() + "/instant-run.jar");
    }

    @Override
    @NonNull
    public File getIncrementalApplicationSupportDir() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-classes/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunResourcesFile() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "instant-run-resources",
                "resources-" + variantData.getVariantConfiguration().getDirName() + ".ir.ap_");
    }

    @Override
    @NonNull
    public File getIncrementalVerifierDir() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-verifier/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public Iterable<File> getJavaOutputs() {
        return Iterables.concat(getJavaClasspath(), ImmutableList.of(getJavaOutputDir()));
    }

    @Override
    @NonNull
    public File getPreDexOutputDir() {
        return new File(globalScope.getIntermediatesDir(), "/pre-dexed/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getProguardOutputFile() {
        return (variantData instanceof LibraryVariantData) ?
                new File(getBaseBundleDir(), "classes.jar") :
                new File(globalScope.getIntermediatesDir(),
                        "/classes-proguard/" + getVariantConfiguration().getDirName()
                                + "/classes.jar");
    }

    @Override
    @NonNull
    public File getProguardComponentsJarFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/componentClasses.jar");
    }

    @Override
    @NonNull
    public File getJarMergingOutputFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/allclasses.jar");
    }

    @Override
    @NonNull
    public File getManifestKeepListProguardFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/manifest_keep.txt");
    }

    @Override
    @NonNull
    public File getMainDexListFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/maindexlist.txt");
    }

    @Override
    @NonNull
    public File getRenderscriptSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/rs/" + variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRenderscriptLibOutputDir() {
        return new File(globalScope.getIntermediatesDir(),
                "rs/" + variantData.getVariantConfiguration().getDirName() + "/lib");
    }

    @Override
    @NonNull
    public File getSymbolLocation() {
        return new File(globalScope.getIntermediatesDir() + "/symbols/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getFinalResourcesDir() {
        return Objects.firstNonNull(resourceOutputDir, getDefaultMergeResourcesOutputDir());
    }

    @Override
    public void setResourceOutputDir(@NonNull File resourceOutputDir) {
        this.resourceOutputDir = resourceOutputDir;
    }

    @Override
    @NonNull
    public File getDefaultMergeResourcesOutputDir() {
        if (variantData.getType() == VariantType.ATOM) {
            return FileUtils.join(getBaseBundleDir(), FD_RES);
        } else {
            return FileUtils.join(getGlobalScope().getIntermediatesDir(),
                    FD_RES,
                    FD_MERGED,
                    getVariantConfiguration().getDirName());
        }
    }

    @Override
    @NonNull
    public File getMergeResourcesOutputDir() {
        if (mergeResourceOutputDir == null) {
            return getDefaultMergeResourcesOutputDir();
        }
        return mergeResourceOutputDir;
    }

    @Override
    public void setMergeResourceOutputDir(@Nullable File mergeResourceOutputDir) {
        this.mergeResourceOutputDir = mergeResourceOutputDir;
    }

    @NonNull
    @Override
    public File getResourceBlameLogDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "blame", "res", getDirectorySegments()));
    }

    @NonNull
    @Override
    public File getResourceBlameLogDir(@NonNull AtomDependency androidAtom) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "blame", "res", androidAtom.getAtomName(), getDirectorySegments()));
    }

    @Override
    @NonNull
    public File getMergeAssetsOutputDir() {
        return getVariantConfiguration().isBundled() ?
                new File(getBaseBundleDir(), FD_ASSETS) :
                FileUtils.join(globalScope.getIntermediatesDir(),
                        FD_ASSETS, getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getMergeNativeLibsOutputDir() {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                "/jniLibs/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getMergeShadersOutputDir() {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                "/shaders/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getBuildConfigSourceOutputDir() {
        return new File(globalScope.getBuildDir() + "/"  + FD_GENERATED + "/source/buildConfig/"
                + variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getGeneratedResourcesDir(String name) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                StringHelper.toStrings(
                        "res",
                        name,
                        getDirectorySegments()));
    }

    @NonNull
    private File getGeneratedAssetsDir(String name) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                StringHelper.toStrings(
                        "assets",
                        name,
                        getDirectorySegments()));
    }

    @Override
    @NonNull
    public File getGeneratedResOutputDir() {
        return getGeneratedResourcesDir("resValues");
    }

    @Override
    @NonNull
    public File getGeneratedPngsOutputDir() {
        return getGeneratedResourcesDir("pngs");
    }

    @Override
    @NonNull
    public File getRenderscriptResOutputDir() {
        return getGeneratedResourcesDir("rs");
    }

    @NonNull
    @Override
    public File getRenderscriptObjOutputDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "rs",
                        getDirectorySegments(),
                        "obj"));
    }

    @NonNull
    @Override
    public File getShadersOutputDir() {
        return getGeneratedAssetsDir("shaders");
    }

    @Override
    @NonNull
    public File getPackagedJarsJavaResDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "packagedJarsJavaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getSourceFoldersJavaResDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "sourceFolderJavaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getJavaResourcesDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "javaResources/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getGeneratedJavaResourcesDir() {
        return new File(
                globalScope.getGeneratedDir(),
                "javaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRClassSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/r/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRClassSourceOutputDir(@NonNull AtomDependency atomDependency) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                FD_SOURCE_GEN,
                FD_RES_CLASS,
                atomDependency.getAtomName() + "-" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getAidlSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/aidl/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getIncrementalDir(String name) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "incremental",
                name);
    }

    @Override
    @NonNull
    public File getPackagedAidlDir() {
        return new File(getBaseBundleDir(), "aidl");
    }

    @NonNull
    @Override
    public File getTypedefFile() {
        return new File(globalScope.getIntermediatesDir(), "typedefs.txt");
    }

    @NonNull
    @Override
    public File getJackEcjOptionsFile() {
        return new File(globalScope.getIntermediatesDir(),
                "jack/" + getDirName() + "/ecj-options.txt");
    }

    @Override
    @NonNull
    public File getJackClassesZip() {
        return new File(globalScope.getIntermediatesDir(),
                "packaged/" + getVariantConfiguration().getDirName() + "/classes.zip");
    }

    @Override
    @NonNull
    public File getJackCoverageMetadataFile() {
        return new File(globalScope.getIntermediatesDir(), "jack/" + getDirName() + "/coverage.em");
    }

    @NonNull
    @Override
    public File getCoverageReportDir() {
        return new File(globalScope.getReportsDir(), "coverage/" + getDirName());
    }

    @Override
    @NonNull
    public File getClassOutputForDataBinding() {
        return new File(globalScope.getGeneratedDir(),
                "source/dataBinding/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getLayoutInfoOutputForDataBinding() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-info/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getLayoutFolderOutputForDataBinding() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-layout-out/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getBuildFolderForDataBindingCompiler() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-compiler/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getGeneratedClassListOutputFileForDataBinding() {
        return new File(getLayoutInfoOutputForDataBinding(), "_generated.txt");
    }

    @NonNull
    @Override
    public File getBundleFolderForDataBinding() {
        return new File(getBaseBundleDir(), DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR);
    }

    @Override
    @NonNull
    public File getProguardOutputFolder() {
        return new File(globalScope.getBuildDir(), "/" + FD_OUTPUTS + "/mapping/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getProcessAndroidResourcesProguardOutputFile() {
        return new File(globalScope.getIntermediatesDir(),
                "/proguard-rules/" + getVariantConfiguration().getDirName() + "/aapt_rules.txt");
    }

    @Override
    public File getMappingFile() {
        return new File(globalScope.getOutputsDir(),
                "/mapping/" + getVariantConfiguration().getDirName() + "/mapping.txt");
    }

    @Override
    @NonNull
    public File getGenerateSplitAbiResOutputDirectory() {
        return new File(globalScope.getIntermediatesDir(),
                "abi/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getSplitOutputDirectory() {
        return new File(globalScope.getIntermediatesDir(),
                "splits/" + getVariantConfiguration().getDirName());
    }


    @Override
    @NonNull
    public List<File> getSplitAbiResOutputFiles() {
        Set<String> filters = AbiSplitOptions.getAbiFilters(
                globalScope.getExtension().getSplits().getAbiFilters());
        return filters.stream()
                .map(this::getOutputFileForSplit)
                .collect(Collectors.toList());
    }

    private File getOutputFileForSplit(final String split) {
        return new File(getGenerateSplitAbiResOutputDirectory(),
                "resources-" + getVariantConfiguration().getBaseName() + "-" + split + ".ap_");
    }

    @Override
    @NonNull
    public List<File> getPackageSplitAbiOutputFiles() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        for (String split : globalScope.getExtension().getSplits().getAbiFilters()) {
            String apkName = getApkName(split);
            builder.add(new File(getSplitOutputDirectory(), apkName));
        }
        return builder.build();
    }

    private String getApkName(final String split) {
        String archivesBaseName = globalScope.getArchivesBaseName();
        String apkName =
                archivesBaseName + "-" + getVariantConfiguration().getBaseName() + "_" + split;
        return apkName
                + (getVariantConfiguration().getSigningConfig() == null
                        ? "-unsigned.apk"
                        : "-unaligned.apk");
    }

    @NonNull
    @Override
    public File getPackageAtom(@NonNull AtomDependency androidAtom) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "atoms",
                getVariantConfiguration().getDirName(),
                androidAtom.getAtomName() + DOT_ANDROID_PACKAGE);
    }

    @NonNull
    @Override
    public File getAaptFriendlyManifestOutputFile() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "manifests",
                "aapt",
                getVariantConfiguration().getDirName(),
                "AndroidManifest.xml");
    }

    @NonNull
    @Override
    public File getInstantRunManifestOutputFile() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "manifests",
                "instant-run",
                getVariantConfiguration().getDirName(),
                "AndroidManifest.xml");
    }

    @NonNull
    @Override
    public File getManifestReportFile() {
        // If you change this, please also update Lint#getManifestReportFile
        return FileUtils.join(getGlobalScope().getOutputsDir(),
                "logs", "manifest-merger-" + variantData.getVariantConfiguration().getBaseName()
                        + "-report.txt");
    }

    @NonNull
    @Override
    public File getMicroApkManifestFile() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "manifests",
                "microapk",
                getVariantConfiguration().getDirName(),
                FN_ANDROID_MANIFEST_XML);
    }

    @NonNull
    @Override
    public File getMicroApkResDirectory() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "res",
                "microapk",
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getBaseBundleDir() {
        // The base bundle dir must be recomputable from outside of this project.
        // DirName is a set for folders (flavor1/flavor2/buildtype) which is difficult to
        // recompute if all you have is the fullName (flavor1Flavor2Buildtype) as it would
        // require string manipulation which could break if a flavor is using camelcase in
        // its name (myFlavor).
        // So here we use getFullName directly. It's a direct match with the externally visible
        // variant name (which is == to getFullName), and set as the published artifact's
        // classifier.
        // However if there is only a single published artifact, then the bundle is set to a
        // standard name ('default', which cannot be a variant name anyway), since the consuming
        // side doesn't know what the variant name is (no coordinate classifier in this case). Note
        // that this only applies to the published artifact. Non published artifact always use their
        // name no matter what (necessary for testing)
        // See DependencyManager.addDependency

        String leaf;
        String variantName = getVariantConfiguration().getFullName();
        if (globalScope.getExtension().getPublishNonDefault() ||
                !variantName.equals(globalScope.getExtension().getDefaultPublishConfig())) {
            leaf = variantName;
        } else {
            leaf = "default";
        }
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                getVariantConfiguration().getType() == VariantType.ATOM ?
                        DIR_ATOMBUNDLES : DIR_BUNDLES,
                leaf);
    }

    @NonNull
    @Override
    public File getOutputBundleFile() {
        String extension =
                getVariantConfiguration().getType() == VariantType.ATOM
                        ? BuilderConstants.EXT_ATOMBUNDLE_ARCHIVE
                        : BuilderConstants.EXT_LIB_ARCHIVE;
        return FileUtils.join(
                globalScope.getOutputsDir(),
                extension,
                globalScope.getProjectBaseName()
                        + "-"
                        + getVariantConfiguration().getBaseName()
                        + "."
                        + extension);
    }

    @NonNull
    @Override
    public File getAnnotationProcessorOutputDir() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "source",
                "apt",
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getMainJarOutputDir() {
        if (getVariantConfiguration().getType() == VariantType.ATOM)
            return getBaseBundleDir();
        else
            return FileUtils.join(globalScope.getIntermediatesDir(),
                    "packaged",
                    getVariantConfiguration().getDirName());
    }

    // Tasks getters/setters.

    @Override
    public AndroidTask<DefaultTask> getAssembleTask() {
        return assembleTask;
    }

    @Override
    public void setAssembleTask(@NonNull AndroidTask<DefaultTask> assembleTask) {
        this.assembleTask = assembleTask;
    }

    @Override
    public AndroidTask<DefaultTask> getPreBuildTask() {
        return preBuildTask;
    }

    @Override
    public void setPreBuildTask(
            AndroidTask<DefaultTask> preBuildTask) {
        this.preBuildTask = preBuildTask;
    }

    @Override
    public AndroidTask<PrepareDependenciesTask> getPrepareDependenciesTask() {
        return prepareDependenciesTask;
    }

    @Override
    public void setPrepareDependenciesTask(
            AndroidTask<PrepareDependenciesTask> prepareDependenciesTask) {
        this.prepareDependenciesTask = prepareDependenciesTask;
    }

    @Override
    public AndroidTask<ResolveDependenciesTask> getResolveDependenciesTask() {
        return resolveDependenciesTask;
    }

    @Override
    public void setResolveDependenciesTask(
            AndroidTask<ResolveDependenciesTask> resolveDependenciesTask) {
        this.resolveDependenciesTask = resolveDependenciesTask;
    }

    @Override
    public AndroidTask<ProcessAndroidResources> getGenerateRClassTask() {
        return generateRClassTask;
    }

    @Override
    public void setGenerateRClassTask(
            AndroidTask<ProcessAndroidResources> generateRClassTask) {
        this.generateRClassTask = generateRClassTask;
    }

    @Override
    public AndroidTask<Task> getSourceGenTask() {
        return sourceGenTask;
    }

    @Override
    public void setSourceGenTask(
            AndroidTask<Task> sourceGenTask) {
        this.sourceGenTask = sourceGenTask;
    }

    @Override
    public AndroidTask<Task> getResourceGenTask() {
        return resourceGenTask;
    }

    @Override
    public void setResourceGenTask(
            AndroidTask<Task> resourceGenTask) {
        this.resourceGenTask = resourceGenTask;
    }

    @Override
    public AndroidTask<Task> getAssetGenTask() {
        return assetGenTask;
    }

    @Override
    public void setAssetGenTask(
            AndroidTask<Task> assetGenTask) {
        this.assetGenTask = assetGenTask;
    }

    @Override
    public AndroidTask<CheckManifest> getCheckManifestTask() {
        return checkManifestTask;
    }

    @Override
    public void setCheckManifestTask(
            AndroidTask<CheckManifest> checkManifestTask) {
        this.checkManifestTask = checkManifestTask;
    }

    @Override
    public AndroidTask<RenderscriptCompile> getRenderscriptCompileTask() {
        return renderscriptCompileTask;
    }

    @Override
    public void setRenderscriptCompileTask(
            AndroidTask<RenderscriptCompile> renderscriptCompileTask) {
        this.renderscriptCompileTask = renderscriptCompileTask;
    }

    @Override
    public AndroidTask<AidlCompile> getAidlCompileTask() {
        return aidlCompileTask;
    }

    @Override
    public void setAidlCompileTask(AndroidTask<AidlCompile> aidlCompileTask) {
        this.aidlCompileTask = aidlCompileTask;
    }

    @Override
    public AndroidTask<ShaderCompile> getShaderCompileTask() {
        return shaderCompileTask;
    }

    @Override
    public void setShaderCompileTask(AndroidTask<ShaderCompile> shaderCompileTask) {
        this.shaderCompileTask = shaderCompileTask;
    }

    @Override
    @Nullable
    public AndroidTask<MergeResources> getMergeResourcesTask() {
        return mergeResourcesTask;
    }

    @Override
    public void setMergeResourcesTask(
            @Nullable AndroidTask<MergeResources> mergeResourcesTask) {
        this.mergeResourcesTask = mergeResourcesTask;
    }

    @Override
    @Nullable
    public AndroidTask<MergeSourceSetFolders> getMergeAssetsTask() {
        return mergeAssetsTask;
    }

    @Override
    public void setMergeAssetsTask(
            @Nullable AndroidTask<MergeSourceSetFolders> mergeAssetsTask) {
        this.mergeAssetsTask = mergeAssetsTask;
    }

    @Nullable
    @Override
    public AndroidTask<MergeSourceSetFolders> getMergeJniLibFoldersTask() {
        return mergeJniLibsFolderTask;
    }

    @Override
    public void setMergeJniLibFoldersTask(
            @Nullable AndroidTask<MergeSourceSetFolders> mergeJniLibsFolderTask) {
        this.mergeJniLibsFolderTask = mergeJniLibsFolderTask;
    }

    @Override
    public AndroidTask<GenerateBuildConfig> getGenerateBuildConfigTask() {
        return generateBuildConfigTask;
    }

    @Override
    public void setGenerateBuildConfigTask(
            AndroidTask<GenerateBuildConfig> generateBuildConfigTask) {
        this.generateBuildConfigTask = generateBuildConfigTask;
    }

    @Override
    public AndroidTask<GenerateResValues> getGenerateResValuesTask() {
        return generateResValuesTask;
    }

    @Override
    public void setGenerateResValuesTask(
            AndroidTask<GenerateResValues> generateResValuesTask) {
        this.generateResValuesTask = generateResValuesTask;
    }

    @Override
    public AndroidTask<Sync> getProcessJavaResourcesTask() {
        return processJavaResourcesTask;
    }

    @Override
    public void setProcessJavaResourcesTask(
            AndroidTask<Sync> processJavaResourcesTask) {
        this.processJavaResourcesTask = processJavaResourcesTask;
    }

    @Override
    public void setMergeJavaResourcesTask(
            AndroidTask<TransformTask> mergeJavaResourcesTask) {
        this.mergeJavaResourcesTask = mergeJavaResourcesTask;
    }

    /**
     * Returns the task extracting java resources from libraries and merging those with java
     * resources coming from the variant's source folders.
     * @return the task merging resources.
     */
    @Override
    public AndroidTask<TransformTask> getMergeJavaResourcesTask() {
        return mergeJavaResourcesTask;
    }

    @Override
    @Nullable
    public AndroidTask<? extends Task> getJavaCompilerTask() {
        return javaCompilerTask;
    }

    @Override
    @Nullable
    public AndroidTask<? extends  JavaCompile> getJavacTask() {
        return javacTask;
    }

    @Override
    public void setJavacTask(
            @Nullable AndroidTask<? extends JavaCompile> javacTask) {
        this.javacTask = javacTask;
    }

    @Override
    public void setJavaCompilerTask(
            @NonNull AndroidTask<? extends Task> javaCompileTask) {
        this.javaCompilerTask = javaCompileTask;
    }

    @Override
    public AndroidTask<Task> getCompileTask() {
        return compileTask;
    }

    @Override
    public void setCompileTask(
            AndroidTask<Task> compileTask) {
        this.compileTask = compileTask;
    }

    @Override
    public AndroidTask<GenerateApkDataTask> getMicroApkTask() {
        return microApkTask;
    }

    @Override
    public void setMicroApkTask(
            AndroidTask<GenerateApkDataTask> microApkTask) {
        this.microApkTask = microApkTask;
    }

    @Override
    public AndroidTask<?> getCoverageReportTask() {
        return coverageReportTask;
    }

    @Override
    public void setCoverageReportTask(AndroidTask<?> coverageReportTask) {
        this.coverageReportTask = coverageReportTask;
    }

    @NonNull
    private InstantRunBuildContext instantRunBuildContext = new InstantRunBuildContext();

    @Override
    @NonNull
    public InstantRunBuildContext getInstantRunBuildContext() {
        return instantRunBuildContext;
    }


    @NonNull
    @Override
    public ImmutableList<File> getInstantRunBootClasspath() {
        SdkHandler sdkHandler = getGlobalScope().getSdkHandler();
        AndroidBuilder androidBuilder = globalScope.getAndroidBuilder();
        IAndroidTarget androidBuilderTarget = androidBuilder.getTarget();

        Preconditions.checkState(
                androidBuilderTarget != null,
                "AndroidBuilder target not initialized.");

        File annotationsJar = sdkHandler.getSdkLoader().getSdkInfo(LOGGER).getAnnotationsJar();

        AndroidVersion targetDeviceVersion =
                AndroidGradleOptions.getTargetAndroidVersion(getGlobalScope().getProject());

        if (targetDeviceVersion.equals(androidBuilderTarget.getVersion())) {
            // Compile SDK and the target device match, re-use the target that we have already
            // found earlier.
            return BootClasspathBuilder.computeFullBootClasspath(
                    androidBuilderTarget, annotationsJar);
        }

        IAndroidTarget targetToUse =
                getAndroidTarget(
                        sdkHandler, AndroidTargetHash.getPlatformHashString(targetDeviceVersion));

        if (targetToUse == null) {
            // The device platform is not installed, Studio should have done this already, so fail.
            throw new RuntimeException(
                    String.format(
                            ""
                                    + "In order to use Instant Run with this device running %1$S, "
                                    + "you must install platform %1$S in your SDK",
                            targetDeviceVersion.toString()));
        }

        return BootClasspathBuilder.computeFullBootClasspath(targetToUse, annotationsJar);
    }

    /**
     * Calls the sdklib machinery to construct the {@link IAndroidTarget} for the given hash string.
     *
     * @return appropriate {@link IAndroidTarget} or null if the matching platform package is not
     *         installed.
     */
    @Nullable
    private static IAndroidTarget getAndroidTarget(
            @NonNull SdkHandler sdkHandler,
            @NonNull String targetHash) {
        File sdkLocation = sdkHandler.getSdkFolder();
        ProgressIndicator progressIndicator = new LoggerProgressIndicatorWrapper(LOGGER);
        IAndroidTarget target = AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
        if (target != null) {
            return target;
        }
        // reset the cached AndroidSdkHandler, next time a target is looked up,
        // this will force the re-parsing of the SDK.
        AndroidSdkHandler.resetInstance(sdkLocation);

        // and let's try immediately, it's possible the platform was installed since the SDK
        // handler was initialized in the this VM, since we reset the instance just above, it's
        // possible we find it.
        return AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
}

    @Override
    public void setExternalNativeBuildTask(
            @NonNull AndroidTask<ExternalNativeBuildTask> task) {
        this.externalNativeBuild = task;
    }

    @Nullable
    @Override
    public ExternalNativeJsonGenerator getExternalNativeJsonGenerator() {
        return externalNativeJsonGenerator;
    }

    @Override
    public void setExternalNativeJsonGenerator(@NonNull ExternalNativeJsonGenerator generator) {
        Preconditions.checkState(this.externalNativeJsonGenerator == null,
                "Unexpected overwrite of externalNativeJsonGenerator "
                        + "may result in information loss");
        this.externalNativeJsonGenerator = generator;
    }

    @Nullable
    @Override
    public AndroidTask<ExternalNativeBuildTask> getExternalNativeBuildTask() {
        return externalNativeBuild;
    }

    @Override
    @NonNull
    public List<NativeBuildConfigValue> getExternalNativeBuildConfigValues() {
        return externalNativeBuildConfigValues;
    }

    @Override
    public void addExternalNativeBuildConfigValues(
            @NonNull Collection<NativeBuildConfigValue> values) {
        externalNativeBuildConfigValues.addAll(values);
    }

    @Nullable
    @Override
    public InstantRunTaskManager getInstantRunTaskManager() {
        return instantRunTaskManager;
    }

    @Override
    public void setInstantRunTaskManager(InstantRunTaskManager instantRunTaskManager) {
        this.instantRunTaskManager = instantRunTaskManager;
    }

    @NonNull
    @Override
    public TransformVariantScope getTransformVariantScope() {
        return this;
    }

    @NonNull
    @Override
    public Java8LangSupport getJava8LangSupportType() {
        // in order of precedence
        if (isJackEnabled()) {
            return Java8LangSupport.JACK;
        }

        if (globalScope.getProject().getPlugins().hasPlugin("me.tatarka.retrolambda")
                || globalScope.getProject().getPlugins().hasPlugin("dexguard")) {
            return Java8LangSupport.EXTERNAL_PLUGIN;
        }

        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_DESUGAR)) {
            return Java8LangSupport.DESUGAR;
        }

        return Java8LangSupport.NONE;
    }

    @Nullable
    @Override
    public Integer getMinSdkForDx() {
        if (getJava8LangSupportType() != Java8LangSupport.DESUGAR) {
            return null;
        }

        if (!globalScope
                .getExtension()
                .getCompileOptions()
                .getTargetCompatibility()
                .isJava8Compatible()) {
            return null;
        }

        if (getVariantConfiguration().getMinSdkVersionValue() >= 24) {
            return getVariantConfiguration().getMinSdkVersionValue();
        } else {
            return null;
        }
    }
}
