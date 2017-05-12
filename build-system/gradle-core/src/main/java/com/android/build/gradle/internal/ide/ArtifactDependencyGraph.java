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

import static com.android.SdkConstants.EXT_AAR;
import static com.android.SdkConstants.EXT_JAR;
import static com.android.build.gradle.internal.ide.ModelBuilder.EMPTY_DEPENDENCIES_IMPL;
import static com.android.build.gradle.internal.ide.ModelBuilder.EMPTY_DEPENDENCY_GRAPH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact;
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs;
import com.android.build.gradle.internal.dependency.VariantAttr;
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl;
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl;
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.caching.CreatingCache;
import com.android.utils.ImmutableCollectors;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.component.Artifact;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;

/** For creating dependency graph based on {@link ResolvedArtifactResult}. */
public class ArtifactDependencyGraph {

    private static final String LOCAL_AAR_GROUPID = "__local_aars__";

    private static final CreatingCache<HashableResolvedArtifactResult, MavenCoordinates>
            sMavenCoordinatesCache =
                    new CreatingCache<>(ArtifactDependencyGraph::computeMavenCoordinates);

    private static final CreatingCache<HashableResolvedArtifactResult, Library> sLibraryCache =
            new CreatingCache<>(ArtifactDependencyGraph::instantiateLibrary);
    private static final Map<String, Library> sGlobalLibrary = Maps.newHashMap();

    private final List<Throwable> failures = Lists.newArrayList();

    public static void clearCaches() {
        sMavenCoordinatesCache.clear();
        sLibraryCache.clear();
    }

    @NonNull
    private static Library instantiateLibrary(@NonNull HashableResolvedArtifactResult artifact) {
        Library library;
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        String address = ArtifactDependencyGraph.computeAddress(artifact);

        if (id instanceof ProjectComponentIdentifier) {
            library =
                    new ModuleLibraryImpl(
                            address,
                            artifact.getFile(),
                            ((ProjectComponentIdentifier) id).getProjectPath(),
                            getVariant(artifact));
        } else if (artifact.isJava) {
            library =
                    new com.android.build.gradle.internal.ide.level2.JavaLibraryImpl(
                            address, artifact.getFile());
        } else {
            library =
                    new AndroidLibraryImpl(
                            address,
                            null, /* artifactFile */
                            artifact.getFile(),
                            ImmutableList.of()); // FIXME: get local jar override
        }

        synchronized (sGlobalLibrary) {
            sGlobalLibrary.put(library.getArtifactAddress(), library);
        }

        return library;
    }

    public static Map<String, Library> getGlobalLibMap() {
        return ImmutableMap.copyOf(sGlobalLibrary);
    }

    @Nullable
    public static String getVariant(@NonNull ResolvedArtifactResult artifact) {
        VariantAttr variantAttr =
                artifact.getVariant().getAttributes().getAttribute(VariantAttr.ATTRIBUTE);
        return variantAttr == null ? null : variantAttr.getName();
    }

    private static String computeAddress(@NonNull HashableResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (id instanceof ProjectComponentIdentifier) {
            String variant = getVariant(artifact);
            if (variant == null) {
                return ((ProjectComponentIdentifier) id).getProjectPath().intern();
            } else {
                return (((ProjectComponentIdentifier) id).getProjectPath() + "::" + variant)
                        .intern();
            }
        } else if (id instanceof ModuleComponentIdentifier || id instanceof OpaqueComponentArtifactIdentifier) {
            MavenCoordinates coordinates = sMavenCoordinatesCache.get(artifact);
            checkNotNull(coordinates);
            return coordinates.toString().intern();
        } else {
            throw new RuntimeException(
                    "Don't know how to handle ComponentIdentifier '"
                            + id.getDisplayName()
                            + "'of type "
                            + id.getClass());
        }
    }

