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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.incremental.BuildContext;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.internal.tasks.ResolveDependenciesTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
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
import com.android.builder.model.ApiVersion;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
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
    @NonNull
    private List<String> annotationProcessorArguments = Lists.newArrayList();

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

    private final Map<AndroidArtifacts.ArtifactType, ConfigurableFileCollection> artifactMap = Maps.newHashMap();

    public VariantScopeImpl(
            @NonNull GlobalScope globalScope,
            @NonNull TransformManager transformManager,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        this.globalScope = globalScope;
        this.transformManager = transformManager;
        this.variantData = variantData;
    }

    @Override
    protected Project getProject() {
        return globalScope.getProject();
    }

    @Override
    public void publishIntermediateArtifact(
            @NonNull File file,
            @NonNull String builtBy,
            @NonNull ArtifactType artifactType) {
        Collection<PublishedConfigType> configTypes = artifactType.getPublishingConfigurations();
        Preconditions.checkState(!configTypes.isEmpty());

        // FIXME: figure out strategy for each ArtifactType
        if (configTypes.contains(API_ELEMENTS)) {
            publishArtifactToConfiguration(
                    getVariantData().getVariantDependency().getApiElements(),
                    file,
                    builtBy,
                    artifactType);
        }

        if (configTypes.contains(RUNTIME_ELEMENTS)) {
            publishArtifactToConfiguration(
                    getVariantData().getVariantDependency().getRuntimeElements(),
                    file,
                    builtBy,
                    artifactType);
        }

        artifactMap.put(artifactType, createCollection(file, builtBy));
    }

    private void publishArtifactToConfiguration(
            @NonNull Configuration configuration,
            @NonNull File file,
            @NonNull String builtBy,
            @NonNull ArtifactType artifactType) {
        final Project project = getGlobalScope().getProject();
        String type = artifactType.getType();
        configuration.getOutgoing().variants(
                (NamedDomainObjectContainer<ConfigurationVariant> variants) -> {
                    variants.create(type, (variant) ->
                            variant.artifact(file, (artifact) -> {
                                artifact.setType(type);
                                artifact.builtBy(project.getTasks().getByName(builtBy));
                            }));
                });
    }

    @Override
    @Nullable
    public ConfigurableFileCollection getInternalArtifact(@NonNull ArtifactType type) {
        return artifactMap.get(type);
    }

    @Nullable
    @Override
    public ConfigurableFileCollection getTestedArtifact(
            @NonNull ArtifactType type,
            @NonNull VariantType testedVariantType) {
        // get the matching file collection for the tested variant, if any.
        if (variantData instanceof TestVariantData) {
            TestedVariantData tested = ((TestVariantData) variantData).getTestedVariantData();

            if (tested instanceof BaseVariantData) {
                BaseVariantData testedVariantData = (BaseVariantData) tested;
                if (testedVariantData.getVariantConfiguration().getType() == testedVariantType) {
                    return testedVariantData.getScope().getInternalArtifact(type);
                }
            }
        }

        return null;
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
                && !buildType.isUseProguard()
                && !getBuildContext().isInInstantRunMode();
    }

    @Override
    public boolean isJackEnabled() {
        return getVariantConfiguration().isJackEnabled();
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
        if (getVariantData().getType() == VariantType.ATOM)
            suffix = ATOM_SUFFIX + suffix;
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
    public File getDexOutputFolder(@NonNull String atomName) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                FD_DEX,
                atomName + "-" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getBuildInfoOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/build-info/" + getVariantConfiguration().getDirName());
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
        // TODO cache?
        FileCollection classpath = getArtifactFileCollection(COMPILE_CLASSPATH, ALL, CLASSES);

        if (variantData.getVariantConfiguration().getRenderscriptSupportModeEnabled()) {
            File renderScriptSupportJar = globalScope.getAndroidBuilder().getRenderScriptSupportJar();
            classpath = classpath.plus(globalScope.getProject().files(renderScriptSupportJar));
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
    public File getJavaOutputDir(@NonNull String atomName) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                FD_CLASSES_OUTPUT,
                atomName + "-" + variantData.getVariantConfiguration().getDirName());
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
    public FileCollection getArtifactFileCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {
        checkConfigType(artifactType, configType);

        Configuration configuration;
        switch (configType) {
            case COMPILE_CLASSPATH:
                configuration = getVariantData().getVariantDependency().getCompileClasspath();
                break;
            case RUNTIME_CLASSPATH:
                configuration = getVariantData().getVariantDependency().getRuntimeClasspath();
                break;
            case ANNOTATION_PROCESSOR:
                configuration = getVariantData()
                        .getVariantDependency()
                        .getAnnotationProcessorConfiguration();
                break;
            default:
                throw new RuntimeException("unknown ConfigType value");
        }

        Action<AttributeContainer> attributes =
                container -> container.attribute(ARTIFACT_TYPE, artifactType.getType());

        Spec<ComponentIdentifier> filter = null;
        FileCollection minus = null;
        switch (scope) {
            case ALL:
                break;
            case EXTERNAL:
                filter = id -> id instanceof ModuleComponentIdentifier;
                // FIXME once Spec based filtering supports file based dependency
                if (artifactType == ArtifactType.JAVA_RES
                        || artifactType == CLASSES) {

                    // only remove the local jars to this project, not all file-based jars.
                    Callable<Collection<File>> callable = getLocalJarLambda(configuration)::get;
                    minus = globalScope.getProject().files(callable);
                }
                break;
            case MODULE:
                filter = id -> id instanceof ProjectComponentIdentifier;
                // FIXME once Spec based filtering supports file based dependency
                if (artifactType == ArtifactType.JAVA_RES
                        || artifactType == CLASSES) {
                    // remove all file-based local jar in this case.
                    minus = getAllFileBasedJars(attributes, configuration);
                }

                break;
            default:
                throw new RuntimeException("unknown ArtifactScope value");
        }

        FileCollection fileCollection;

        ArtifactView artifactView = configuration.getIncoming().artifactView().attributes(attributes);
        if (filter == null) {
            fileCollection = artifactView.getFiles();
        } else {
            fileCollection = artifactView.componentFilter(filter).getFiles();
        }

        fileCollection = handleTestedComponent(fileCollection,
                configType,
                scope,
                artifactType);

        if (minus != null) {
            fileCollection = fileCollection.minus(minus);
        }

        return fileCollection;
    }

    private static void checkConfigType(
            @NonNull ArtifactType artifactType,
            @NonNull ConsumedConfigType consumedConfigType) {
        // check if the queried configuration matches where this artifact has been published.
        // this is mostly a check against typo'ed configuration.
        Collection<PublishedConfigType> publishedConfigs = artifactType.getPublishingConfigurations();
        PublishedConfigType publishedTo = consumedConfigType.getPublishedTo();
        // if empty, means it was published to all configs.
        if (!publishedConfigs.contains(publishedTo)) {
            throw new RuntimeException(
                    "Querying Artifact '"
                            + artifactType.name()
                            + "' with config '"
                            + consumedConfigType.name()
                            + "' for artifact published to: "
                            + publishedConfigs);
        }
    }

    @Override
    @NonNull
    public ArtifactCollection getArtifactCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {
        checkConfigType(artifactType, configType);

        Configuration configuration;
        switch (configType) {
            case COMPILE_CLASSPATH:
                configuration = getVariantData().getVariantDependency().getCompileClasspath();
                break;
            case RUNTIME_CLASSPATH:
                configuration = getVariantData().getVariantDependency().getRuntimeClasspath();
                break;
            default:
                throw new RuntimeException("unknown ConfigType value");
        }

        Spec<ComponentIdentifier> filter = null;
        switch (scope) {
            case ALL:
                break;
            case EXTERNAL:
                filter = id -> id instanceof ModuleComponentIdentifier;
                break;
            case MODULE:
                filter = id -> id instanceof ProjectComponentIdentifier;
                break;
            default:
                throw new RuntimeException("unknown ArtifactScope value");
        }

        ArtifactView artifactView = configuration.getIncoming().artifactView()
                .attributes(container -> container.attribute(ARTIFACT_TYPE, artifactType.getType()));

        if (filter == null) {
            return artifactView.getArtifacts();
        } else {
            return artifactView.componentFilter(filter).getArtifacts();
        }
    }

    /**
     * Returns the packaged local Jars
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    @Override
    public Supplier<Collection<File>> getLocalPackagedJars() {

        return TaskInputHelper.bypassFileSupplier(
                getLocalJarLambda(
                        getVariantData().getVariantDependency()
                        .getRuntimeClasspath()));
    }

    @NonNull
    @Override
    public FileCollection getProvidedOnlyClasspath() {
        FileCollection compile = getArtifactFileCollection(COMPILE_CLASSPATH, ALL, CLASSES);
        FileCollection pkg = getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, CLASSES);
        return compile.minus(pkg);
    }

    @Override
    @NonNull
    public File getIntermediateJarOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/intermediate-jars/" +
                variantData.getVariantConfiguration().getDirName());
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
        if (getVariantData().getType() == VariantType.ATOM) {
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
    public File getResourceBlameLogDir(@NonNull String atomName) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings("blame", "res", atomName, getDirectorySegments()));
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
    public File getRClassSourceOutputDir(@NonNull String atomName) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                FD_SOURCE_GEN,
                FD_RES_CLASS,
                atomName + "-" + getVariantConfiguration().getDirName());
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
    public File getSplitSupportDirectory() {
        return new File(globalScope.getIntermediatesDir(),
                "splits-support/" + getVariantConfiguration().getDirName());
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
    public File getPackageAtom(@NonNull String atomName) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "atoms",
                getVariantConfiguration().getDirName(),
                atomName + DOT_ANDROID_PACKAGE);
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
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                getVariantConfiguration().getType() == VariantType.ATOM ?
                        DIR_ATOMBUNDLES : DIR_BUNDLES,
                getVariantConfiguration().getFullName());
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

    @NonNull
    @Override
    public File getLibInfoFile() {
        return new File(getBaseBundleDir(), "libinfo.txt");
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
    private BuildContext buildContext = new BuildContext();

    @Override
    @NonNull
    public BuildContext getBuildContext() {
        return buildContext;
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

        int targetDeviceFeatureLevel =
                AndroidGradleOptions.getTargetFeatureLevel(getGlobalScope().getProject());

        if (androidBuilderTarget.getVersion().getFeatureLevel() == targetDeviceFeatureLevel) {
            // Compile SDK and the target device match, re-use the target that we have already
            // found earlier.
            return BootClasspathBuilder.computeFullBootClasspath(
                    androidBuilderTarget, annotationsJar);
        }

        // Try treating it as a stable version
        IAndroidTarget targetToUse = getAndroidTarget(
                sdkHandler,
                AndroidTargetHash.getPlatformHashString(
                        new AndroidVersion(targetDeviceFeatureLevel, null)));

        // Otherwise try a preview version
        if (targetToUse == null) {
            // Currently AS always sets the injected api level to a number, so the target hash above
            // is something like "android-24". We failed to find it, so let's try "android-N".
            String buildCode = SdkVersionInfo.getBuildCode(targetDeviceFeatureLevel);
            if (buildCode != null) {
                AndroidVersion versionFromBuildCode =
                        new AndroidVersion(targetDeviceFeatureLevel - 1, buildCode);

                targetToUse = getAndroidTarget(
                        sdkHandler,
                        AndroidTargetHash.getPlatformHashString(versionFromBuildCode));
            }
        }

        if (targetToUse == null) {
            // The device platform is not installed, let's carry on with the compile SDK.
            throw new RuntimeException(String.format(""
                    + "In order to use Instant Run with this device running API %1$d, "
                    + "you must install platform %1$d in your SDK",
                    targetDeviceFeatureLevel));
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

    /**
     * adds the tested artifact if the tested artifact is an AAR.
     *
     * @param fileCollection the file collection to add the new artifact to
     * @param artifactType the type of the artifact to add
     * @return a new file collection containing the result
     */
    @NonNull
    private FileCollection handleTestedComponent(
            @NonNull FileCollection fileCollection,
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope artifactScope,
            @NonNull ArtifactType artifactType) {
        // this only handles Android Test, not unit tests.
        VariantType variantType = getVariantConfiguration().getType();
        if (!variantType.isForTesting() || variantType != VariantType.ANDROID_TEST) {
            return fileCollection;
        }

        // get the matching file collection for the tested variant, if any.
        if (variantData instanceof TestVariantData) {
            TestedVariantData tested = ((TestVariantData) variantData).getTestedVariantData();

            if (tested instanceof LibraryVariantData) {
                // we only add the tested component to the MODULE scope.
                if (artifactScope == ArtifactScope.MODULE || artifactScope == ALL) {
                    // for the library, always add the tested scope no matter the
                    // configuration type (package or compile)
                    final LibraryVariantData testedVariantData = (LibraryVariantData) tested;
                    ConfigurableFileCollection testedFC = testedVariantData.getScope()
                            .getInternalArtifact(artifactType);

                    if (testedFC != null) {
                        fileCollection = testedFC.plus(fileCollection);
                    }
                }

            } else if (tested instanceof ApplicationVariantData) {
                final ApplicationVariantData testedVariantData = (ApplicationVariantData) tested;

                // for tested application, first we add the tested component as provided (so
                // only for the compile version), and only for the java compilation
                if (configType == COMPILE_CLASSPATH &&
                        (artifactScope == ArtifactScope.MODULE || artifactScope == ALL) &&
                        artifactType == CLASSES) {
                    ConfigurableFileCollection testedFC = testedVariantData.getScope()
                            .getInternalArtifact(CLASSES);

                    if (testedFC != null) {
                        fileCollection = testedFC.plus(fileCollection);
                    }
                } else if (configType == RUNTIME_CLASSPATH) {
                    // however we also always remove the transitive dependencies coming from the
                    // tested app to avoid having the same artifact on each app and tested app.
                    // This applies only to the package scope since we do want these in the compile
                    // scope in order to compile
                    fileCollection = fileCollection.minus(testedVariantData.getScope()
                            .getArtifactFileCollection(configType, ALL,
                                    artifactType));
                }
            }
        }

        return fileCollection;
    }

    @NonNull
    private static FileCollection getAllFileBasedJars(
            @NonNull Action<AttributeContainer> attributes,
            @NonNull Configuration configuration) {
        return configuration.getIncoming().artifactView()
                .attributes(attributes)
                .componentFilter(id -> false).getFiles();
    }

    @NonNull
    private static Supplier<Collection<File>> getLocalJarLambda(
            @NonNull Configuration configuration) {
        return () -> {
            List<File> files = new ArrayList<>();
            for (Dependency dependency : configuration.getAllDependencies()) {
                if (dependency instanceof SelfResolvingDependency &&
                        !(dependency instanceof ProjectDependency)) {
                    files.addAll(((SelfResolvingDependency) dependency).resolve());
                }
            }
            return files;
        };
    }


    @Nullable
    private SplitList splitList;

    @Nullable
    @Override
    public SplitList getSplitList() {
        return splitList;
    }

    @Override
    public void setSplitList(@NonNull SplitList splitList) {
        this.splitList = splitList;
    }
}
