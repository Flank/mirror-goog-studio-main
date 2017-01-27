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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.ConfigurationProvider;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.model.SyncIssue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;

/**
 * Object that represents the dependencies of a "config", in the sense of defaultConfigs, build
 * type and flavors.
 *
 * <p>The dependencies are expressed as composite Gradle configuration objects that extends
 * all the configuration objects of the "configs".</p>
 *
 * <p>It optionally contains the dependencies for a test config for the given config.</p>
 */
public class VariantDependencies {

    /** Name of the metadata configuration */
    public static final String CONFIGURATION_METADATA = "-metadata";
    /** Name of the mapping configuration */
    public static final String CONFIGURATION_MAPPING = "-mapping";
    /** Name of the classes configuration */
    public static final String CONFIGURATION_CLASSES = "-classes";
    /** Name of the manifest configuration */
    public static final String CONFIGURATION_MANIFEST = "-manifest";

    @NonNull
    private final String variantName;

    @NonNull
    private final Configuration compileConfiguration;
    @NonNull
    private final Configuration packageConfiguration;
    @Nullable
    private final Configuration publishConfiguration;
    @NonNull
    private final Configuration annotationProcessorConfiguration;
    @NonNull
    private final Configuration jackPluginConfiguration;

    @Nullable
    private final Configuration mappingConfiguration;
    @Nullable
    private final Configuration classesConfiguration;
    @Nullable
    private final Configuration metadataConfiguration;
    @Nullable
    private final VariantDependencies testedVariantDependencies;
    @Nullable
    private final AndroidDependency testedVariantOutput;

    @Nullable
    private Configuration manifestConfiguration;

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
        private final String variantName;
        @NonNull
        private final VariantType variantType;
        private boolean publishVariant = false;
        private VariantType testedVariantType = null;
        private VariantDependencies testedVariantDependencies = null;
        private AndroidDependency testedVariantOutput = null;

        // default size should be enough. It's going to be rare for a variant to include
        // more than a few configurations (main, build-type, flavors...)
        // At most it's going to be flavor dimension count + 5:
        // variant-specific, build type, multi-flavor, flavor1, ..., flavorN, defaultConfig, test.
        // Default hashmap size of 16 (w/ load factor of .75) should be enough.
        private final Set<Configuration> compileConfigs = Sets.newHashSet();
        private final Set<Configuration> apkConfigs = Sets.newHashSet();
        private final Set<Configuration> annotationConfigs = Sets.newHashSet();
        private final Set<Configuration> jackPluginConfigs = Sets.newHashSet();

        protected Builder(
            @NonNull Project project,
            @NonNull ErrorReporter errorReporter,
            @NonNull String variantName,
            @NonNull VariantType variantType) {

            this.project = project;
            this.errorReporter = errorReporter;
            this.variantName = variantName;
            this.variantType = variantType;
        }

        public Builder setPublishVariant(boolean publishVariant) {
            this.publishVariant = publishVariant;
            return this;
        }

        public Builder setTestedVariantType(@NonNull VariantType testedVariantType) {
            this.testedVariantType = testedVariantType;
            return this;
        }

        public Builder addProviders(@NonNull ConfigurationProvider... providers) {
            for (ConfigurationProvider provider : providers) {
                addProvider(provider);
            }
            return this;
        }

        public Builder addProviders(@NonNull Collection<ConfigurationProvider> providers) {
            for (ConfigurationProvider provider : providers) {
                addProvider(provider);
            }
            return this;
        }

