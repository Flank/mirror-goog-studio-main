/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.model

import com.android.annotations.NonNull
import com.android.build.gradle.internal.AndroidConfigHelper
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.LibraryCache
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.coverage.JacocoPlugin
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.RecordingBuildListener
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.managed.AndroidConfig
import com.android.build.gradle.managed.BuildType
import com.android.build.gradle.managed.FilePattern
import com.android.build.gradle.managed.ManagedString
import com.android.build.gradle.managed.NdkConfig
import com.android.build.gradle.managed.ProductFlavor
import com.android.build.gradle.managed.SigningConfig
import com.android.build.gradle.managed.adaptor.AndroidConfigAdaptor
import com.android.build.gradle.managed.adaptor.BuildTypeAdaptor
import com.android.build.gradle.managed.adaptor.ProductFlavorAdaptor
import com.android.build.gradle.tasks.JillTask
import com.android.build.gradle.tasks.PreDex
import com.android.builder.core.AndroidBuilder
import com.android.builder.internal.compiler.JackConversionCache
import com.android.builder.internal.compiler.PreDexCache
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.ProcessRecorderFactory
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import com.android.builder.sdk.TargetInfo
import com.android.builder.signing.DefaultSigningConfig
import com.android.ide.common.internal.ExecutorSingleton
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.signing.KeystoreHelper
import com.android.utils.ILogger
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.base.internal.registry.LanguageTransform
import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.collection.ManagedSet
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject
import java.security.KeyStore

import static com.android.build.gradle.model.ModelConstants.ANDROID_BUILDER
import static com.android.build.gradle.model.ModelConstants.EXTRA_MODEL_INFO
import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

@CompileStatic
public class BaseComponentModelPlugin implements Plugin<Project> {
    ToolingModelBuilderRegistry toolingRegistry
    ModelRegistry modelRegistry

    @Inject
    protected BaseComponentModelPlugin(
            ToolingModelBuilderRegistry toolingRegistry,
            ModelRegistry modelRegistry) {
        this.toolingRegistry = toolingRegistry
        this.modelRegistry = modelRegistry
    }

