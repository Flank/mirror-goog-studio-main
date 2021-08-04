/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.binaries;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tools.json.JsonFileWriter;
import com.android.tools.maven.AetherUtils;
import com.android.tools.maven.HandleAarDescriptorReaderDelegate;
import com.android.tools.maven.HighestVersionSelector;
import com.android.tools.repository_generator.BuildFileWriter;
import com.android.tools.repository_generator.ResolutionResult;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NoopDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;

/**
 * A tool that generates a virtual Maven repository from a given list of initial artifacts, and
 * then generates a BUILD file that contains rules for all the artifacts in that Maven repository.
 *
 * <p>It uses Aether for Maven dependency resolution, extending it with the ability to collect Maven
 * parents and Maven resolution conflict losers, to make sure the generated repository is complete.
 */
public class LocalMavenRepositoryGenerator {

    /**
     * The coordinates of the Maven artifacts that will be used as seeds to produce the BUILD file.
     */
    private final List<String> coords;

    /** The location of the BUILD file that will be produced. */
    private final String outputBuildFile;

    /** Location of the local maven repository for which the BUILD rules wil be generated. */
    private final Path repoPath;

    /** Handle to the repo object that encapsulates Aether operations. */
    private final CustomMavenRepository repo;

    /** If true, also writes the result in JSON format to a file and stdout. */
    private final boolean verbose;

    /** Whether to fetch dependencies from remote repositories. */
    private final boolean fetch;

    @VisibleForTesting
    public LocalMavenRepositoryGenerator(
            Path repoPath,
            String outputBuildFile,
            List<String> coords,
            boolean fetch,
            boolean verbose) {
        this.coords = coords;
        this.outputBuildFile = outputBuildFile;
        this.repoPath = repoPath.toAbsolutePath();
        this.verbose = verbose;
        this.fetch = fetch;

        // This is where the artifacts will be downloaded from. We make it point to our own local
        // maven repo. If an artifact is required for dependency resolution but doesn't exist
        // there, it will be reported as an error.
        RemoteRepository remoteRepository =
                new RemoteRepository.Builder("prebuilts", "default", "file://" + this.repoPath)
                        .build();
        List<RemoteRepository> repositories = new ArrayList<>();
        if (fetch) {
            repositories.addAll(AetherUtils.REPOSITORIES);
        }
        repositories.add(remoteRepository);
        repo = new CustomMavenRepository(this.repoPath.toString(), repositories);
    }