        public Builder addProvider(@Nullable ConfigurationProvider provider) {
            if (provider != null) {
                compileConfigs.add(provider.getCompileConfiguration());
                if (provider.getProvidedConfiguration() != null) {
                    compileConfigs.add(provider.getProvidedConfiguration());
                }

                apkConfigs.add(provider.getCompileConfiguration());
                apkConfigs.add(provider.getPackageConfiguration());
                annotationConfigs.add(provider.getAnnotationProcessorConfiguration());
                jackPluginConfigs.add(provider.getJackPluginConfiguration());
            }

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

            // if the tested variant is a library, then we include its configurations to the
            // variant config
            if (testedVariantType == VariantType.LIBRARY) {
                compileConfigs.add(testedVariant.getCompileConfiguration());
                apkConfigs.add(testedVariant.getPackageConfiguration());
                annotationConfigs.add(testedVariant.getAnnotationProcessorConfiguration());
                jackPluginConfigs.add(testedVariant.getJackPluginConfiguration());

                // also record this so that we can resolve local jar conflict during flattening
                testedVariantOutput = testedConfig.getOutput();
            }

            // record this no matter what.
            testedVariantDependencies = testedVariant;

            return this;
        }

        public VariantDependencies build() {
            Configuration compile = project.getConfigurations().maybeCreate("_" + variantName + "Compile");
            compile.setVisible(false);
            compile.setDescription("## Internal use, do not manually configure ##");
            compile.setExtendsFrom(compileConfigs);

            Configuration annotationProcessor =
                    project.getConfigurations().maybeCreate("_" + variantName + "AnnotationProcessor");
            annotationProcessor.setVisible(false);
            annotationProcessor.setDescription("## Internal use, do not manually configure ##");
            annotationProcessor.setExtendsFrom(annotationConfigs);

            Configuration jackPlugin =
                    project.getConfigurations().maybeCreate("_" + variantName + "JackPlugin");
            jackPlugin.setVisible(false);
            jackPlugin.setDescription("## Internal use, do not manually configure ##");
            jackPlugin.setExtendsFrom(jackPluginConfigs);

            Configuration apk =
                    project.getConfigurations()
                            .maybeCreate(
                                    variantType == VariantType.LIBRARY
                                            ? "_" + variantName + "Publish"
                                            : "_" + variantName + "Apk");

            apk.setVisible(false);
            apk.setDescription("## Internal use, do not manually configure ##");
            apk.setExtendsFrom(apkConfigs);

            Configuration publish = null;
            Configuration mapping = null;
            Configuration classes = null;
            Configuration metadata = null;
            Configuration manifest = null;

            if (publishVariant) {
                publish = project.getConfigurations().maybeCreate(variantName);
                publish.setDescription("Published Configuration for Variant " + variantName);
                // if the variant is not a library, then the publishing configuration should
                // not extend from the apkConfigs. It's mostly there to access the artifact from
                // another project but it shouldn't bring any dependencies with it.
                if (variantType == VariantType.LIBRARY) {
                    publish.setExtendsFrom(apkConfigs);
                }

                // create configuration for -metadata.
                metadata = project.getConfigurations().create(variantName + CONFIGURATION_METADATA);
                metadata.setDescription("Published APKs metadata for Variant " + variantName);

                // create configuration for -mapping and -classes.
                mapping = project.getConfigurations().maybeCreate(variantName + CONFIGURATION_MAPPING);
                mapping.setDescription("Published mapping configuration for Variant " + variantName);

                classes = project.getConfigurations().maybeCreate(variantName + CONFIGURATION_CLASSES);
                classes.setDescription("Published classes configuration for Variant " + variantName);

                // create configuration for -manifest
                manifest =
                        project.getConfigurations().maybeCreate(variantName + CONFIGURATION_MANIFEST);
                manifest.setDescription("Published manifest configuration for Variant " + variantName);

                // because we need the transitive dependencies for the classes, extend the compile config.
                classes.setExtendsFrom(compileConfigs);
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
                    compile,
                    apk,
                    publish,
                    annotationProcessor,
                    jackPlugin,
                    mapping,
                    classes,
                    metadata,
                    manifest,
                    testedVariantDependencies,
                    testedVariantOutput);
        }
    }

    public static Builder builder(
            @NonNull Project project,
            @NonNull ErrorReporter errorReporter,
            @NonNull String variantName,
            @NonNull VariantType variantType) {
        return new Builder(project, errorReporter, variantName, variantType);
    }

    private VariantDependencies(
            @NonNull String variantName,
            @NonNull DependencyChecker dependencyChecker,
            @NonNull Configuration compileConfiguration,
            @NonNull Configuration packageConfiguration,
            @Nullable Configuration publishConfiguration,
            @NonNull Configuration annotationProcessorConfiguration,
            @NonNull Configuration jackPluginConfiguration,
            @Nullable Configuration mappingConfiguration,
            @Nullable Configuration classesConfiguration,
            @Nullable Configuration metadataConfiguration,
            @Nullable Configuration manifestConfiguration,
            @Nullable VariantDependencies testedVariantDependencies,
            @Nullable AndroidDependency testedVariantOutput) {
        this.variantName = variantName;
        this.checker = dependencyChecker;
        this.compileConfiguration = compileConfiguration;
        this.packageConfiguration = packageConfiguration;
        this.publishConfiguration = publishConfiguration;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.jackPluginConfiguration = jackPluginConfiguration;
        this.mappingConfiguration = mappingConfiguration;
        this.classesConfiguration = classesConfiguration;
        this.metadataConfiguration = metadataConfiguration;
        this.manifestConfiguration = manifestConfiguration;
        this.testedVariantDependencies = testedVariantDependencies;
        this.testedVariantOutput = testedVariantOutput;
    }

    public String getName() {
        return variantName;
    }

    @NonNull
    public Configuration getCompileConfiguration() {
        return compileConfiguration;
    }

    @NonNull
    public Configuration getPackageConfiguration() {
        return packageConfiguration;
    }

    @Nullable
    public Configuration getPublishConfiguration() {
        return publishConfiguration;
    }

    @NonNull
    public Configuration getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration;
    }

    @NonNull
    public Configuration getJackPluginConfiguration() {
        return jackPluginConfiguration;
    }

    @Nullable
    public Configuration getMappingConfiguration() {
        return mappingConfiguration;
    }

    @Nullable
    public Configuration getClassesConfiguration() {
        return classesConfiguration;
    }

    @Nullable
    public Configuration getMetadataConfiguration() {
        return metadataConfiguration;
    }

    @Nullable
    public Configuration getManifestConfiguration() {
        return manifestConfiguration;
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
    public Set<File> resolveAndGetAnnotationProcessorClassPath(
            boolean includeClasspath,
            @NonNull ErrorReporter errorReporter) {
        if (getAnnotationProcessorConfiguration().getAllDependencies().isEmpty()) {
            return Collections.emptySet();
        }

        if (getAnnotationProcessorConfiguration().getState() != Configuration.State.RESOLVED
                && includeClasspath) {
            getAnnotationProcessorConfiguration().extendsFrom(
                    getCompileConfiguration(),
                    getPackageConfiguration());
        }
        ResolvedConfiguration resolvedConfiguration =
                getAnnotationProcessorConfiguration().getResolvedConfiguration();
        if (resolvedConfiguration.hasError()) {
            try {
                resolvedConfiguration.rethrowFailure();
            } catch (Exception e) {
                errorReporter.handleSyncError(
                        "annotationProcessor",
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        e.getMessage());
                return Collections.emptySet();
            }
        }
        return getAnnotationProcessorConfiguration()
                .getFiles()
                .stream()
                .filter(file -> !file.getPath().endsWith(SdkConstants.DOT_AAR))
                .collect(Collectors.toSet());
    }

    @NonNull
    public Set<File> resolveAndGetJackPluginClassPath(
            @NonNull ErrorReporter errorReporter) {
        if (getJackPluginConfiguration().getAllDependencies().isEmpty()) {
            return Collections.emptySet();
        }

        ResolvedConfiguration resolvedConfiguration =
                getJackPluginConfiguration().getResolvedConfiguration();
        if (resolvedConfiguration.hasError()) {
            try {
                resolvedConfiguration.rethrowFailure();
            } catch (Exception e) {
                errorReporter.handleSyncError(
                        "jackPlugin",
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        "Unable to find Jack plugin. " + e.getMessage());
                return Collections.emptySet();
            }
        }
        return getJackPluginConfiguration().getFiles();
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
