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

package com.android.build.gradle.internal.ide;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_AAR_LIBS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.android.build.gradle.internal.ide.ModelBuilder.EMPTY_DEPENDENCIES_IMPL;
import static com.android.build.gradle.internal.ide.ModelBuilder.EMPTY_DEPENDENCY_GRAPH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact;
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs;
import com.android.build.gradle.internal.ide.dependencies.ArtifactUtils;
import com.android.build.gradle.internal.ide.dependencies.BuildMappingUtils;
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact;
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact.DependencyType;
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl;
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl;
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl;
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.res.namespaced.AutoNamespaceLocation;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.caching.CreatingCache;
import com.android.utils.FileUtils;
import com.android.utils.ImmutableCollectors;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;

/** For creating dependency graph based on {@link ResolvedArtifactResult}. */
public class ArtifactDependencyGraph {

    private static final CreatingCache<ResolvedArtifact, Library> sLibraryCache =
            new CreatingCache<>(ArtifactDependencyGraph::instantiateLibrary);
    private static final Map<String, Library> sGlobalLibrary = Maps.newHashMap();

    private DependencyFailureHandler dependencyFailureHandler = new DependencyFailureHandler();

    public static void clearCaches() {
        sLibraryCache.clear();
    }

    @NonNull
    private static Library instantiateLibrary(@NonNull ResolvedArtifact artifact) {
        Library library;
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        String address = artifact.computeModelAddress();

        if (!(id instanceof ProjectComponentIdentifier) || artifact.isWrappedModule()) {
            if (artifact.getDependencyType() == DependencyType.ANDROID) {
                final File extractedFolder =
                        Preconditions.checkNotNull(artifact.getExtractedFolder());
                library =
                        new AndroidLibraryImpl(
                                address,
                                artifact.getArtifactFile(),
                                extractedFolder,
                                // TODO(b/110879504): Auto-namespacing in level4 model
                                findResStaticLibrary(artifact),
                                findLocalJarsAsStrings(extractedFolder));
            } else {
                library = new JavaLibraryImpl(address, artifact.getArtifactFile());
            }
        } else {
            // get the build ID
            final ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) id;
            String buildId = BuildMappingUtils.getBuildId(projectId, artifact.getBuildMapping());

            library =
                    new ModuleLibraryImpl(
                            address,
                            buildId,
                            projectId.getProjectPath(),
                            artifact.getVariantName());
        }

        synchronized (sGlobalLibrary) {
            sGlobalLibrary.put(library.getArtifactAddress(), library);
        }

