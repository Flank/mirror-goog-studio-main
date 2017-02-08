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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;

/**
 * Object that represents the dependencies of variant.
 *
 * <p>The dependencies are expressed as composite Gradle configuration objects that extends
 * all the configuration objects of the "configs".</p>
 *
 * <p>It optionally contains the dependencies for a test config for the given config.</p>
 */
public class VariantDependencies {

    public static final String CONFIG_NAME_COMPILE = "compile";
    public static final String CONFIG_NAME_S_COMPILE = "%sCompile";
    public static final String CONFIG_NAME_PUBLISH = "publish";
    public static final String CONFIG_NAME_S_PUBLISH = "%sPublish";
    public static final String CONFIG_NAME_APK = "apk";
    public static final String CONFIG_NAME_S_APK = "%sApk";
    public static final String CONFIG_NAME_PROVIDED = "provided";
    public static final String CONFIG_NAME_S_PROVIDED = "%sProvided";
    public static final String CONFIG_NAME_WEAR_APP = "wearApp";
    public static final String CONFIG_NAME_ANNOTATION_PROCESSOR = "annotationProcessor";
    public static final String CONFIG_NAME_S_WEAR_APP = "%sWearApp";
    public static final String CONFIG_NAME_S_ANNOTATION_PROCESSOR = "%sAnnotationProcessor";
    public static final String CONFIG_NAME_JACK_PLUGIN = "jackPlugin";
    public static final String CONFIG_NAME_S_JACK_PLUGIN = "%sJackPlugin";

    public static final String CONFIG_NAME_API = "api";
    public static final String CONFIG_NAME_S_API = "%sApi";
    public static final String CONFIG_NAME_COMPILE_ONLY = "compileOnly";
    public static final String CONFIG_NAME_S_COMPILE_ONLY = "%sCompileOnly";
    public static final String CONFIG_NAME_IMPLEMENTATION = "implementation";
    public static final String CONFIG_NAME_S_IMPLEMENTATION = "%sImplementation";
    public static final String CONFIG_NAME_RUNTIME_ONLY = "runtimeOnly";
    public static final String CONFIG_NAME_S_RUNTIME_ONLY = "%sRuntimeOnly";

    @NonNull
    private final String variantName;

    @NonNull
    private final Configuration compileClasspath;
    @NonNull
    private final Configuration runtimeClasspath;
    @Nullable
    private final Configuration apiElements;
    @Nullable
    private final Configuration runtimeElements;
    @NonNull
    private final Configuration annotationProcessorConfiguration;
    @NonNull
    private final Configuration jackPluginConfiguration;
    @NonNull
    private final Configuration wearAppConfiguration;

    private final VariantDependencies testedVariantDependencies;
    @Nullable
    private final AndroidDependency testedVariantOutput;

    private DependencyGraph compileGraph;
    private DependencyGraph packageGraph;
    private DependencyContainer compileContainer;
    private DependencyContainer packageContainer;

    /**
     *  Whether we have a direct dependency on com.android.support:support-annotations; this
     * is used to drive whether we extract annotations when building libraries for example
     */
    private boolean annotationsPresent;

    @NonNull
    private DependencyChecker checker;

    public static final class Builder {
        @NonNull
        private final Project project;
        @NonNull
        private final ErrorReporter errorReporter;
        @NonNull
        private final GradleVariantConfiguration variantConfiguration;
        private boolean publishVariant = false;
        private VariantType testedVariantType = null;
        private VariantDependencies testedVariantDependencies = null;
        private AndroidDependency testedVariantOutput = null;
        private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMatching;

        // default size should be enough. It's going to be rare for a variant to include
        // more than a few configurations (main, build-type, flavors...)
        // At most it's going to be flavor dimension count + 5:
        // variant-specific, build type, multi-flavor, flavor1, ..., flavorN, defaultConfig, test.
        // Default hash-map size of 16 (w/ load factor of .75) should be enough.
        private final Set<Configuration> compileClasspaths = Sets.newHashSet();
        private final Set<Configuration> apiClasspaths = Sets.newHashSet();
        private final Set<Configuration> runtimeClasspaths = Sets.newHashSet();
        private final Set<Configuration> annotationConfigs = Sets.newHashSet();
        private final Set<Configuration> jackPluginConfigs = Sets.newHashSet();
        private final Set<Configuration> wearAppConfigs = Sets.newHashSet();