    public void run() throws Exception {
        ResolutionResult result = new ResolutionResult();

        // Compute dependency graph with version resolution and with conflict resolution.
        List<DependencyNode> resolvedNodes =
                collectNodes(repo.resolveDependencies(coords, true));

        // Add the nodes in the conflict-resolved graph into the |dependencies| section of |result|.
        for (DependencyNode node : resolvedNodes) {
            processNode(node, false, result);
        }

        // Compute dependency graph with version resolution, but without conflict resolution.
        List<DependencyNode> unresolvedNodes =
                collectNodes(repo.resolveDependencies(coords, false));

        // "Fetch" the metadata from the locally downloaded files
        if (this.fetch) {
            for (DependencyNode node : unresolvedNodes) {
                Artifact artifact = node.getArtifact();
                DefaultMetadata metadata = new DefaultMetadata(artifact.getGroupId(),
                        artifact.getArtifactId(),
                        "maven-metadata.xml",
                        Metadata.Nature.RELEASE_OR_SNAPSHOT);

                for (RemoteRepository repository : AetherUtils.REPOSITORIES) {
                    String path = repo.session.getLocalRepositoryManager()
                            .getPathForRemoteMetadata(metadata, repository, "");
                    File source = new File(repo.session.getLocalRepository().getBasedir(), path);
                    if (source.exists()) {
                        File target = new File(source.getParentFile(), "maven-metadata.xml");
                        Files.copy(source.toPath(), target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                }
            }
        }

        // Add the nodes in the conflict-unresolved graph, but not in the conflict-resolved graph
        // into the |conflictLosers| section of |result|.
        Set<String> resolvedNodeCoords =
                resolvedNodes.stream()
                        .map(d -> d.getArtifact().toString())
                        .collect(Collectors.toSet());
        Set<DependencyNode> conflictLoserNodes =
                unresolvedNodes.stream()
                        .filter(d -> !resolvedNodeCoords.contains(d.getArtifact().toString()))
                        .collect(Collectors.toSet());
        for (DependencyNode node : conflictLoserNodes) {
            processNode(node, true, result);
        }

        // Add the transitive parents of all nodes in the conflict-unresolved graph into the
        // |parents| section of |result|.
        //
        // This is a worklist algorithm that starts with the parents of all nodes in the graph,
        // dequeues one parent at a time, adding the transitive parents back to the work list.
        // Parents of all nodes that will later need to be processed.
        List<Parent> parentsToProcess =
                unresolvedNodes.stream()
                        .map(node -> repo.getMavenModel(node.getArtifact()).getParent())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        Set<String> alreadyProcessedParents = new HashSet<>();
        while (!parentsToProcess.isEmpty()) {
            Parent parent = parentsToProcess.remove(0);
            if (!alreadyProcessedParents.add(parent.toString())) {
                continue;
            }

            Model model =
                    repo.getMavenModel(
                            new DefaultArtifact(
                                    parent.getGroupId(),
                                    parent.getArtifactId(),
                                    "pom",
                                    parent.getVersion()));

            result.parents.add(
                    new ResolutionResult.Parent(
                            parent.toString(),
                            repoPath.relativize(model.getPomFile().toPath()).toString(),
                            model.getParent() == null ? null : model.getParent().toString()));

            if (model.getParent() != null) {
                parentsToProcess.add(model.getParent());
            }
        }

        result.sortByCoord();

        if (verbose) {
            JsonFileWriter.write("output.json", result);
            JsonFileWriter.print(result);
        }

        new BuildFileWriter(repoPath, outputBuildFile).write(result);
    }

    /**
     * Returns a list that contains all the nodes in the Dependency graph with the given virtual
     * root (that contains all real roots as its children).
     *
     * <p>The virtual root is excluded in the returned list.
     *
     * <p>If input dependency graph was built using VERBOSE mode enabled (i.e., it preserved some of
     * the conflict loser nodes), the returned list also excludes any nodes that are conflict
     * losers.
     */
    private List<DependencyNode> collectNodes(DependencyNode root) {
        Map<String, DependencyNode> allNodes = new HashMap<>();
        root.accept(
                new DependencyVisitor() {
                    @Override
                    public boolean visitEnter(DependencyNode node) {
                        if (node.getArtifact() == null) return true;
                        if (allNodes.containsKey(node.getArtifact().toString())) return false;

                        // Exclude conflict losers.
                        // Dependency loser nodes do not have their children populated, and
                        // therefore cause problems in analysis further downstream. It's better to
                        // handle them separately.
                        //
                        // Checks if this node was involved in a conflict, lost the conflict, and
                        // was upgraded to a different version.
                        DependencyNode winnerNode = getWinner(node);
                        boolean lostConflict =
                                winnerNode != null
                                        && winnerNode.getArtifact() != null
                                        && !winnerNode
                                                .getArtifact()
                                                .toString()
                                                .equals(node.getArtifact().toString());
                        if (lostConflict) return true;

                        allNodes.put(node.getArtifact().toString(), node);
                        return true;
                    }

                    @Override
                    public boolean visitLeave(DependencyNode node) {
                        return true;
                    }
                });

        return new ArrayList<>(allNodes.values());
    }

    /**
     * Processes the given node, converts it into a {@link ResolutionResult.Dependency} object, and
     * adds it into result.
     */
    private void processNode(
            DependencyNode node, boolean isConflictLoser, ResolutionResult result) {
        // node does not contain any information about parent or pom file.
        // To obtain those, we use the maven-model-builder plugin.
        Model model = repo.getMavenModel(node.getArtifact());
        String parentCoord = model.getParent() != null ? model.getParent().toString() : null;

        // node does not contain any information about classifiers, or
        // sources jar. maven-model-provider does not provide a way of extracting
        // source jars either. Here, we try to find out whether there is a source
        // jar by manipulating the artifact path and checking whether such a jar
        // exists.
        String artifactPath = node.getArtifact().getFile().toPath().toString();
        String sourcesJarPath = null;
        if (artifactPath.endsWith(".jar")) {
            String path =
                    artifactPath.substring(0, artifactPath.length() - ".jar".length())
                            + "-sources.jar";
            if (new File(path).exists()) {
                sourcesJarPath = repoPath.relativize(Paths.get(path)).toString();
            }
        }

        // For every dependency (i.e., child) of this node, if the dependency was
        // involved in a conflict, then this will map requested dependency artifact
        // to its reconciled dependency artifact.
        Map<String, String> conflictResolution = new HashMap<>();
        // The dependencies after resolution grouped by scope.
        Map<String, List<String>> resolvedDeps = new TreeMap<>();
        // The dependencies that were involved in a conflict and got upgraded.
        List<String> originalDeps = new ArrayList<>();

        for (DependencyNode child : node.getChildren()) {
            originalDeps.add(child.getArtifact().toString());
        }

        if (!isConflictLoser) {
            for (DependencyNode child : node.getChildren()) {
                DependencyNode winnerChildNode = getWinner(child);
                if (winnerChildNode == null
                        || winnerChildNode.getArtifact() == null
                        || winnerChildNode
                                .getArtifact()
                                .toString()
                                .equals(child.getArtifact().toString())) {
                    // Winner doesn't exist, does not have an artifact, or is identical
                    // to the child node. This is a dependency that was not upgraded.
                    String scope = child.getDependency().getScope();
                    resolvedDeps.putIfAbsent(scope, new ArrayList<>());
                    resolvedDeps.get(scope).add(child.getArtifact().toString());
                } else {
                    // This dependency was in a conflict, and got upgraded.
                    conflictResolution.put(
                            child.getArtifact().toString(),
                            winnerChildNode.getArtifact().toString());
                    // We still maintain the original dependency scope.
                    String scope = child.getDependency().getScope();
                    resolvedDeps.putIfAbsent(scope, new ArrayList<>());
                    resolvedDeps.get(scope).add(winnerChildNode.getArtifact().toString());
                }
            }
        }

        if (isConflictLoser) {
            result.conflictLosers.add(
                    new ResolutionResult.Dependency(
                            node.getArtifact().toString(),
                            repoPath.relativize(node.getArtifact().getFile().toPath()).toString(),
                            repoPath.relativize(model.getPomFile().toPath()).toString(),
                            null,
                            null,
                            node.getChildren().stream()
                                    .map(d -> d.getArtifact().toString())
                                    .toArray(String[]::new),
                            null,
                            null));
        } else {
            result.dependencies.add(
                    new ResolutionResult.Dependency(
                            node.getArtifact().toString(),
                            repoPath.relativize(node.getArtifact().getFile().toPath()).toString(),
                            repoPath.relativize(model.getPomFile().toPath()).toString(),
                            parentCoord,
                            sourcesJarPath,
                            originalDeps.toArray(new String[0]),
                            resolvedDeps,
                            conflictResolution));
        }
    }

    /**
     * Extracts the dependency resolution winner information from the given node. By default, the
     * dependency graph does not contain this information, however we asked it to be kept by setting
     * the CONFIG_PROP_VERBOSE flag to true in graph construction.
     *
     * @return if there was a resolution conflict, returns the node that represents the conflict
     *     winner
     */
    private static DependencyNode getWinner(DependencyNode node) {
        return (DependencyNode) node.getData().get(ConflictResolver.NODE_DATA_WINNER);
    }

    public static void main(String[] args) throws Exception {
        List<String> coords = new ArrayList<>();
        Path repoPath = null;
        boolean verbose = false;
        boolean fetch = "1".equals(System.getenv("MAVEN_FETCH"));
        String outputFile = "output.BUILD";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-o")) {
                if (args.length < i + 1) {
                    System.err.println("-o must be followed by a filename");
                    System.exit(1);
                }
                outputFile = args[i + 1];
                i++;
                continue;
            }
            if (arg.equals("-v")) {
                verbose = true;
                continue;
            }
            if (arg.equals("--repo-path")) {
                if (args.length < i + 1) {
                    System.err.println("--repo-path must be followed by a path");
                    System.exit(1);
                }
                repoPath = Paths.get(args[i + 1]);
                i++;
                continue;
            }
            if (arg.equals("--fetch")) {
                fetch = true;
                continue;
            }

            // All other arguments are coords.
            coords.add(args[i]);
        }
        if (repoPath == null) {
            System.err.println("Missing argument: --repo-path");
            System.exit(1);
        }

        if (coords.isEmpty()) {
            System.err.println("At least one maven artifact coordinate must be provided");
            System.exit(1);
        }

        new LocalMavenRepositoryGenerator(repoPath, outputFile, coords, fetch, verbose).run();
    }