        return library;
    }

    public static Map<String, Library> getGlobalLibMap() {
        return ImmutableMap.copyOf(sGlobalLibrary);
    }



    /**
     * Returns a set of ResolvedArtifact where the {@link ResolvedArtifact#getDependencyType()} and
     * {@link ResolvedArtifact#isWrappedModule()} fields have been setup properly.
     *
     * @param variantScope the variant to get the artifacts from
     * @param consumedConfigType the type of the dependency to resolve (compile vs runtime)
     * @param dependencyFailureHandler handler for dependency resolution errors
     * @param buildMapping a build mapping from build name to root dir.
     */
    public static Set<ResolvedArtifact> getAllArtifacts(
            @NonNull VariantScope variantScope,
            @NonNull AndroidArtifacts.ConsumedConfigType consumedConfigType,
            @Nullable DependencyFailureHandler dependencyFailureHandler,
            @NonNull ImmutableMap<String, String> buildMapping) {
        // FIXME change the way we compare dependencies b/64387392

        // we need to figure out the following:
        // - Is it an external dependency or a sub-project?
        // - Is it an android or a java dependency

        // Querying for JAR type gives us all the dependencies we care about, and we can use this
        // to differentiate external vs sub-projects (to a certain degree).
        // Note: Query for JAR instead of PROCESSED_JAR due to b/110054209
        ArtifactCollection allArtifactList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.JAR);

        // Then we can query for MANIFEST that will give us only the Android project so that we
        // can detect JAVA vs ANDROID.
        ArtifactCollection manifestList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.MANIFEST);
        ArtifactCollection nonNamespacedManifestList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST);

        // We still need to understand wrapped jars and aars. The former is difficult (TBD), but
        // the latter can be done by querying for EXPLODED_AAR. If a sub-project is in this list,
        // then we need to override the type to be external, rather than sub-project.
        // This is why we query for Scope.ALL
        // But we also simply need the exploded AARs for external Android dependencies so that
        // Studio can access the content.
        ArtifactCollection explodedAarList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.EXPLODED_AAR);

        // We also need the actual AARs so that we can get the artifact location and find the source
        // location from it.
        // Note: Query for AAR instead of PROCESSED_AAR due to b/110054209
        ArtifactCollection aarList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        AndroidArtifacts.ArtifactType.AAR);


        // collect dependency resolution failures
        if (dependencyFailureHandler != null) {
            // compute the name of the configuration
            dependencyFailureHandler.addErrors(
                    variantScope.getGlobalScope().getProject().getPath()
                            + "@"
                            + variantScope.getFullVariantName()
                            + "/"
                            + consumedConfigType.getName(),
                    allArtifactList.getFailures());
        }

        // build a list of wrapped AAR, and a map of all the exploded-aar artifacts
        final Set<ComponentIdentifier> wrapperModules = new HashSet<>();
        final Set<ResolvedArtifactResult> explodedAarArtifacts = explodedAarList.getArtifacts();
        final Map<ComponentIdentifier, ResolvedArtifactResult> explodedAarResults =
                Maps.newHashMapWithExpectedSize(explodedAarArtifacts.size());
        for (ResolvedArtifactResult result : explodedAarArtifacts) {
            final ComponentIdentifier componentIdentifier = result.getId().getComponentIdentifier();
            if (componentIdentifier instanceof ProjectComponentIdentifier) {
                wrapperModules.add(componentIdentifier);
            }
            explodedAarResults.put(componentIdentifier, result);
        }

        final Set<ResolvedArtifactResult> aarArtifacts = aarList.getArtifacts();
        final Map<ComponentIdentifier, ResolvedArtifactResult> aarResults =
                Maps.newHashMapWithExpectedSize(aarArtifacts.size());
        for (ResolvedArtifactResult result : aarArtifacts) {
            aarResults.put(result.getId().getComponentIdentifier(), result);
        }

        // build a list of android dependencies based on them publishing a MANIFEST element
        final Set<ResolvedArtifactResult> manifestArtifacts = new HashSet<>();
        manifestArtifacts.addAll(manifestList.getArtifacts());
        manifestArtifacts.addAll(nonNamespacedManifestList.getArtifacts());

        final Set<ComponentIdentifier> manifestIds =
                Sets.newHashSetWithExpectedSize(manifestArtifacts.size());
        for (ResolvedArtifactResult result : manifestArtifacts) {
            manifestIds.add(result.getId().getComponentIdentifier());
        }

        // build the final list, using the main list augmented with data from the previous lists.
        final Set<ResolvedArtifactResult> allArtifacts = allArtifactList.getArtifacts();

        // use a linked hash set to keep the artifact order.
        final Set<ResolvedArtifact> artifacts =
                Sets.newLinkedHashSetWithExpectedSize(allArtifacts.size());

        for (ResolvedArtifactResult artifact : allArtifacts) {
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier();

            // check if this is a wrapped module
            boolean isWrappedModule = wrapperModules.contains(componentIdentifier);

            // check if this is an android external module. In this case, we want to use the exploded
            // aar as the artifact we depend on rather than just the JAR, so we swap out the
            // ResolvedArtifactResult.
            DependencyType dependencyType = DependencyType.JAVA;

            // in case of AAR, the current artifact is the extracted artifacts. It needs to
            // be swapped with the AAR bundle one.
            ResolvedArtifactResult mainArtifact = artifact;
            ResolvedArtifactResult extractedAar = null;

            // optional result that will point to the artifact (AAR) when the current result
            // is the exploded AAR.
            if (manifestIds.contains(componentIdentifier)) {
                dependencyType = DependencyType.ANDROID;
                // if it's an android dependency, we swap out the manifest result for the exploded
                // AAR result.
                // If the exploded AAR is null then it's a sub-project and we can keep the manifest
                // as the Library we'll create will be a ModuleLibrary which doesn't care about
                // the artifact file anyway.
                ResolvedArtifactResult explodedAar = explodedAarResults.get(componentIdentifier);
                if (explodedAar != null) {
                    extractedAar = explodedAar;

                    // and we need the AAR bundle itself (if it exists)
                    ResolvedArtifactResult aarBundle = aarResults.get(componentIdentifier);
                    if (aarBundle != null) {
                        mainArtifact = aarBundle;
                    }
                }
            }

            artifacts.add(
                    new ResolvedArtifact(
                            mainArtifact,
                            extractedAar,
                            dependencyType,
                            isWrappedModule,
                            buildMapping));
        }

        return artifacts;
    }

    @NonNull
    private static ArtifactCollection computeArtifactList(
            @NonNull VariantScope variantScope,
            @NonNull AndroidArtifacts.ConsumedConfigType consumedConfigType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType type) {
        ArtifactCollection artifacts =
                variantScope.getArtifactCollection(consumedConfigType, scope, type);

        // because the ArtifactCollection could be a collection over a test variant which ends
        // up being a ArtifactCollectionWithExtraArtifact, we need to get the actual list
        // without the tested artifact.
        if (artifacts instanceof ArtifactCollectionWithExtraArtifact) {
            return ((ArtifactCollectionWithExtraArtifact) artifacts).getParentArtifacts();
        }

        return artifacts;
    }

    /**
     * Create a level 4 dependency graph.
     *
     * @see AndroidProject#MODEL_LEVEL_4_NEW_DEP_MODEL
     */
    public DependencyGraphs createLevel4DependencyGraph(
            @NonNull VariantScope variantScope,
            boolean withFullDependency,
            boolean downloadSources,
            @NonNull ImmutableMap<String, String> buildMapping,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        // FIXME change the way we compare dependencies b/64387392

        try {
            // get the compile artifact first.
            Set<ResolvedArtifact> compileArtifacts =
                    getAllArtifacts(
                            variantScope,
                            COMPILE_CLASSPATH,
                            dependencyFailureHandler,
                            buildMapping);

            // force download the javadoc/source artifacts of compile scope only, since the
            // the runtime-only is never used from the IDE.
            if (downloadSources) {
                Set<ComponentIdentifier> ids =
                        Sets.newHashSetWithExpectedSize(compileArtifacts.size());
                for (ResolvedArtifact artifact : compileArtifacts) {
                    ids.add(artifact.getId().getComponentIdentifier());
                }

                handleSources(variantScope.getGlobalScope().getProject(), ids, failureConsumer);
            }

            // In this simpler model, faster computation of the runtime dependencies to get the
            // provided bit.
            if (!withFullDependency) {
                // get the runtime artifacts. We only care about the ComponentIdentifier so we don't
                // need to call getAllArtifacts() which computes a lot more many things, and takes
                // longer on large projects.
                // Instead just get all the jars to get all the dependencies.
                // Note: Query for JAR instead of PROCESSED_JAR due to b/110054209
                ArtifactCollection runtimeArtifactCollection =
                        computeArtifactList(
                                variantScope,
                                RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                AndroidArtifacts.ArtifactType.JAR);

                // build a list of the runtime ComponentIdentifiers
                final Set<ResolvedArtifactResult> runtimeArtifacts =
                        runtimeArtifactCollection.getArtifacts();
                final Set<ComponentIdentifier> runtimeIdentifiers =
                        Sets.newHashSetWithExpectedSize(runtimeArtifacts.size());
                for (ResolvedArtifactResult result : runtimeArtifacts) {
                    runtimeIdentifiers.add(result.getId().getComponentIdentifier());
                }

                List<String> providedAddresses = Lists.newArrayList();

                List<GraphItem> compileItems =
                        Lists.newArrayListWithCapacity(compileArtifacts.size());
                for (ResolvedArtifact artifact : compileArtifacts) {
                    final GraphItemImpl graphItem =
                            new GraphItemImpl(artifact.computeModelAddress(), ImmutableList.of());
                    compileItems.add(graphItem);
                    sLibraryCache.get(artifact);
                    if (!runtimeIdentifiers.contains(artifact.getId().getComponentIdentifier())) {
                        providedAddresses.add(graphItem.getArtifactAddress());
                    }
                }

                return new SimpleDependencyGraphsImpl(compileItems, providedAddresses);
            }

            // now build the list of compile items
            List<GraphItem> compileItems = Lists.newArrayListWithCapacity(compileArtifacts.size());
            for (ResolvedArtifact artifact : compileArtifacts) {
                compileItems.add(
                        new GraphItemImpl(artifact.computeModelAddress(), ImmutableList.of()));
                sLibraryCache.get(artifact);
            }

            // in this mode, compute GraphItem for the runtime configuration
            // get the runtime artifacts.
            Set<ResolvedArtifact> runtimeArtifacts =
                    getAllArtifacts(
                            variantScope,
                            RUNTIME_CLASSPATH,
                            dependencyFailureHandler,
                            buildMapping);

            List<GraphItem> runtimeItems = Lists.newArrayListWithCapacity(runtimeArtifacts.size());
            for (ResolvedArtifact artifact : runtimeArtifacts) {
                runtimeItems.add(
                        new GraphItemImpl(artifact.computeModelAddress(), ImmutableList.of()));
                sLibraryCache.get(artifact);
            }

            // compute the provided dependency list, by comparing the compile and runtime items
            List<GraphItem> providedItems = Lists.newArrayList(compileItems);
            providedItems.removeAll(runtimeItems);
            final ImmutableList<String> providedAddresses =
                    providedItems
                            .stream()
                            .map(GraphItem::getArtifactAddress)
                            .collect(ImmutableCollectors.toImmutableList());

            // FIXME: when full dependency is enabled, this should return a full graph instead of a
            // flat list.

            return new FullDependencyGraphsImpl(
                    compileItems,
                    runtimeItems,
                    providedAddresses,
                    ImmutableList.of()); // FIXME: actually get skip list
        } finally {
            dependencyFailureHandler.collectIssues().forEach(failureConsumer);
        }
    }

    /** Create a level 1 dependency list. */
    @NonNull
    public DependenciesImpl createDependencies(
            @NonNull VariantScope variantScope,
            boolean downloadSources,
            @NonNull ImmutableMap<String, String> buildMapping,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        // FIXME change the way we compare dependencies b/64387392

        try {
            ImmutableList.Builder<Dependencies.ProjectIdentifier> projects =
                    ImmutableList.builder();
            ImmutableList.Builder<AndroidLibrary> androidLibraries = ImmutableList.builder();
            ImmutableList.Builder<JavaLibrary> javaLibrary = ImmutableList.builder();

            // get the runtime artifact. We only care about the ComponentIdentifier so we don't
            // need to call getAllArtifacts() which computes a lot more many things.
            // Instead just get all the jars to get all the dependencies.
            // Note: Query for JAR instead of PROCESSED_JAR due to b/110054209
            ArtifactCollection runtimeArtifactCollection =
                    computeArtifactList(
                            variantScope,
                            RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.JAR);

            // build a list of the artifacts
            Set<ComponentIdentifier> runtimeIdentifiers =
                    new HashSet<>(runtimeArtifactCollection.getArtifacts().size());
            for (ResolvedArtifactResult result : runtimeArtifactCollection.getArtifacts()) {
                runtimeIdentifiers.add(result.getId().getComponentIdentifier());
            }

            Set<ResolvedArtifact> artifacts =
                    getAllArtifacts(
                            variantScope,
                            COMPILE_CLASSPATH,
                            dependencyFailureHandler,
                            buildMapping);

            for (ResolvedArtifact artifact : artifacts) {
                ComponentIdentifier id = artifact.getId().getComponentIdentifier();

                boolean isProvided = !runtimeIdentifiers.contains(id);

                boolean isSubproject = id instanceof ProjectComponentIdentifier;

                String projectPath = null;
                String buildId = null;
                if (isSubproject) {
                    final ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) id;
                    projectPath = projectId.getProjectPath();
                    buildId = BuildMappingUtils.getBuildId(projectId, buildMapping);
                }

                if (artifact.getDependencyType() == DependencyType.JAVA) {
                    if (projectPath != null) {
                        projects.add(
                                new DependenciesImpl.ProjectIdentifierImpl(buildId, projectPath));
                        continue;
                    }
                    // FIXME: Dependencies information is not set correctly.
                    javaLibrary.add(
                            new com.android.build.gradle.internal.ide.JavaLibraryImpl(
                                    artifact.getArtifactFile(),
                                    null, /* buildId */
                                    null, /* projectPath */
                                    ImmutableList.of(), /* dependencies */
                                    null, /* requestedCoordinates */
                                    ArtifactUtils.getMavenCoordinates(artifact),
                                    false, /* isSkipped */
                                    isProvided));
                } else {
                    if (artifact.isWrappedModule()) {
                        // force external dependency mode.
                        buildId = null;
                        projectPath = null;
                    }

                    File extractedFolder = artifact.getExtractedFolder();
                    if (extractedFolder == null) {
                        // fall back so the value is non null, in case of sub-modules which don't
                        // have aar/extracted folders.
                        extractedFolder = artifact.getArtifactFile();
                    }

                    androidLibraries.add(
                            new com.android.build.gradle.internal.ide.AndroidLibraryImpl(
                                    ArtifactUtils.getMavenCoordinates(artifact),
                                    buildId,
                                    projectPath,
                                    artifact.getArtifactFile(),
                                    extractedFolder,
                                    findResStaticLibrary(variantScope, artifact),
                                    artifact.getVariantName(),
                                    isProvided,
                                    false, /* dependencyItem.isSkipped() */
                                    ImmutableList.of(), /* androidLibraries */
                                    ImmutableList.of(), /* javaLibraries */
                                    findLocalJarsAsFiles(extractedFolder)));
                }
            }

            // force download the source artifacts of the compile classpath only.
            if (downloadSources) {
                Set<ComponentIdentifier> ids = Sets.newHashSetWithExpectedSize(artifacts.size());
                for (ResolvedArtifact artifact : artifacts) {
                    ids.add(artifact.getId().getComponentIdentifier());
                }

                handleSources(variantScope.getGlobalScope().getProject(), ids, failureConsumer);
            }

            return new DependenciesImpl(
                    androidLibraries.build(), javaLibrary.build(), projects.build());
        } finally {
            dependencyFailureHandler.collectIssues().forEach(failureConsumer);
        }
    }

    @NonNull
    public static Dependencies clone(@NonNull Dependencies dependencies, int modelLevel) {
        if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            return EMPTY_DEPENDENCIES_IMPL;
        }

        // these items are already ready for serializable, all we need to clone is
        // the Dependencies instance.
        List<AndroidLibrary> libraries = Collections.emptyList();
        List<JavaLibrary> javaLibraries = Lists.newArrayList(dependencies.getJavaLibraries());
        List<Dependencies.ProjectIdentifier> projects = Collections.emptyList();

        return new DependenciesImpl(libraries, javaLibraries, projects);
    }

    public static DependencyGraphs clone(
            @NonNull DependencyGraphs dependencyGraphs,
            int modelLevel,
            boolean modelWithFullDependency) {
        if (modelLevel < AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            return EMPTY_DEPENDENCY_GRAPH;
        }

        Preconditions.checkState(dependencyGraphs instanceof ConfigurationDependencyGraphs);
        ConfigurationDependencyGraphs cdg = (ConfigurationDependencyGraphs) dependencyGraphs;

        // these items are already ready for serializable, all we need to clone is
        // the DependencyGraphs instance.

        List<Library> libs = cdg.getLibraries();
        synchronized (sGlobalLibrary) {
            for (Library library : libs) {
                sGlobalLibrary.put(library.getArtifactAddress(), library);
            }
        }

        final List<GraphItem> nodes = cdg.getCompileDependencies();

        if (modelWithFullDependency) {
            return new FullDependencyGraphsImpl(
                    nodes, nodes, ImmutableList.of(), ImmutableList.of());
        }

        // just need to register the libraries in the global libraries.
        return new SimpleDependencyGraphsImpl(nodes, cdg.getProvidedLibraries());
    }

    private static void handleSources(
            @NonNull Project project,
            @NonNull Set<ComponentIdentifier> artifacts,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        final DependencyHandler dependencies = project.getDependencies();

        try {
            ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery();
            query.forComponents(artifacts);

            @SuppressWarnings("unchecked")
            Class<? extends Artifact>[] artifactTypesArray =
                    (Class<? extends Artifact>[]) new Class<?>[] {SourcesArtifact.class};
            query.withArtifacts(JvmLibrary.class, artifactTypesArray);
            ArtifactResolutionResult queryResult = query.execute();
            Set<ComponentArtifactsResult> resolvedComponents = queryResult.getResolvedComponents();

            // Create and execute another query to attempt javadoc resolution
            // where sources are not available
            Set<ComponentIdentifier> remainingToResolve = Sets.newHashSet();
            for (ComponentArtifactsResult componentResult : resolvedComponents) {
                Set<ArtifactResult> sourcesArtifacts =
                        componentResult.getArtifacts(SourcesArtifact.class);
                if (sourcesArtifacts.isEmpty()) {
                    remainingToResolve.add(componentResult.getId());
                }
            }
            if (!remainingToResolve.isEmpty()) {
                artifactTypesArray[0] = JavadocArtifact.class;
                query = dependencies.createArtifactResolutionQuery();
                query.forComponents(remainingToResolve);
                query.withArtifacts(JvmLibrary.class, artifactTypesArray);
                query.execute().getResolvedComponents();
            }
        } catch (Throwable t) {
            DependencyFailureHandlerKt.processDependencyThrowable(
                    t,
                    s -> null,
                    (data, messages) ->
                            failureConsumer.accept(
                                    new SyncIssueImpl(
                                            EvalIssueReporter.Type.GENERIC,
                                            EvalIssueReporter.Severity.WARNING,
                                            null,
                                            String.format(
                                                    "Unable to download sources/javadoc: %s",
                                                    messages.get(0)),
                                            messages)));
        }
    }


    @NonNull
    private static List<String> findLocalJarsAsStrings(@NonNull File folder) {
        File localJarRoot = FileUtils.join(folder, FD_JARS, FD_AAR_LIBS);

        if (!localJarRoot.isDirectory()) {
            return ImmutableList.of();
        }

        String[] jarFiles = localJarRoot.list((dir, name) -> name.endsWith(DOT_JAR));
        if (jarFiles != null && jarFiles.length > 0) {
            List<String> list = Lists.newArrayListWithCapacity(jarFiles.length);
            for (String jarFile : jarFiles) {
                list.add(FD_JARS + File.separatorChar + FD_AAR_LIBS + File.separatorChar + jarFile);
            }

            return list;
        }

        return ImmutableList.of();
    }

    @NonNull
    private static List<File> findLocalJarsAsFiles(@NonNull File folder) {
        File localJarRoot = FileUtils.join(folder, FD_JARS, FD_AAR_LIBS);

        if (!localJarRoot.isDirectory()) {
            return ImmutableList.of();
        }

        File[] jarFiles = localJarRoot.listFiles((dir, name) -> name.endsWith(DOT_JAR));
        if (jarFiles != null && jarFiles.length > 0) {
            return ImmutableList.copyOf(jarFiles);
        }

        return ImmutableList.of();
    }

    @Nullable
    private static File findResStaticLibrary(
            @NonNull VariantScope variantScope, @NonNull ResolvedArtifact explodedAar) {
        File file = findResStaticLibrary(explodedAar);
        if (file != null) {
            return file;
        }

        if (variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()
                && variantScope
                        .getGlobalScope()
                        .getProjectOptions()
                        .get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
            BuildArtifactsHolder artifacts = variantScope.getArtifacts();
            if (artifacts.hasArtifact(
                    InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES)) {
                // If it will be auto-namespaced.
                File convertedDirectory =
                        Iterables.get(
                                artifacts.getFinalArtifactFiles(
                                        InternalArtifactType
                                                .RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES),
                                0);
                return new File(
                        convertedDirectory,
                        AutoNamespaceLocation.getAutoNamespacedLibraryFileName(
                                explodedAar.getId().getComponentIdentifier()));
            }
        }
        // Not auto-namespaced, nor present in the original artifact
        return null;
    }

    @Nullable
    private static File findResStaticLibrary(@NonNull ResolvedArtifact explodedAar) {
        if (explodedAar.getExtractedFolder() == null) {
            return null;
        }

        File file = new File(explodedAar.getExtractedFolder(), FN_RESOURCE_STATIC_LIBRARY);
        if (!file.exists()) {
            return null;
        }
        return file;
    }

}