        protected Builder(
                @NonNull Project project,
                @NonNull ErrorReporter errorReporter,
                @NonNull GradleVariantConfiguration variantConfiguration) {

            this.project = project;
            this.errorReporter = errorReporter;
            this.variantConfiguration = variantConfiguration;
        }

        public Builder setPublishVariant(boolean publishVariant) {
            this.publishVariant = publishVariant;
            return this;
        }

        public Builder setTestedVariantType(@NonNull VariantType testedVariantType) {
            this.testedVariantType = testedVariantType;
            return this;
        }

        public Builder addSourceSets(@NonNull DefaultAndroidSourceSet... sourceSets) {
            for (DefaultAndroidSourceSet sourceSet : sourceSets) {
                addSourceSet(sourceSet);
            }
            return this;
        }

        public Builder addSourceSets(@NonNull Collection<DefaultAndroidSourceSet> sourceSets) {
            for (DefaultAndroidSourceSet sourceSet : sourceSets) {
                addSourceSet(sourceSet);
            }
            return this;
        }

        public Builder addSourceSet(@Nullable DefaultAndroidSourceSet sourceSet) {
            if (sourceSet != null) {

                final ConfigurationContainer configs = project.getConfigurations();

                compileClasspaths.add(configs.getByName(sourceSet.getCompileOnlyConfigurationName()));
                runtimeClasspaths.add(configs.getByName(sourceSet.getRuntimeOnlyConfigurationName()));

                final Configuration implementationConfig = configs.getByName(sourceSet.getImplementationConfigurationName());
                compileClasspaths.add(implementationConfig);
                runtimeClasspaths.add(implementationConfig);

                String apiConfigName = sourceSet.getApiConfigurationName();
                if (apiConfigName != null) {
                    apiClasspaths.add(configs.getByName(apiConfigName));
                }

                annotationConfigs.add(configs.getByName(sourceSet.getAnnotationProcessorConfigurationName()));
                jackPluginConfigs.add(configs.getByName(sourceSet.getJackPluginConfigurationName()));
                wearAppConfigs.add(configs.getByName(sourceSet.getWearAppConfigurationName()));
            }

            return this;
        }

        public Builder setFlavorMatching(
                @Nullable Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMatching) {
            this.flavorMatching = flavorMatching;
            return this;
        }


        /**
         * Add a tested provider.
         *
         * In the case for tests of a library modules where the test must include the full
         * graph of the test + the library since there is a single apk that packages both.
         *
         * For app tests, we don't want to include the dependencies directly as there is two
         * apks and we need to make sure their graph are identical. Therefore we resolve them
         * independently and compare after.
         *
         * @param testedConfig the tested variant configuration
         * @param testedVariant the tested variant
         */
        public Builder addTestedVariant(
                @NonNull GradleVariantConfiguration testedConfig,
                @NonNull VariantDependencies testedVariant) {
            Preconditions.checkNotNull(testedVariantType,
                    "cannot call addTestedVariant before setTestedVariantType");

            if (testedVariantType == VariantType.LIBRARY) {
                // also record this so that we can resolve local jar conflict during flattening
                testedVariantOutput = testedConfig.getOutput();
            }

            // record this no matter what.
            testedVariantDependencies = testedVariant;

            return this;
        }

        public VariantDependencies build() {
            String variantName = variantConfiguration.getFullName();
            VariantType variantType = variantConfiguration.getType();
            String buildType = variantConfiguration.getBuildType().getName();
            Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMap =
                    getFlavorAttributes(flavorMatching);

            Configuration compileClasspath = project.getConfigurations().maybeCreate(variantName + "CompileClasspath");
            compileClasspath.setVisible(false);
            compileClasspath.setDescription("Resolved configuration for compilation for variant: " + variantName);
            compileClasspath.setExtendsFrom(compileClasspaths);
            compileClasspath.setCanBeConsumed(false);
            applyVariantAttributes(compileClasspath, buildType, flavorMap);
            compileClasspath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_COMPILE);
            compileClasspath.getResolutionStrategy().sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);