    /**
     * Extended, customized version of {@link com.android.tools.maven.MavenRepository}.
     *
     * <p>Specifically, it can:
     *
     * <ul>
     *   <li>Perform dependency resolution without conflict resolution
     *   <li>Preserve edges/nodes that would otherwise be eliminated from dependency graph during
     *       conflict resolution (transitive edges/nodes are not preserved).
     * </ul>
     *
     * TODO(b/190268212): Consider merging with {@link com.android.tools.maven.MavenRepository}.
     */
    private static class CustomMavenRepository {

        private final RepositorySystem system;
        private final DefaultRepositorySystemSession session;
        private final List<RemoteRepository> repositories;
        private final ModelBuilder modelBuilder;

        public CustomMavenRepository(String repoPath, List<RemoteRepository> repositories) {
            system = CustomAetherUtils.newRepositorySystem();
            session = CustomAetherUtils.newSession(system, repoPath);
            this.repositories = repositories;
            modelBuilder = new DefaultModelBuilderFactory().newInstance();
        }

        /**
         * Performs dependency resolution for the given maven coordinates.
         *
         * <p>If resolveConflicts is true, then it also performs dependency conflict resolution in
         * verbose mode, which effectively reduces the dependency graph into a dependency tree
         * (except for the effects of verbose mode, which preserves some extra information about the
         * conflict loser nodes that are directly reachable by conflict winners).
         *
         * <p>If resolveConflicts is false, the dependency graph is returned as-is, without any
         * dependency conflict resolution.
         */
        public DependencyNode resolveDependencies(List<String> coords, boolean resolveConflicts)
                throws DependencyResolutionException {
            List<Dependency> deps = new ArrayList<>();
            for (String coord : coords) {
                deps.add(new Dependency(new DefaultArtifact(coord), JavaScopes.COMPILE));
            }

            if (resolveConflicts) {
                session.setDependencyGraphTransformer(
                        new ConflictResolver(
                                // Skip explicitly requested version check.
                                new HighestVersionSelector(Collections.emptySet()),
                                new JavaScopeSelector(),
                                new SimpleOptionalitySelector(),
                                new JavaScopeDeriver()));
            } else {
                session.setDependencyGraphTransformer(new NoopDependencyGraphTransformer());
            }

            // Collect and resolve the transitive dependencies of the given artifacts. This
            // operation is a combination of collectDependencies() and resolveArtifacts().
            DependencyRequest request =
                    new DependencyRequest()
                            .setCollectRequest(
                                    new CollectRequest()
                                            .setDependencies(deps)
                                            .setRepositories(repositories));

            DependencyResult result = system.resolveDependencies(session, request);

            return result.getRoot();
        }