    /**
     * Replace BasePlugin's apply method for component model.
     */
    @Override
    public void apply(Project project) {
        ProcessRecorderFactory.initialize(new LoggerWrapper(project.logger), project.rootProject.
                file("profiler" + System.currentTimeMillis() + ".json"))
        project.gradle.addListener(new RecordingBuildListener(ThreadRecorder.get()));

        project.apply plugin: AndroidComponentModelPlugin

        project.apply plugin: JavaBasePlugin
        project.apply plugin: JacocoPlugin

        // TODO: Create configurations for build types and flavors, or migrate to new dependency
        // management if it's ready.
        ConfigurationContainer configurations = project.getConfigurations()
        createConfiguration(
                configurations,
                "compile",
                "Classpath for default sources.")

        createConfiguration(
                configurations,
                "default-metadata",
                "Metadata for published APKs")

        createConfiguration(
                configurations,
                "default-mapping",
                "Metadata for published APKs")

        project.tasks.getByName("assemble").description =
                "Assembles all variants of all applications and secondary packages."

        project.apply plugin: NdkComponentModelPlugin

        // Remove this when our models no longer depends on Project.
        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("projectModel", Project), project)
                        .descriptor("Model of project.")
                        .build())

        toolingRegistry.register(new ComponentModelBuilder(modelRegistry))

        // Inserting the ToolingModelBuilderRegistry into the model so that it can be use to create
        // TaskManager in child classes.
        modelRegistry.create(
                ModelCreators.bridgedInstance(
                        ModelReference.of("toolingRegistry", ToolingModelBuilderRegistry), toolingRegistry)
                        .descriptor("Tooling model builder model registry.")
                        .build())
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    static class Rules extends RuleSource {
        @Mutate
        void configureAndroidModel(
                AndroidConfig androidModel,
                ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            AndroidConfigHelper.configure(androidModel, instantiator)

            androidModel.signingConfigs.create {
                it.name = DEBUG
                it.storeFile = KeystoreHelper.defaultDebugKeystoreLocation();
                it.storePassword = DefaultSigningConfig.DEFAULT_PASSWORD;
                it.keyAlias = DefaultSigningConfig.DEFAULT_ALIAS;
                it.keyPassword = DefaultSigningConfig.DEFAULT_PASSWORD;
                it.storeType = KeyStore.getDefaultType();
            }
        }

        // com.android.build.gradle.AndroidConfig do not contain an NdkConfig.  Copy it to the
        // defaultConfig for now.
        @Mutate
        void copyNdkConfig(
                @Path("android.defaultConfig.ndkConfig") NdkConfig defaultNdkConfig,
                @Path("android.ndk") NdkConfig pluginNdkConfig) {
            defaultNdkConfig.moduleName = pluginNdkConfig.moduleName
            defaultNdkConfig.toolchain = pluginNdkConfig.toolchain
            defaultNdkConfig.toolchainVersion = pluginNdkConfig.toolchainVersion

            for (final ManagedString abi : pluginNdkConfig.getAbiFilters()) {
                defaultNdkConfig.getAbiFilters().create {
                    it.value = abi.value
                }
            }

            defaultNdkConfig.setCFlags(pluginNdkConfig.getCFlags())
            defaultNdkConfig.cppFlags = pluginNdkConfig.cppFlags

            for (final ManagedString ldLibs : pluginNdkConfig.getAbiFilters()) {
                defaultNdkConfig.getLdLibs().create {
                    it.value = ldLibs.value
                }
            }

            defaultNdkConfig.stl = pluginNdkConfig.stl
            defaultNdkConfig.renderscriptNdkMode = pluginNdkConfig.renderscriptNdkMode
        }

        // TODO: Remove code duplicated from BasePlugin.
        @Model(EXTRA_MODEL_INFO)
        ExtraModelInfo createExtraModelInfo(
                Project project,
                @NonNull @Path("isApplication") Boolean isApplication) {
            return new ExtraModelInfo(project, isApplication)
        }

        @Model
        SdkHandler createSdkHandler(Project project) {
            ILogger logger = new LoggerWrapper(project.logger)
            SdkHandler sdkHandler = new SdkHandler(project, logger)

            // call back on execution. This is called after the whole build is done (not
            // after the current project is done).
            // This is will be called for each (android) projects though, so this should support
            // being called 2+ times.
            project.gradle.buildFinished {
                ExecutorSingleton.shutdown()
                sdkHandler.unload()
                PreDexCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"),
                        logger)
                JackConversionCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"),
                        logger)
                LibraryCache.getCache().unload()
            }

            project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
                for (Task task : taskGraph.allTasks) {
                    if (task instanceof PreDex) {
                        PreDexCache.getCache().load(
                                project.rootProject.file(
                                        "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"))
                        break;
                    } else if (task instanceof JillTask) {
                        JackConversionCache.getCache().load(
                                project.rootProject.file(
                                        "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"))
                        break;
                    }
                }
            }
            return sdkHandler
        }

        @Model(ANDROID_BUILDER)
        AndroidBuilder createAndroidBuilder(Project project, ExtraModelInfo extraModelInfo) {
            String creator = "Android Gradle"
            ILogger logger = new LoggerWrapper(project.logger)

            return new AndroidBuilder(
                    project == project.rootProject ? project.name : project.path,
                    creator,
                    new GradleProcessExecutor(project),
                    new GradleJavaProcessExecutor(project),
                    new LoggedProcessOutputHandler(logger),
                    extraModelInfo,
                    logger,
                    project.logger.isEnabled(LogLevel.INFO))

        }

        @Mutate
        void initDebugBuildTypes(
                @Path("android.buildTypes") ManagedSet<BuildType> buildTypes,
                @Path("android.signingConfigs") ManagedSet<SigningConfig> signingConfigs) {
            final SigningConfig debugSigningConfig = signingConfigs.find { it -> DEBUG }

            buildTypes.beforeEach { BuildType buildType ->
                initBuildType(buildType)
            }

            buildTypes.afterEach { BuildType buildType ->
                if (buildType.getName().equals(DEBUG)) {
                    buildType.setSigningConfig(debugSigningConfig)
                }
            }
        }

        private static void initBuildType(@NonNull BuildType buildType) {
            buildType.setIsDebuggable(false)
            buildType.setIsTestCoverageEnabled(false)
            buildType.setIsJniDebuggable(false)
            buildType.setIsPseudoLocalesEnabled(false)
            buildType.setIsRenderscriptDebuggable(false)
            buildType.setRenderscriptOptimLevel(3)
            buildType.setIsMinifyEnabled(false)
            buildType.setIsZipAlignEnabled(true)
            buildType.setIsEmbedMicroApp(true)
            buildType.setUseJack(false)
            buildType.setShrinkResources(false)
        }


        @Mutate
        void addDefaultAndroidSourceSet(
                @Path("android.sources") AndroidComponentModelSourceSet sources) {
            sources.addDefaultSourceSet("resources", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("java", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("manifest", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("res", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("assets", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("aidl", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("renderscript", AndroidLanguageSourceSet.class);
            sources.addDefaultSourceSet("jniLibs", AndroidLanguageSourceSet.class);
        }

        @Model
        com.android.build.gradle.AndroidConfig createModelAdaptor(
                ServiceRegistry serviceRegistry,
                AndroidConfig androidExtension,
                Project project,
                @Path("isApplication") Boolean isApplication) {
                Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return new AndroidConfigAdaptor(
                    androidExtension,
                    AndroidConfigHelper.createSourceSetsContainer(
                            project, instantiator, !isApplication /* isLibrary */));
        }

        @Mutate
        void createAndroidComponents(
                AndroidComponentSpec androidSpec,
                ServiceRegistry serviceRegistry,
                AndroidConfig androidExtension,
                com.android.build.gradle.AndroidConfig adaptedModel,
                @Path("android.buildTypes") ManagedSet<BuildType> buildTypes,
                @Path("android.productFlavors") ManagedSet<ProductFlavor> productFlavors,
                @Path("android.signingConfigs") ManagedSet<SigningConfig> signingConfigs,
                VariantFactory variantFactory,
                TaskManager taskManager,
                Project project,
                AndroidBuilder androidBuilder,
                SdkHandler sdkHandler,
                ExtraModelInfo extraModelInfo,
                @Path("isApplication") Boolean isApplication) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            // check if the target has been set.
            TargetInfo targetInfo = androidBuilder.getTargetInfo()
            if (targetInfo == null) {
                sdkHandler.initTarget(
                        androidExtension.getCompileSdkVersion(),
                        androidExtension.buildToolsRevision,
                        androidExtension.libraryRequests,
                        androidBuilder)
            }

            VariantManager variantManager = new VariantManager(
                    project,
                    androidBuilder,
                    adaptedModel,
                    variantFactory,
                    taskManager,
                    instantiator)

            for(BuildType buildType : buildTypes) {
                variantManager.addBuildType(new BuildTypeAdaptor(buildType))
            }
            for(ProductFlavor productFlavor : productFlavors) {
                variantManager.addProductFlavor(new ProductFlavorAdaptor(productFlavor))
            }

            DefaultAndroidComponentSpec spec = (DefaultAndroidComponentSpec) androidSpec
            spec.extension = androidExtension
            spec.variantManager = variantManager
        }

        @Mutate
        void createVariantData(
                CollectionBuilder<AndroidBinary> binaries,
                AndroidComponentSpec spec, TaskManager taskManager) {
            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager
            binaries.all {
                DefaultAndroidBinary binary = it as DefaultAndroidBinary
                binary.variantData =
                        variantManager.createVariantData(binary.buildType, binary.productFlavors)
                variantManager.getVariantDataList().add(binary.variantData);
            }
        }

        @Mutate
        void createLifeCycleTasks(
                CollectionBuilder<Task> tasks,
                TaskManager taskManager) {
            taskManager.createTasksBeforeEvaluate(new TaskCollectionBuilderAdaptor(tasks))
        }

        @Mutate
        void createAndroidTasks(
                CollectionBuilder<Task> tasks,
                AndroidComponentSpec androidSpec,
                TaskManager taskManager,
                SdkHandler sdkHandler,
                Project project,
                AndroidComponentModelSourceSet androidSources) {
            DefaultAndroidComponentSpec spec = (DefaultAndroidComponentSpec) androidSpec

            // setup SDK repositories.
            for (File file : sdkHandler.sdkLoader.repositories) {
                project.repositories.maven { MavenArtifactRepository repo ->
                    repo.url = file.toURI()
                }
            }

            // TODO: determine how to provide functionalities of variant API objects.
        }

        // TODO: Use @BinaryTasks after figuring how to configure non-binary specific tasks.
        @Mutate
        void createBinaryTasks(
                CollectionBuilder<Task> tasks,
                BinaryContainer binaries,
                AndroidComponentSpec spec,
                TaskManager taskManager) {

            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager
            binaries.withType(AndroidBinary) { androidBinary ->
                ThreadRecorder.get().record(ExecutionType.VARIANT_MANAGER_CREATE_TASKS_FOR_VARIANT,
                        new Recorder.Block<Void>() {
                            @Override
                            public Void call() throws Exception {
                                    DefaultAndroidBinary binary = androidBinary as DefaultAndroidBinary
                                    variantManager.createTasksForVariantData(
                                            new TaskCollectionBuilderAdaptor(tasks),
                                            binary.variantData)
                                return null;
                            }
                        });
            }
        }

        /**
         * Create tasks that must be created after other tasks for variants are created.
         */
        @Mutate
        void createRemainingTasks(
                CollectionBuilder<Task> tasks,
                TaskManager taskManager,
                AndroidComponentSpec spec) {
            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager

            // create the test tasks.
            taskManager.createTopLevelTestTasks (
                    new TaskCollectionBuilderAdaptor(tasks),
                    !variantManager.productFlavors.isEmpty() /*hasFlavors*/);
        }

        @Mutate
        void createReportTasks(CollectionBuilder<Task> tasks, AndroidComponentSpec spec) {
            VariantManager variantManager = (spec as DefaultAndroidComponentSpec).variantManager

            tasks.create("androidDependencies", DependencyReportTask) { DependencyReportTask dependencyReportTask ->
                dependencyReportTask.setDescription("Displays the Android dependencies of the project")
                dependencyReportTask.setVariants(variantManager.variantDataList)
                dependencyReportTask.setGroup("Android")
            }

            tasks.create("signingReport", SigningReportTask) { SigningReportTask signingReportTask ->
                signingReportTask.setDescription("Displays the signing info for each variant")
                signingReportTask.setVariants(variantManager.variantDataList)
                signingReportTask.setGroup("Android")
            }
        }
    }

    /**
     * Default Android LanguageRegistration.
     *
     * Allows default LanguageSourceSet to be create until specialized LanguageRegistration is
     * created.
     */
    private static class AndroidSource implements LanguageTransform<AndroidLanguageSourceSet, AndroidObject> {
        public String getName() {
            return "main";
        }

        public Class<AndroidLanguageSourceSet> getSourceSetType() {
            return AndroidLanguageSourceSet.class;
        }

        public Class<? extends AndroidLanguageSourceSet> getSourceSetImplementation() {
            return AndroidLanguageSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<AndroidObject> getOutputType() {
            return null;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "process";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return DefaultTask;
                }

                public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
                }
            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return false
        }
    }

    private void createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String configurationName,
            @NonNull String configurationDescription) {
        Configuration configuration = configurations.findByName(configurationName)
        if (configuration == null) {
            configuration = configurations.create(configurationName)
        }
        configuration.setVisible(false);
        configuration.setDescription(configurationDescription)
    }

}