            Configuration annotationProcessor =
                    project.getConfigurations().maybeCreate("_" + variantName + "AnnotationProcessor");
            annotationProcessor.setVisible(false);
            annotationProcessor.setDescription("Resolved configuration for annotation-processor for variant: " + variantName);
            annotationProcessor.setExtendsFrom(annotationConfigs);
            annotationProcessor.setCanBeConsumed(false);
            // the annotation processor is using its dependencies for running the processor, so we need
            // all the runtime graph.
            annotationProcessor.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);
            applyVariantAttributes(annotationProcessor, buildType, flavorMap);

            Configuration jackPlugin =
                    project.getConfigurations().maybeCreate("_" + variantName + "JackPlugin");
            jackPlugin.setVisible(false);
            jackPlugin.setDescription("Resolved configuration for jack plugins for variant: " + variantName);
            jackPlugin.setExtendsFrom(jackPluginConfigs);
            jackPlugin.setCanBeConsumed(false);
            // the jack plugin is using its dependencies for running the plugins, so we need
            // all the runtime graph.
            jackPlugin.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);

            Configuration runtimeClasspath = project.getConfigurations().maybeCreate(variantName + "RuntimeClasspath");
            runtimeClasspath.setVisible(false);
            runtimeClasspath.setDescription("Resolved configuration for runtime for variant: " + variantName);
            runtimeClasspath.setExtendsFrom(runtimeClasspaths);
            runtimeClasspath.setCanBeConsumed(false);
            applyVariantAttributes(runtimeClasspath, buildType, flavorMap);
            runtimeClasspath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);
            runtimeClasspath.getResolutionStrategy().sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);

            Configuration wearApp = project.getConfigurations().maybeCreate(variantName + "WearBundling");
            wearApp.setDescription("Resolved Configuration for wear app bundling for variant: " + variantName);
            wearApp.setExtendsFrom(wearAppConfigs);
            wearApp.setCanBeConsumed(false);
            applyVariantAttributes(wearApp, buildType, flavorMap);
            // because the APK is published to Runtime, then we need to make sure this one consumes RUNTIME as well.
            wearApp.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);

            Configuration apiElements = null;
            Configuration runtimeElements = null;

            if (publishVariant) {
                // this is the configuration that contains the artifacts for inter-module
                // dependencies.
                runtimeElements = project.getConfigurations().maybeCreate(variantName + "RuntimeElements");
                runtimeElements.setDescription("Runtime elements for " + variantName);
                runtimeElements.setCanBeResolved(false);

                Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMap2 =
                        getFlavorAttributes(null);
                applyVariantAttributes(runtimeElements, buildType, flavorMap2);
                runtimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);

                // if the variant is not a library, then the publishing configuration should
                // not extend from anything. It's mostly there to access the artifacts from
                // another project but it shouldn't bring any dependencies with it.
                if (variantType == VariantType.LIBRARY) {
                    runtimeElements.setExtendsFrom(runtimeClasspaths);
                }

                apiElements = project.getConfigurations().maybeCreate(variantName + "ApiElements");
                apiElements.setDescription("API elements for " + variantName);
                apiElements.setCanBeResolved(false);
                applyVariantAttributes(apiElements, buildType, flavorMap2);
                apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_COMPILE);
                // apiElements only extends the api classpaths.
                apiElements.setExtendsFrom(apiClasspaths);
            }

            DependencyChecker checker = new DependencyChecker(
                    project.getPath().equals(":") ? project.getName() : project.getPath(),
                    variantName,
                    errorReporter,
                    variantType,
                    testedVariantType);

            return new VariantDependencies(
                    variantName,
                    checker,
                    compileClasspath,
                    runtimeClasspath,
                    apiElements,
                    runtimeElements,
                    annotationProcessor,
                    jackPlugin,
                    wearApp,
                    testedVariantDependencies,
                    testedVariantOutput);
        }

        /**
         * Returns a map of Configuration attributes containing all the flavor values.
         * @param flavorMatching a list of override for flavor matching or for new attributes.
         */
        @NonNull
        private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> getFlavorAttributes(
                @Nullable Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMatching) {
            List<CoreProductFlavor> productFlavors = variantConfiguration.getProductFlavors();
            Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> map = Maps.newHashMapWithExpectedSize(productFlavors.size());

            // first go through the product flavors and add matching attributes
            for (CoreProductFlavor f : productFlavors) {
                if (f.getDimension() == null) {
                    throw new RuntimeException("Null dimension for " + f.getName());
                }
                map.put(Attribute.of(f.getDimension(), ProductFlavorAttr.class), ProductFlavorAttr.of(f.getName()));
            }

            // then go through the override or new attributes.
            if (flavorMatching != null) {
                map.putAll(flavorMatching);
            }

            return map;
        }

        private static void applyVariantAttributes(
                @NonNull Configuration configuration,
                @NonNull String buildType,
                @NonNull Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMap) {
            configuration.getAttributes().attribute(BuildTypeAttr.ATTRIBUTE, BuildTypeAttr.of(buildType));
            for (Map.Entry<Attribute<ProductFlavorAttr>, ProductFlavorAttr> entry : flavorMap.entrySet()) {
                configuration.getAttributes().attribute(entry.getKey(), entry.getValue());
            }
        }
    }

    public static Builder builder(
            @NonNull Project project,
            @NonNull ErrorReporter errorReporter,
            @NonNull GradleVariantConfiguration variantConfiguration) {
        return new Builder(project, errorReporter, variantConfiguration);
    }

    private VariantDependencies(
            @NonNull String variantName,
            @NonNull DependencyChecker dependencyChecker,
            @NonNull Configuration compileClasspath,
            @NonNull Configuration runtimeClasspath,
            @Nullable Configuration apiElements,
            @Nullable Configuration runtimeElements,
            @NonNull Configuration annotationProcessorConfiguration,
            @NonNull Configuration jackPluginConfiguration,
            @NonNull Configuration wearAppConfiguration,
            @Nullable VariantDependencies testedVariantDependencies,
            @Nullable AndroidDependency testedVariantOutput) {
        this.variantName = variantName;
        this.checker = dependencyChecker;
        this.compileClasspath = compileClasspath;
        this.runtimeClasspath = runtimeClasspath;
        this.apiElements = apiElements;
        this.runtimeElements = runtimeElements;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.jackPluginConfiguration = jackPluginConfiguration;
        this.wearAppConfiguration = wearAppConfiguration;
        this.testedVariantDependencies = testedVariantDependencies;
        this.testedVariantOutput = testedVariantOutput;
    }

    public String getName() {
        return variantName;
    }

    @NonNull
    public Configuration getCompileClasspath() {
        return compileClasspath;
    }

    @NonNull
    public Configuration getRuntimeClasspath() {
        return runtimeClasspath;
    }

    @Nullable
    public Configuration getApiElements() {
        return apiElements;
    }

    @Nullable
    public Configuration getRuntimeElements() {
        return runtimeElements;
    }

    @NonNull
    public Configuration getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration;
    }

    @NonNull
    public Configuration getJackPluginConfiguration() {
        return jackPluginConfiguration;
    }

    @NonNull
    public Configuration getWearAppConfiguration() {
        return wearAppConfiguration;
    }

    public void setDependencies(
            @NonNull DependencyGraph compileGraph,
            @NonNull DependencyGraph packageGraph,
            boolean validate) {
        this.compileGraph = compileGraph;
        this.packageGraph = packageGraph;

        FlatDependencyContainer flatCompileContainer = compileGraph.flatten(
                testedVariantOutput,
                testedVariantDependencies != null
                        ? testedVariantDependencies.getCompileDependencies() : null);
        FlatDependencyContainer flatPackageContainer = packageGraph.flatten(
                testedVariantOutput,
                testedVariantDependencies != null
                        ? testedVariantDependencies.getPackageDependencies() : null);

        if (validate) {
            //noinspection VariableNotUsedInsideIf
            if (testedVariantOutput != null) {
                // in this case (test of a library module), we don't want to compare to the tested
                // variant. it's guaranteed that the current and tested graphs have common dependencies
                // because the former extends the latter. We don't want to start removing (skipping)
                // items because they are not going to be installed via the 1st of 2 apk (there is
                // only one apk for the test + aar)
                checker.validate(flatCompileContainer, flatPackageContainer, null);
            } else {
                checker.validate(
                        flatCompileContainer,
                        flatPackageContainer,
                        testedVariantDependencies);
            }
        }

        compileContainer = flatCompileContainer.filterSkippedLibraries();
        packageContainer = flatPackageContainer.filterSkippedLibraries();
    }


    DependencyGraph getCompileGraph() {
        return compileGraph;
    }

    DependencyGraph getPackageGraph() {
        return packageGraph;
    }

    public DependencyContainer getCompileDependencies() {
        return compileContainer;
    }

    public DependencyContainer getPackageDependencies() {
        return packageContainer;
    }

    @NonNull
    public DependencyChecker getChecker() {
        return checker;
    }

    public void setAnnotationsPresent(boolean annotationsPresent) {
        this.annotationsPresent = annotationsPresent;
    }

    public boolean isAnnotationsPresent() {
        return annotationsPresent;
    }

    public boolean hasNonOptionalLibraries() {
        // non optional libraries mean that there is some libraries in the package
        // dependencies
        // TODO this will go away when the user of this is removed.
        return packageGraph.getDependencies().stream()
                .anyMatch(node -> node.getNodeType() == DependencyNode.NodeType.ANDROID);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", variantName)
                .toString();
    }
}