        /** Returns the maven model for the given artifact. */
        public Model getMavenModel(Artifact artifact) {
            try {
                File pomFile = getPomFile(artifact);
                ModelBuildingRequest buildingRequest = new DefaultModelBuildingRequest();
                // Java version is determined from system properties.
                buildingRequest.setSystemProperties(System.getProperties());
                buildingRequest.setPomFile(pomFile);
                buildingRequest.setProcessPlugins(true);
                buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

                ModelResolver modelResolver = newDefaultModelResolver();
                buildingRequest.setModelResolver(modelResolver);

                ModelBuildingResult result = modelBuilder.build(buildingRequest);
                return result.getEffectiveModel();
            } catch (Exception e) {
                // Rethrow as runtime exception to simplify call site. We have no intention to catch
                // these errors and handle them gracefully.
                throw new RuntimeException(e);
            }
        }

        /** Returns the pom file for the given artifact. */
        private File getPomFile(Artifact artifact) throws Exception {
            Artifact pomArtifact =
                    new DefaultArtifact(
                            artifact.getGroupId(),
                            artifact.getArtifactId(),
                            "pom",
                            artifact.getVersion());
            ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, null);
            pomArtifact = system.resolveArtifact(session, request).getArtifact();
            return pomArtifact.getFile();
        }

