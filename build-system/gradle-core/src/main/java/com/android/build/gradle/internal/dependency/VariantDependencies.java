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
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ComponentSelectionRules;
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
        private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection;

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

        public Builder setFlavorSelection(
                @Nullable Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection) {
            this.flavorSelection = flavorSelection;
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
                    getFlavorAttributes(flavorSelection);

            final ConfigurationContainer configurations = project.getConfigurations();

            final String compileClasspathName = variantName + "CompileClasspath";
            Configuration compileClasspath = configurations.maybeCreate(compileClasspathName);
            compileClasspath.setVisible(false);
            compileClasspath.setDescription("Resolved configuration for compilation for variant: " + variantName);
            compileClasspath.setExtendsFrom(compileClasspaths);
            compileClasspath.setCanBeConsumed(false);
            filterOutBadArtifacts(compileClasspath);
            applyVariantAttributes(compileClasspath, buildType, flavorMap);
            compileClasspath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_COMPILE);
            compileClasspath.getResolutionStrategy().sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);

            Configuration annotationProcessor =
                    configurations.maybeCreate("_" + variantName + "AnnotationProcessor");
            annotationProcessor.setVisible(false);
            annotationProcessor.setDescription("Resolved configuration for annotation-processor for variant: " + variantName);
            annotationProcessor.setExtendsFrom(annotationConfigs);
            annotationProcessor.setCanBeConsumed(false);
            // the annotation processor is using its dependencies for running the processor, so we need
            // all the runtime graph.
            annotationProcessor.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);
            applyVariantAttributes(annotationProcessor, buildType, flavorMap);

            Configuration jackPlugin = configurations.maybeCreate("_" + variantName + "JackPlugin");
            jackPlugin.setVisible(false);
            jackPlugin.setDescription("Resolved configuration for jack plugins for variant: " + variantName);
            jackPlugin.setExtendsFrom(jackPluginConfigs);
            jackPlugin.setCanBeConsumed(false);
            // the jack plugin is using its dependencies for running the plugins, so we need
            // all the runtime graph.
            jackPlugin.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);

            final String runtimeClasspathName = variantName + "RuntimeClasspath";
            Configuration runtimeClasspath = configurations.maybeCreate(runtimeClasspathName);
            runtimeClasspath.setVisible(false);
            runtimeClasspath.setDescription("Resolved configuration for runtime for variant: " + variantName);
            runtimeClasspath.setExtendsFrom(runtimeClasspaths);
            runtimeClasspath.setCanBeConsumed(false);
            filterOutBadArtifacts(compileClasspath);
            applyVariantAttributes(runtimeClasspath, buildType, flavorMap);
            runtimeClasspath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);
            runtimeClasspath.getResolutionStrategy().sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);

            Configuration wearApp = configurations.maybeCreate(variantName + "WearBundling");
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
                runtimeElements = configurations.maybeCreate(variantName + "RuntimeElements");
                runtimeElements.setDescription("Runtime elements for " + variantName);
                runtimeElements.setCanBeResolved(false);

                Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMap2 =
                        getFlavorAttributes(null);
                applyVariantAttributes(runtimeElements, buildType, flavorMap2);
                VariantAttr variantNameAttr = VariantAttr.of(variantName);
                runtimeElements.getAttributes().attribute(VariantAttr.ATTRIBUTE, variantNameAttr);
                runtimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_RUNTIME);

                // if the variant is not a library, then the publishing configuration should
                // not extend from anything. It's mostly there to access the artifacts from
                // another project but it shouldn't bring any dependencies with it.
                if (variantType == VariantType.LIBRARY) {
                    runtimeElements.setExtendsFrom(runtimeClasspaths);
                }

                apiElements = configurations.maybeCreate(variantName + "ApiElements");
                apiElements.setDescription("API elements for " + variantName);
                apiElements.setCanBeResolved(false);
                applyVariantAttributes(apiElements, buildType, flavorMap2);
                apiElements.getAttributes().attribute(VariantAttr.ATTRIBUTE, variantNameAttr);
                apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_COMPILE);
                // apiElements only extends the api classpaths.
                apiElements.setExtendsFrom(apiClasspaths);
            }

            // TODO remove after a while?
            checkOldConfigurations(
                    configurations, "_" + variantName + "Compile", compileClasspathName);
            checkOldConfigurations(configurations, "_" + variantName + "Apk", runtimeClasspathName);
            checkOldConfigurations(
                    configurations, "_" + variantName + "Publish", runtimeClasspathName);

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

        private static void checkOldConfigurations(
                @NonNull ConfigurationContainer configurations,
                @NonNull String oldConfigName,
                @NonNull String newConfigName) {
            if (configurations.findByName(oldConfigName) != null) {
                throw new RuntimeException(
                        String.format(
                                "Configuration with old name %s found. Use new name %s instead.",
                                oldConfigName, newConfigName));
            }
        }

        /**
         * Returns a map of Configuration attributes containing all the flavor values.
         *
         * @param flavorSelection a list of override for flavor matching or for new attributes.
         */
        @NonNull
        private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> getFlavorAttributes(
                @Nullable Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection) {
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
            if (flavorSelection != null) {
                map.putAll(flavorSelection);
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

        // these modules are APIs that are present in the platform and shouldn't be included in one's app.
        private static final List<String> EXCLUDED_MODULES =
                ImmutableList.of(
                        "org.apache.httpcomponents:httpclient",
                        "xpp3:xpp3",
                        "commons-logging:commons-logging",
                        "xerces:xmlParserAPIs",
                        "org.json:json",
                        "org.khronos:opengl-api");

        private void filterOutBadArtifacts(@NonNull Configuration configuration) {
            Action<ComponentSelectionRules> ruleAction =
                    rules -> {
                        // always reject the broken android jar from mavencentral.
                        rules.withModule(
                                "com.google.android:android",
                                componentSelection ->
                                        componentSelection.reject(
                                                "This module is a copy of the android API provided by the SDK. Please exclude it from your dependencies."));

                        // remove APIs already on the platform if not running unit tests.
                        if (variantConfiguration.getType() != VariantType.UNIT_TEST) {
                            for (String module : EXCLUDED_MODULES) {
                                rules.withModule(
                                        module,
                                        componentSelection ->
                                                componentSelection.reject(
                                                        "Conflicts with the internal version provided by Android. Please exclude it from your dependencies."));
                            }
                        }
                    };

            configuration.resolutionStrategy(
                    resolutionStrategy -> resolutionStrategy.componentSelection(ruleAction));
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

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", variantName)
                .toString();
    }
}