    @NonNull
    private static MavenCoordinates computeMavenCoordinates(
            @NonNull ResolvedArtifactResult artifact) {
        // instance should be a hashable.
        HashableResolvedArtifactResult hashableResult = (HashableResolvedArtifactResult) artifact;

        ComponentIdentifier id = artifact.getId().getComponentIdentifier();

        final File artifactFile = artifact.getFile();
        final String fileName = artifactFile.getName();
        String extension = hashableResult.isJava ? EXT_JAR : EXT_AAR;
        if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) id;
            final String module = moduleComponentId.getModule();
            final String version = moduleComponentId.getVersion();
            String classifier = null;

            if (!artifact.getFile().isDirectory()) {
                // attempts to compute classifier based on the filename.
                String pattern = "^" + module + "-" + version + "-(.+)\\." + extension + "$";

                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(fileName);
                if (m.matches()) {
                    classifier = m.group(1);
                }
            }

            return new MavenCoordinatesImpl(
                    moduleComponentId.getGroup(), module, version, extension, classifier);
        } else if (id instanceof ProjectComponentIdentifier) {
            return new MavenCoordinatesImpl(
                    "artifacts", ((ProjectComponentIdentifier) id).getProjectPath(), "unspecified");
        } else if (id instanceof OpaqueComponentArtifactIdentifier) {
            // We have a file based dependency
            if (hashableResult.isJava) {
                return JavaDependency.getCoordForLocalJar(artifactFile);
            } else {
                // local aar?
                assert artifactFile.isDirectory();
                return getMavenCoordForLocalFile(artifactFile);
            }
        }

        throw new RuntimeException(
                "Don't know how to compute maven coordinate for artifact '"
                        + artifact.getId().getDisplayName()
                        + "' with component identifier of type '"
                        + id.getClass()
                        + "'.");
    }

    @NonNull
    public static MavenCoordinatesImpl getMavenCoordForLocalFile(File artifactFile) {
        return new MavenCoordinatesImpl(LOCAL_AAR_GROUPID, artifactFile.getPath(), "unspecified");
    }

    /**
     * Returns a set of HashableResolvedArtifactResult where the {@link
     * HashableResolvedArtifactResult#isJava} field as been setup properly.
     */
    private Set<HashableResolvedArtifactResult> getAllArtifacts(
            @NonNull VariantScope variantScope,
            AndroidArtifacts.ConsumedConfigType consumedConfigType) {
        // Query for all the JARs. This will give us every dependency, even the Android ones (
        // sub-projects and external) as they publish (or transform int) a JAR artifact.
        // This list is enough to find external vs sub-projects, but not enough to find whether
        // and external dependency is a Android or a Java library.
        // We use a 2nd query: exploded AAR on external libraries only for this.
        ArtifactCollection mainArtifactList =
                variantScope.getArtifactCollection(
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.JAR);

        ArtifactCollection externalAarList =
                variantScope.getArtifactCollection(
                        consumedConfigType,
                        // FIXME once we only support level two, we can pass ArtifactScope.EXTERNAL here
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.EXPLODED_AAR);

        // because the ArtifactCollection could be a collection over a test variant which ends
        // up being a ArtifactCollectionWithExtraArtifact, we need to get the actual list
        // without the tested artifact.
        if (mainArtifactList instanceof ArtifactCollectionWithExtraArtifact) {
            mainArtifactList =
                    ((ArtifactCollectionWithExtraArtifact) mainArtifactList).getParentArtifacts();
        }
        if (externalAarList instanceof ArtifactCollectionWithExtraArtifact) {
            externalAarList =
                    ((ArtifactCollectionWithExtraArtifact) externalAarList).getParentArtifacts();
        }

        // collect dependency resolution failures
        failures.addAll(externalAarList.getFailures());

        // build a list of external AARs. Put the hashable result directly in it as we'll want these
        // instead of the other ones in order to have direct access to the exploded aar.
        Map<ComponentIdentifier, HashableResolvedArtifactResult> externalAarMap = Maps.newHashMap();
        for (ResolvedArtifactResult result : externalAarList.getArtifacts()) {
            externalAarMap.put(
                    result.getId().getComponentIdentifier(),
                    new HashableResolvedArtifactResult(result, false /*isJava*/));
        }

        // build the final list.
        final Set<ResolvedArtifactResult> mainArtifacts = mainArtifactList.getArtifacts();
        Set<HashableResolvedArtifactResult> artifacts = Sets.newLinkedHashSet();
        for (ResolvedArtifactResult result : mainArtifacts) {
            // if this is an external dependency, check if it's an AAR.
            final ComponentIdentifier componentIdentifier = result.getId().getComponentIdentifier();

            if (externalAarMap.containsKey(componentIdentifier)) {
                // use the value from the map directly.
                artifacts.add(externalAarMap.get(componentIdentifier));
            } else {
                // this is not an AAR, this is a java library
                artifacts.add(new HashableResolvedArtifactResult(result, true /*isJava*/));
            }
        }

        // force download the javadoc/source artifacts
        handleJavadoc(
                variantScope.getGlobalScope().getProject(),
                artifacts
                        .stream()
                        .map(artifactResult -> artifactResult.getId().getComponentIdentifier())
                        .collect(Collectors.toList()));

        return artifacts;
    }

    /** Create a level 2 dependency graph. */
    public DependencyGraphs createLevel2DependencyGraph(
            @NonNull VariantScope variantScope, boolean withFullDependency) {
        List<GraphItem> compileItems = Lists.newArrayList();

        Set<HashableResolvedArtifactResult> artifacts =
                getAllArtifacts(variantScope, COMPILE_CLASSPATH);

        for (HashableResolvedArtifactResult artifact : artifacts) {
            compileItems.add(new GraphItemImpl(computeAddress(artifact), ImmutableList.of()));
            sLibraryCache.get(artifact);
        }

        if (!withFullDependency) {
            return new SimpleDependencyGraphsImpl(compileItems);
        }

        // FIXME: when full dependency is enabled, this should return a full graph instead of a
        // flat list.

        List<GraphItem> runtimeItems = Lists.newArrayList();

        artifacts = getAllArtifacts(variantScope, RUNTIME_CLASSPATH);

        for (HashableResolvedArtifactResult artifact : artifacts) {
            runtimeItems.add(new GraphItemImpl(computeAddress(artifact), ImmutableList.of()));
            sLibraryCache.get(artifact);
        }
        List<GraphItem> providedItems = Lists.newArrayList(compileItems);
        providedItems.removeAll(runtimeItems);

        return new FullDependencyGraphsImpl(
                compileItems,
                runtimeItems,
                providedItems
                        .stream()
                        .map(GraphItem::getArtifactAddress)
                        .collect(ImmutableCollectors.toImmutableList()),
                ImmutableList.of()); // FIXME: actually get skip list
    }

    /** Create a level 1 dependency list. */
    public DependenciesImpl createDependencies(VariantScope variantScope) {
        ImmutableList.Builder<String> projects = ImmutableList.builder();
        ImmutableList.Builder<AndroidLibrary> androidLibraries = ImmutableList.builder();
        ImmutableList.Builder<JavaLibrary> javaLibrary = ImmutableList.builder();

        Set<HashableResolvedArtifactResult> artifacts =
                getAllArtifacts(variantScope, COMPILE_CLASSPATH);

        for (HashableResolvedArtifactResult artifact : artifacts) {
            ComponentIdentifier id = artifact.getId().getComponentIdentifier();

            boolean isSubproject = id instanceof ProjectComponentIdentifier;
            String projectPath =
                    isSubproject ? ((ProjectComponentIdentifier) id).getProjectPath() : null;

            if (artifact.isJava) {
                if (projectPath != null) {
                    projects.add(projectPath);
                    continue;
                }
                // FIXME: Dependencies information is not set correctly.
                javaLibrary.add(
                        new JavaLibraryImpl(
                                artifact.getFile(),
                                null,
                                ImmutableList.of(), /* dependencies */
                                null, /* requestedCoordinates */
                                checkNotNull(sMavenCoordinatesCache.get(artifact)),
                                false, /* isSkipped */
                                false)); /* isProvided */
            } else {
                //noinspection VariableNotUsedInsideIf
                androidLibraries.add(
                        new com.android.build.gradle.internal.ide.AndroidLibraryImpl(
                                // FIXME: Dependencies information is not set correctly.
                                checkNotNull(sMavenCoordinatesCache.get(artifact)),
                                projectPath,
                                artifact.getFile(), /*exploded folder*/
                                getVariant(artifact),
                                false, /* dependencyItem.isProvided() */
                                false, /* dependencyItem.isSkipped() */
                                ImmutableList.of(), /* androidLibraries */
                                ImmutableList.of(), /* javaLibraries */
                                ImmutableList.of())); /*localJarOverride */
            }
        }

        return new DependenciesImpl(
                androidLibraries.build(), javaLibrary.build(), projects.build());
    }

    @NonNull
    public List<String> collectFailures() {
        if (failures.isEmpty()) {
            return ImmutableList.of();
        }

        Pattern pattern =
                Pattern.compile(".*any matches for ([a-zA-Z0-9:\\-.+]+) .*", Pattern.DOTALL);
        Pattern pattern2 =
                Pattern.compile(".*Could not find ([a-zA-Z0-9:\\-.]+)\\..*", Pattern.DOTALL);

        return failures.stream()
                .map(
                        throwable -> {
                            if (throwable instanceof ResolveException) {
                                throwable = throwable.getCause();
                            }

                            String message = throwable.getMessage();

                            Matcher m = pattern.matcher(message);
                            if (m.matches()) {
                                return m.group(1);
                            }

                            m = pattern2.matcher(message);
                            if (m.matches()) {
                                return m.group(1);
                            }

                            return throwable.getMessage();
                        })
                .collect(Collectors.toList());
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
        List<String> projects = Collections.emptyList();

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
        return new SimpleDependencyGraphsImpl(nodes);
    }

    private void handleJavadoc(@NonNull Project project, List<ComponentIdentifier> artifacts) {

        final DependencyHandler dependencies = project.getDependencies();

        ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery();
        query.forComponents(artifacts);

        @SuppressWarnings("unchecked")
        Class<? extends Artifact>[] artifactTypesArray =
                (Class<? extends Artifact>[])
                        new Class<?>[] {JavadocArtifact.class, SourcesArtifact.class};
        query.withArtifacts(JvmLibrary.class, artifactTypesArray);
        query.execute().getResolvedComponents();
    }

    private static class HashableResolvedArtifactResult implements ResolvedArtifactResult {
        @NonNull private ResolvedArtifactResult delegate;
        private final boolean isJava;

        public HashableResolvedArtifactResult(
                @NonNull ResolvedArtifactResult delegate, boolean isJava) {
            this.delegate = delegate;
            this.isJava = isJava;
        }

        @Override
        public File getFile() {
            return delegate.getFile();
        }

        @Override
        public ResolvedVariantResult getVariant() {
            return delegate.getVariant();
        }

        @Override
        public ComponentArtifactIdentifier getId() {
            return delegate.getId();
        }

        @Override
        public Class<? extends Artifact> getType() {
            return delegate.getType();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HashableResolvedArtifactResult that = (HashableResolvedArtifactResult) o;
            return Objects.equal(getFile(), that.getFile())
                    && Objects.equal(getId(), that.getId())
                    && Objects.equal(getType(), that.getType())
                    && isJava == that.isJava;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getFile(), getId(), getType(), isJava);
        }
    }
}