        /** Creates and returns a new DefaultModelResolver instance. */
        private ModelResolver newDefaultModelResolver() throws Exception {
            // We use reflection to access this internal class.
            Constructor<?> constructor =
                    Class.forName("org.apache.maven.repository.internal.DefaultModelResolver")
                            .getConstructors()[0];
            constructor.setAccessible(true);
            DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
            return (ModelResolver)
                    constructor.newInstance(
                            session,
                            null,
                            null,
                            locator.getService(ArtifactResolver.class),
                            locator.getService(VersionRangeResolver.class),
                            locator.getService(RemoteRepositoryManager.class),
                            repositories);
        }
    }

    /**
     * Extends AetherUtils with:
     *
     * <ul>
     *   <li>System that includes FileTransporterFactory.
     *   <li>Session that sets CONFIG_PROP_VERBOSE=true.
     * </ul>
     *
     * TODO(b/190268212): Consider merging with {@link com.android.tools.maven.AetherUtils}.
     */
    private static class CustomAetherUtils {

        public static RepositorySystem newRepositorySystem() {
            DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
            locator.addService(
                    RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
            locator.addService(TransporterFactory.class, FileTransporterFactory.class);
            locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

            // Note that, if any of the inputs transitively depend on an artifact using a version
            // range, the default version range resolver will not be able to resolve the version,
            // because our prebuilts repository does NOT have any maven-metadata.xml files.
            // In that case, we will have to write our own, custom, version range resolver,
            // then register it here.
            return checkNotNull(locator.getService(RepositorySystem.class));
        }

        public static DefaultRepositorySystemSession newSession(RepositorySystem system, String repoPath) {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

            // This is where the artifacts will be downloaded to. Since it points to our own local
            // maven repo, artifacts  that are already there will not be re-downloaded.
            session.setLocalRepositoryManager(
                    system.newLocalRepositoryManager(session, new LocalRepository(repoPath)));

            session.setIgnoreArtifactDescriptorRepositories(true);

            // When this flag is false, conflict losers are removed from the dependency graph. E.g.,
            // even though common:28 depends on guava:28, it will not be in the dependency graph if
            // there is a conflict and guava:30 wins over guava:28.
            // When this flag is true, these nodes are kept in the dependency graph, but have a
            // special marker (NODE_DATA_WINNER).
            session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
            session.setConfigProperty(
                    ArtifactDescriptorReaderDelegate.class.getName(),
                    new HandleAarDescriptorReaderDelegate());

            return session;
        }
    }
}
